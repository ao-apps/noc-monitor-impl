/*
 * noc-monitor-impl - Implementation of Network Operations Center Monitoring.
 * Copyright (C) 2008-2013, 2016, 2017, 2018, 2019, 2020, 2021, 2022, 2024, 2025  AO Industries, Inc.
 *     support@aoindustries.com
 *     7262 Bull Pen Cir
 *     Mobile, AL 36695
 *
 * This file is part of noc-monitor-impl.
 *
 * noc-monitor-impl is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * noc-monitor-impl is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with noc-monitor-impl.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.aoindustries.noc.monitor.cluster;

import com.aoapps.collections.AoCollections;
import com.aoapps.concurrent.ConcurrentUtils;
import com.aoapps.lang.i18n.Resources;
import com.aoindustries.aoserv.client.AoservConnector;
import com.aoindustries.aoserv.client.account.Account;
import com.aoindustries.aoserv.client.distribution.OperatingSystemVersion;
import com.aoindustries.aoserv.client.infrastructure.PhysicalServer;
import com.aoindustries.aoserv.client.infrastructure.ServerFarm;
import com.aoindustries.aoserv.client.infrastructure.VirtualDisk;
import com.aoindustries.aoserv.client.infrastructure.VirtualServer;
import com.aoindustries.aoserv.client.linux.Server;
import com.aoindustries.aoserv.client.net.Host;
import com.aoindustries.aoserv.cluster.Cluster;
import com.aoindustries.aoserv.cluster.ClusterConfiguration;
import com.aoindustries.aoserv.cluster.Dom0;
import com.aoindustries.aoserv.cluster.Dom0Disk;
import com.aoindustries.aoserv.cluster.DomU;
import com.aoindustries.aoserv.cluster.DomUDisk;
import com.aoindustries.aoserv.cluster.PhysicalVolume;
import com.aoindustries.aoserv.cluster.PhysicalVolumeConfiguration;
import com.aoindustries.aoserv.cluster.ProcessorArchitecture;
import com.aoindustries.aoserv.cluster.ProcessorType;
import com.aoindustries.noc.monitor.RootNodeImpl;
import java.io.IOException;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Builds the cluster configuration from the AOServ Platform.
 *
 * <p>TODO: Add check to make sure that virtual server to physical server mappings are always within the same cluster?
 *       - or - Could this be an underlying database constraint?</p>
 *
 * @author  AO Industries, Inc.
 */
public final class AoservClusterBuilder {

  /** Make no instances. */
  private AoservClusterBuilder() {
    throw new AssertionError();
  }

  private static final Resources RESOURCES =
      Resources.getResources(ResourceBundle::getBundle, AoservClusterBuilder.class);

  private static boolean is7200rpm(String model) {
    return
        // 3Ware, have not yet found way to accurately know which port equates to which /dev/sd[a-z] entry
        // just assuming all are 7200 RPM drives.
        "9650SE-12M DISK".equals(model)      // ?? GB
            || "9650SE-16M DISK".equals(model)      // ?? GB
            // IBM
            || "IC35L120AVV207-0".equals(model)     // 120 GB
            // Maxtor
            || "Maxtor 5T040H4".equals(model)       // 40 GB
            || "MAXTOR 6L060J3".equals(model)       // 60 GB
            || "Maxtor 6Y080L0".equals(model)       // 80 GB
            || "Maxtor 6L250R0".equals(model)       // 250 GB
            // Seagate
            || "ST380811AS".equals(model)           // 80 GB
            || "ST3500320NS".equals(model)          // 500 GB
            || "ST3750330NS".equals(model)          // 750 GB
            // Western Digital
            || model.startsWith("WDC WD800BB-")     // 80 GB
            || model.startsWith("WDC WD800JB-")     // 80 GB
            || model.startsWith("WDC WD1200JB-")    // 120 GB
            || model.startsWith("WDC WD1200JD-")    // 120 GB
            || model.startsWith("WDC WD1200JS-")    // 120 GB
            || model.startsWith("WDC WD2000JB-")    // 200 GB
            || model.startsWith("WDC WD2500JB-")    // 250 GB
            || model.startsWith("WDC WD2500YD-")    // 250 GB
            || model.startsWith("WDC WD3200YS-");   // 320 GB
  }

  private static boolean is10000rpm(String model) {
    return
        // Fujitsu
        "MAW3073NP".equals(model)            // 73 GB
        // Western Digital
        || model.startsWith("WDC WD740GD-"); // 74 GB
  }

  private static boolean is15000rpm(String model) {
    return
        // Seagate
        "ST3146855LC".equals(model);         // 146 GB
  }

  /**
   * Determines if the provide Server is an enabled Dom0.
   */
  private static boolean isEnabledDom0(Server linuxServer) throws SQLException, IOException {
    Host host = linuxServer.getHost();
    if (!host.isMonitoringEnabled()) {
      return false;
    }
    OperatingSystemVersion osvObj = host.getOperatingSystemVersion();
    if (osvObj == null) {
      return false;
    }
    int osv = osvObj.getPkey();
    if (
        osv != OperatingSystemVersion.CENTOS_5_DOM0_I686
            && osv != OperatingSystemVersion.CENTOS_5_DOM0_X86_64
            && osv != OperatingSystemVersion.CENTOS_7_DOM0_X86_64
    ) {
      return false;
    }
    // This should be a physical server and not a virtual server
    PhysicalServer physicalServer = host.getPhysicalServer();
    if (physicalServer == null) {
      throw new SQLException("Dom0 server is not a physical server: " + linuxServer);
    }
    VirtualServer virtualServer = host.getVirtualServer();
    if (virtualServer != null) {
      throw new SQLException("Dom0 server is a virtual server: " + linuxServer);
    }
    return true;
  }

  /**
   * Loads an unmodifiable set of the current cluster states from the AOServ Platform.
   * Only ServerFarms that have at least one enabled Dom0 are included.
   *
   * @see Cluster
   */
  public static SortedSet<Cluster> getClusters(
      final AoservConnector conn,
      final List<Server> linuxServers,
      final Map<String, Map<String, String>> hddModelReports,
      final Map<String, Server.LvmReport> lvmReports,
      final boolean useTarget
  ) throws SQLException, InterruptedException, ExecutionException, IOException {
    List<ServerFarm> serverFarms = conn.getInfrastructure().getServerFarm().getRows();

    // Start concurrently
    List<Future<Cluster>> futures = new ArrayList<>(serverFarms.size());
    for (final ServerFarm serverFarm : serverFarms) {
      // Only create the cluster if there is at least one dom0 machine
      boolean foundDom0 = false;
      for (Server linuxServer : linuxServers) {
        if (isEnabledDom0(linuxServer)) {
          if (linuxServer.getHost().getServerFarm().equals(serverFarm)) {
            foundDom0 = true;
            break;
          }
        }
      }
      if (foundDom0) {
        futures.add(
            RootNodeImpl.executors.getUnbounded().submit(() -> getCluster(conn, serverFarm, linuxServers, hddModelReports, lvmReports, useTarget))
        );
      }
    }

    // Package the results
    return Collections.unmodifiableSortedSet(
        ConcurrentUtils.getAll(
            futures,
            new TreeSet<>()
        )
    );
  }

  /**
   * Loads a cluster for a single server farm.
   *
   * @param  useTarget  if true will use the target values, otherwise will use the live values
   */
  public static Cluster getCluster(
      AoservConnector conn,
      ServerFarm serverFarm,
      List<Server> linuxServers,
      Map<String, Map<String, String>> hddModelReports,
      Map<String, Server.LvmReport> lvmReports,
      boolean useTarget
  ) throws SQLException, IOException {
    final Account.Name rootAccounting = conn.getAccount().getAccount().getRootAccount_name();

    Cluster cluster = new Cluster(serverFarm.getName());

    // Get the Dom0s
    for (Server linuxServer : linuxServers) {
      if (isEnabledDom0(linuxServer)) {
        Host host = linuxServer.getHost();
        if (host.getServerFarm().equals(serverFarm)) {
          PhysicalServer physicalServer = host.getPhysicalServer();
          String hostname = linuxServer.getHostname().toString();
          cluster = cluster.addDom0(
              hostname,
              /*rack,*/
              physicalServer.getRam(),
              ProcessorType.valueOf(physicalServer.getProcessorType().getType()),
              ProcessorArchitecture.valueOf(host.getOperatingSystemVersion().getArchitecture(conn).getName().toUpperCase(Locale.ENGLISH)),
              physicalServer.getProcessorSpeed(),
              physicalServer.getProcessorCores(),
              physicalServer.getSupportsHvm()
          );
          Dom0 dom0 = cluster.getDom0(hostname);
          if (dom0 == null) {
            throw new AssertionError("dom0 is null");
          }

          // Add Dom0Disks when first needed for a physical volume
          Map<String, String> hddModelReport = hddModelReports.get(hostname);
          if (hddModelReport == null) {
            throw new AssertionError("hddmodel report not found for " + hostname);
          }
          Set<String> addedDisks = AoCollections.newHashSet(hddModelReport.size());
          // Add physical volumes
          Server.LvmReport lvmReport = lvmReports.get(hostname);
          if (lvmReport == null) {
            throw new AssertionError("LvmReport not found for " + hostname);
          }
          for (Map.Entry<String, Server.LvmReport.PhysicalVolume> entry : lvmReport.getPhysicalVolumes().entrySet()) {
            final String partition = entry.getKey();
            final Server.LvmReport.PhysicalVolume lvmPhysicalVolume = entry.getValue();
            // Count the number of digits on the right of partition
            int digitCount = 0;
            for (int c = partition.length() - 1; c >= 0; c--) {
              char ch = partition.charAt(c);
              if (ch >= '0' && ch <= '9') {
                digitCount++;
              } else {
                break;
              }
            }
            if (digitCount == 0) {
              throw new AssertionError("No partition number found on physical volume: " + partition);
            }
            String device = partition.substring(0, partition.length() - digitCount);
            short partitionNum = Short.parseShort(partition.substring(partition.length() - digitCount));
            if (!addedDisks.contains(device)) {
              // Add the Dom0Disk
              String model = hddModelReport.get(device);
              if (model == null) {
                throw new AssertionError("device not found in hddmodel report: " + device + " on " + hostname);
              }
              int speed;
              if (is7200rpm(model)) {
                speed = 7200;
              } else if (is10000rpm(model)) {
                speed = 10000;
              } else if (is15000rpm(model)) {
                speed = 15000;
              } else {
                throw new AssertionError("Unknown hard drive model: " + model);
              }
              cluster = cluster.addDom0Disk(hostname, device, speed);
              addedDisks.add(device);
            }
            // Add the physical volume
            long extents = lvmPhysicalVolume.getPvPeCount();
            if (extents == 0) {
              // Not allocated, need to calculate ourselves using the default extents size
              extents = lvmPhysicalVolume.getPvSize() / DomUDisk.EXTENTS_SIZE;
            }
            cluster = cluster.addPhysicalVolume(
                hostname,
                device,
                partitionNum,
                extents
            );
          }
        }
      }
    }

    // Get the DomUs
    for (Host host : conn.getNet().getHost().getRows()) {
      if (host.isMonitoringEnabled() && host.getServerFarm().equals(serverFarm)) {
        // Should be either physical or virtual server
        PhysicalServer physicalServer = host.getPhysicalServer();
        VirtualServer virtualServer = host.getVirtualServer();
        if (physicalServer == null && virtualServer == null) {
          throw new SQLException("Host is neither a physical server nor a virtual server: " + host);
        }
        if (physicalServer != null && virtualServer != null) {
          throw new SQLException("Host is both a physical server and a virtual server: " + host);
        }
        if (virtualServer != null) {
          // Must always be in the package with the same name as the root account
          Account.Name packageName = host.getPackage().getName();
          if (!packageName.equals(rootAccounting)) {
            throw new SQLException("All virtual servers should have a package name equal to the root account name:"
                + " servers.package.name != root_account.accounting: " + packageName + " != " + rootAccounting);
          }
          String hostname = host.getName();
          cluster = cluster.addDomU(
              hostname,
              useTarget ? virtualServer.getPrimaryRamTarget() : virtualServer.getPrimaryRam(),
              useTarget ? virtualServer.getSecondaryRamTarget() : virtualServer.getSecondaryRam(),
              virtualServer.getMinimumProcessorType() == null ? null : ProcessorType.valueOf(virtualServer.getMinimumProcessorType().getType()),
              ProcessorArchitecture.valueOf(virtualServer.getMinimumProcessorArchitecture().getName().toUpperCase(Locale.ENGLISH)),
              useTarget ? virtualServer.getMinimumProcessorSpeedTarget() : virtualServer.getMinimumProcessorSpeed(),
              useTarget ? virtualServer.getProcessorCoresTarget() : virtualServer.getProcessorCores(),
              useTarget ? virtualServer.getProcessorWeightTarget() : virtualServer.getProcessorWeight(),
              virtualServer.getRequiresHvm(),
              virtualServer.isPrimaryPhysicalServerLocked(),
              virtualServer.isSecondaryPhysicalServerLocked()
          );
          DomU domU = cluster.getDomU(hostname);
          if (domU == null) {
            throw new AssertionError("domU is null");
          }
          for (VirtualDisk virtualDisk : virtualServer.getVirtualDisks()) {
            cluster = cluster.addDomUDisk(
                hostname,
                virtualDisk.getDevice(),
                useTarget ? virtualDisk.getMinimumDiskSpeedTarget() : virtualDisk.getMinimumDiskSpeed(),
                virtualDisk.getExtents(),
                useTarget ? virtualDisk.getWeightTarget() : virtualDisk.getWeight()
            );
          }
        }
      }
    }

    return cluster;
  }

  /**
   * Loads an unmodifiable set of the current cluster configuration from the AOServ Platform.
   *
   * @see  AoservClusterBuilder#getClusters
   * @see  AoservClusterBuilder#getClusterConfiguration
   */
  public static SortedSet<ClusterConfiguration> getClusterConfigurations(
      final Locale locale,
      final AoservConnector conn,
      final SortedSet<Cluster> clusters,
      final Map<String, List<Server.DrbdReport>> drbdReports,
      final Map<String, Server.LvmReport> lvmReports
  ) throws InterruptedException, ExecutionException {
    // final List<Server> linuxServers = conn.linuxServers.getRows();
    // final Map<String, Map<String, String>> hddModelReports = getHddModelReports(linuxServers, locale);
    // final Map<String, List<Server.DrbdReport>> drbdReports = getDrbdReports(linuxServers, locale);
    // final Map<String, Server.LvmReport> lvmReports = getLvmReports(linuxServers, locale);

    // Start concurrently
    // SortedSet<Cluster> clusters = getClusters(conn, linuxServers, hddModelReports, lvmReports);
    List<Future<ClusterConfiguration>> futures = new ArrayList<>(clusters.size());
    for (final Cluster cluster : clusters) {
      futures.add(
          RootNodeImpl.executors.getUnbounded().submit(() -> getClusterConfiguration(locale, conn, cluster, drbdReports, lvmReports))
      );
    }

    // Package the results
    return Collections.unmodifiableSortedSet(
        ConcurrentUtils.getAll(
            futures,
            new TreeSet<>()
        )
    );
  }

  /**
   * Concurrently gets all of the DRBD reports for the entire cluster.  This doesn't perform
   * any sanity checks on the data, it merely parses it and ensures correct values.
   */
  public static Map<String, List<Server.DrbdReport>> getDrbdReports(
      final List<Server> linuxServers,
      final Locale locale
  ) throws SQLException, InterruptedException, ExecutionException, IOException {
    // Query concurrently for each of the drbdcstate's to get a good snapshot and determine primary/secondary locations
    Map<String, Future<List<Server.DrbdReport>>> futures = AoCollections.newHashMap(linuxServers.size());
    for (final Server linuxServer : linuxServers) {
      if (isEnabledDom0(linuxServer)) {
        futures.put(
            linuxServer.getHostname().toString(),
            RootNodeImpl.executors.getUnbounded().submit(linuxServer::getDrbdReport)
        );
      }
    }

    // Get and parse the results, also perform sanity checks
    return Collections.unmodifiableMap(ConcurrentUtils.getAll(futures));
  }

  /**
   * Concurrently gets all of the LVM reports for the all clusters.  This doesn't perform
   * any sanity checks on the data, it merely parses it and ensures correct values.
   */
  public static Map<String, Server.LvmReport> getLvmReports(
      final List<Server> linuxServers,
      final Locale locale
  ) throws SQLException, InterruptedException, ExecutionException, IOException {
    Map<String, Future<Server.LvmReport>> futures = AoCollections.newHashMap(linuxServers.size());
    for (final Server linuxServer : linuxServers) {
      if (isEnabledDom0(linuxServer)) {
        futures.put(
            linuxServer.getHostname().toString(),
            RootNodeImpl.executors.getUnbounded().submit(linuxServer::getLvmReport)
        );
      }
    }

    // Get and parse the results, also perform sanity checks
    return Collections.unmodifiableMap(ConcurrentUtils.getAll(futures));
  }

  /**
   * Concurrently gets all of the hard drive model reports for the all clusters.  This doesn't perform
   * any sanity checks on the data, it merely parses it and ensures correct values.
   */
  public static Map<String, Map<String, String>> getHddModelReports(
      final List<Server> linuxServers,
      final Locale locale
  ) throws SQLException, InterruptedException, ExecutionException, IOException {
    Map<String, Future<Map<String, String>>> futures = AoCollections.newHashMap(linuxServers.size());
    for (final Server linuxServer : linuxServers) {
      if (isEnabledDom0(linuxServer)) {
        futures.put(
            linuxServer.getHostname().toString(),
            RootNodeImpl.executors.getUnbounded().submit(linuxServer::getHddModelReport)
        );
      }
    }

    // Get and parse the results, also perform sanity checks
    return Collections.unmodifiableMap(ConcurrentUtils.getAll(futures));
  }

  /**
   * Loads the configuration for the provided cluster.
   */
  public static ClusterConfiguration getClusterConfiguration(
      Locale locale,
      AoservConnector conn,
      Cluster cluster,
      Map<String, List<Server.DrbdReport>> drbdReports,
      Map<String, Server.LvmReport> lvmReports
  ) throws ParseException, IOException, SQLException {
    final Account.Name rootAccounting = conn.getAccount().getAccount().getRootAccount_name();

    ClusterConfiguration clusterConfiguration = new ClusterConfiguration(cluster);

    Map<String, String> drbdPrimaryDom0s = new HashMap<>();
    Map<String, String> drbdSecondaryDom0s = new HashMap<>();

    // Get and primary and secondary Dom0s from the DRBD report.
    // Also performs sanity checks on all the DRBD information.
    for (Map.Entry<String, List<Server.DrbdReport>> entry : drbdReports.entrySet()) {
      String dom0Hostname = entry.getKey();
      int lineNum = 0;
      for (Server.DrbdReport report : entry.getValue()) {
        lineNum++;
        // Must be a virtual server
        String domUHostname = report.getResourceHostname();
        Host domUServer = conn.getNet().getHost().get(rootAccounting + "/" + domUHostname);
        if (domUServer == null) {
          throw new ParseException(
              RESOURCES.getMessage(
                  locale,
                  "ParseException.serverNotFound",
                  domUHostname
              ),
              lineNum
          );
        }
        VirtualServer domUVirtualServer = domUServer.getVirtualServer();
        if (domUVirtualServer == null) {
          throw new ParseException(
              RESOURCES.getMessage(
                  locale,
                  "ParseException.notVirtualServer",
                  domUHostname
              ),
              lineNum
          );
        }
        String domUDevice = report.getResourceDevice();
        if (
            domUDevice.length() != 4
                || domUDevice.charAt(0) != 'x'
                || domUDevice.charAt(1) != 'v'
                || domUDevice.charAt(2) != 'd'
                || domUDevice.charAt(3) < 'a'
                || domUDevice.charAt(3) > 'z'
        ) {
          throw new ParseException(
              RESOURCES.getMessage(
                  locale,
                  "ParseException.unexpectedResourceEnding",
                  domUDevice
              ),
              lineNum
          );
        }
        Server.DrbdReport.Role localRole = report.getLocalRole();
        if (localRole == Server.DrbdReport.Role.Primary) {
          // Is Primary
          String previousValue = drbdPrimaryDom0s.put(domUHostname, dom0Hostname);
          if (previousValue != null && !previousValue.equals(dom0Hostname)) {
            throw new ParseException(
                RESOURCES.getMessage(
                    locale,
                    "ParseException.multiPrimary",
                    domUHostname,
                    previousValue,
                    dom0Hostname
                ),
                lineNum
            );
          }
        } else if (localRole == Server.DrbdReport.Role.Secondary) {
          // Is Secondary
          String previousValue = drbdSecondaryDom0s.put(domUHostname, dom0Hostname);
          if (previousValue != null && !previousValue.equals(dom0Hostname)) {
            throw new ParseException(
                RESOURCES.getMessage(
                    locale,
                    "ParseException.multiSecondary",
                    domUHostname,
                    previousValue,
                    dom0Hostname
                ),
                lineNum
            );
          }
        } else {
          throw new ParseException(
              RESOURCES.getMessage(
                  locale,
                  "ParseException.unexpectedState",
                  localRole
              ),
              lineNum
          );
        }

        // Find the corresponding VirtualDisk
        VirtualDisk virtualDisk = domUVirtualServer.getVirtualDisk(domUDevice);
        if (virtualDisk == null) {
          // System.err.println("-- "+domUHostname);
          // System.err.println("INSERT INTO virtual_disks VALUES(DEFAULT, "+domUVirtualServer.getPkey()+", '"+device+"', NULL, extents, 1, false, false);");
          throw new ParseException(
              RESOURCES.getMessage(
                  locale,
                  "ParseException.virtualDiskNotFound",
                  domUHostname,
                  domUDevice
              ),
              lineNum
          );
        }
      }
    }

    // Now, each and every enabled virtual server must have both a primary and a secondary
    for (Map.Entry<String, DomU> entry : cluster.getDomUs().entrySet()) {
      String domUHostname = entry.getKey();
      DomU domU = entry.getValue();
      Host domUServer = conn.getNet().getHost().get(rootAccounting + "/" + domUHostname);
      // VirtualServer domUVirtualServer = domUServer.getVirtualServer();

      String primaryDom0Hostname = drbdPrimaryDom0s.get(domUHostname);
      if (primaryDom0Hostname == null) {
        throw new ParseException(
            RESOURCES.getMessage(
                locale,
                "ParseException.primaryNotFound",
                domUHostname
            ),
            0
        );
      }

      String secondaryDom0Hostname = drbdSecondaryDom0s.get(domUHostname);
      if (secondaryDom0Hostname == null) {
        throw new ParseException(
            RESOURCES.getMessage(
                locale,
                "ParseException.secondaryNotFound",
                domUHostname
            ),
            0
        );
      }
      clusterConfiguration = clusterConfiguration.addDomUConfiguration(
          domU,
          cluster.getDom0(primaryDom0Hostname),
          cluster.getDom0(secondaryDom0Hostname)
      );
      // DomUConfiguration domUConfiguration = clusterConfiguration.getDomUConfiguration(domU);
      // assert domUConfiguration != null : "domUConfiguration is null";

      // Add each DomUDisk
      for (DomUDisk domUDisk : domU.getDomUDisks().values()) {
        // Must have been found once, and only once, on the primary server DRBD report
        int foundCount = 0;
        for (Server.DrbdReport report : drbdReports.get(primaryDom0Hostname)) {
          if (report.getResourceHostname().equals(domUHostname) && report.getResourceDevice().equals(domUDisk.getDevice())) {
            foundCount++;
          }
        }
        if (foundCount != 1) {
          throw new ParseException(
              RESOURCES.getMessage(
                  locale,
                  "ParseException.drbdDomUDiskShouldBeFoundOnce",
                  domUDisk.getDevice(),
                  primaryDom0Hostname
              ),
              0
          );
        }

        // Must have been found once, and only once, on the secondary server DRBD report
        foundCount = 0;
        for (Server.DrbdReport report : drbdReports.get(secondaryDom0Hostname)) {
          if (report.getResourceHostname().equals(domUHostname) && report.getResourceDevice().equals(domUDisk.getDevice())) {
            foundCount++;
          }
        }
        if (foundCount != 1) {
          throw new ParseException(
              RESOURCES.getMessage(
                  locale,
                  "ParseException.drbdDomUDiskShouldBeFoundOnce",
                  domUDisk.getDevice(),
                  secondaryDom0Hostname
              ),
              0
          );
        }

        // Add to the configuration
        clusterConfiguration = clusterConfiguration.addDomUDiskConfiguration(
            domU,
            domUDisk,
            getPhysicalVolumeConfigurations(cluster, domUDisk, lvmReports, primaryDom0Hostname),
            getPhysicalVolumeConfigurations(cluster, domUDisk, lvmReports, secondaryDom0Hostname)
        );
      }
    }

    // Look for any extra resources in LVM
    {
      // Make sure every volume group found in LVM equals a domUHostname that is either primary or secondary on that machine
      for (Map.Entry<String, Server.LvmReport> entry : lvmReports.entrySet()) {
        String dom0Hostname = entry.getKey();
        // Verify within this cluster only
        if (cluster.getDom0s().containsKey(dom0Hostname)) {
          Server.LvmReport lvmReport = entry.getValue();
          for (Map.Entry<String, Server.LvmReport.VolumeGroup> vgEntry : lvmReport.getVolumeGroups().entrySet()) {
            String vgName = vgEntry.getKey();
            Server.LvmReport.VolumeGroup volumeGroup = vgEntry.getValue();
            // This should still validate the logical volumes in the off chance a virtual server is named "backup"
            DomU domU = cluster.getDomU(vgName);
            if (domU == null) {
              if (!"backup".equals(vgName)) {
                throw new AssertionError("Volume group found but there is no virtual server of the same name: " + dom0Hostname + ":/dev/" + vgName);
              }
            } else {
              // Make sure primary or secondary on this Dom0
              String domUHostname = domU.getHostname();
              String primaryDom0Hostname = drbdPrimaryDom0s.get(domUHostname);
              String secondaryDom0Hostname = drbdSecondaryDom0s.get(domUHostname);
              if (
                  !primaryDom0Hostname.equals(dom0Hostname)
                      && !secondaryDom0Hostname.equals(dom0Hostname)
              ) {
                throw new AssertionError("Volume group found but the virtual server is neither primary nor secondary on this physical server: " + dom0Hostname + ":/dev/" + vgName);
              }

              // Make sure every logical volume found in LVM equals a domUDisk
              for (String lvName : volumeGroup.getLogicalVolumes().keySet()) {
                if (!lvName.endsWith("-drbd")) {
                  throw new AssertionError("lvName does not end with -drbd: " + lvName);
                }
                DomUDisk domUDisk = domU.getDomUDisk(lvName.substring(0, lvName.length() - 5));
                if (domUDisk == null) {
                  throw new AssertionError("Logical volume found but the virtual server does not have a corresponding virtual disk: " + dom0Hostname + ":/dev/" + vgName + "/" + lvName);
                }
              }
            }
          }
        }
      }
    }

    return clusterConfiguration;
  }

  /**
   * Gets the physical volume configuration for the provided disk on the provided Dom0.
   */
  private static List<PhysicalVolumeConfiguration> getPhysicalVolumeConfigurations(
      Cluster cluster,
      DomUDisk domUDisk,
      Map<String, Server.LvmReport> lvmReports,
      String dom0Hostname
  ) {
    Server.LvmReport lvmReport = lvmReports.get(dom0Hostname);
    if (lvmReport == null) {
      throw new AssertionError("No lvm report found for " + dom0Hostname);
    }
    // Find the Dom0
    Dom0 dom0 = cluster.getDom0(dom0Hostname);
    if (dom0 == null) {
      throw new AssertionError("Dom0 not found: " + dom0Hostname);
    }
    // Should have a volume group equal to its hostname
    String domUHostname = domUDisk.getDomUHostname();
    Server.LvmReport.VolumeGroup volumeGroup = lvmReport.getVolumeGroup(domUHostname);
    if (volumeGroup == null) {
      throw new AssertionError("No volume group named " + domUHostname + " found on " + dom0Hostname);
    }
    // Should have a logical volume named {device}-drbd
    String logicalVolumeName = domUDisk.getDevice() + "-drbd";
    Server.LvmReport.LogicalVolume logicalVolume = volumeGroup.getLogicalVolume(logicalVolumeName);
    if (logicalVolume == null) {
      throw new AssertionError("No logical volume named " + logicalVolumeName + " found on " + dom0Hostname + ":" + domUHostname);
    }
    // Each logical volume may have any number of segments
    List<PhysicalVolumeConfiguration> configs = new ArrayList<>();
    for (Server.LvmReport.Segment segment : logicalVolume.getSegments()) {
      Server.LvmReport.SegmentType segmentType = segment.getSegtype();
      if (segmentType != Server.LvmReport.SegmentType.linear && segmentType != Server.LvmReport.SegmentType.striped) {
        throw new AssertionError("Only linear and striped segments currently supported");
      }
      // This is somewhat of an over-simplication as it treats any striped segment as if each stripe is linearly appended.
      // This should be close enough for the optimization purposes.
      long firstLogicalExtent = segment.getSegStartPe();
      // Each segment may have multiple stripes
      for (Server.LvmReport.Stripe stripe : segment.getStripes()) {
        assert stripe.getPhysicalVolume().getVolumeGroup().getVgName().equals(domUHostname) : "stripe.physicalVolume.volumeGroup.vgName != domUHostname";
        String partition = stripe.getPhysicalVolume().getPvName();
        // Count the number of digits on the right of partition
        int digitCount = 0;
        for (int c = partition.length() - 1; c >= 0; c--) {
          char ch = partition.charAt(c);
          if (ch >= '0' && ch <= '9') {
            digitCount++;
          } else {
            break;
          }
        }
        if (digitCount == 0) {
          throw new AssertionError("No partition number found on physical volume: " + partition);
        }
        String device = partition.substring(0, partition.length() - digitCount);
        short partitionNum = Short.parseShort(partition.substring(partition.length() - digitCount));

        // Find the Dom0Disk
        Dom0Disk dom0Disk = dom0.getDom0Disk(device);
        if (dom0Disk == null) {
          throw new AssertionError("Unable to find Dom0Disk: " + dom0Hostname + ":" + device);
        }

        // Find the Dom0 physical volume
        PhysicalVolume physicalVolume = dom0Disk.getPhysicalVolume(partitionNum);
        if (physicalVolume == null) {
          throw new AssertionError("Unable to find PhysicalVolume: " + dom0Hostname + ":" + device + partitionNum);
        }

        // Add the new physical volume config
        long firstPhysicalExtent = stripe.getFirstPe();
        long extents = stripe.getLastPe() - firstPhysicalExtent + 1;
        configs.add(
            PhysicalVolumeConfiguration.newInstance(
                physicalVolume,
                firstLogicalExtent,
                firstPhysicalExtent,
                extents
            )
        );
        firstLogicalExtent += extents;
      }
    }
    return configs;
  }
}
