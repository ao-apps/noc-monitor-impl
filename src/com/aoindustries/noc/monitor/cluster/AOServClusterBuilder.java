/*
 * Copyright 2008-2009 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor.cluster;

import com.aoindustries.aoserv.client.AOServConnector;
import com.aoindustries.aoserv.client.AOServer;
import com.aoindustries.aoserv.client.OperatingSystemVersion;
import com.aoindustries.aoserv.client.PhysicalServer;
import com.aoindustries.aoserv.client.Server;
import com.aoindustries.aoserv.client.ServerFarm;
import com.aoindustries.aoserv.client.VirtualDisk;
import com.aoindustries.aoserv.client.VirtualServer;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Builds the cluster configuration from the AOServ system.
 *
 * @author  AO Industries, Inc.
 */
public class AOServClusterBuilder {

    private static boolean is7200rpm(String model) {
        return
            // 3Ware, have not yet found way to accurately know which port equates to which /dev/sd[a-z] entry
            // just assuming all are 7200 RPM drives.
               model.equals("9650SE-12M DISK")      // ?? GB
            || model.equals("9650SE-16M DISK")      // ?? GB
            // IBM
            || model.equals("IC35L120AVV207-0")     // 120 GB
            // Maxtor
            || model.equals("Maxtor 5T040H4")       // 40 GB
            || model.equals("MAXTOR 6L060J3")       // 60 GB
            || model.equals("Maxtor 6Y080L0")       // 80 GB
            || model.equals("Maxtor 6L250R0")       // 250 GB
            // Seagate
            || model.equals("ST380811AS")           // 80 GB
            || model.equals("ST3500320NS")          // 500 GB
            || model.equals("ST3750330NS")          // 750 GB
            // Western Digital
            || model.startsWith("WDC WD800BB-")     // 80 GB
            || model.startsWith("WDC WD800JB-")     // 80 GB
            || model.startsWith("WDC WD1200JB-")    // 120 GB
            || model.startsWith("WDC WD1200JD-")    // 120 GB
            || model.startsWith("WDC WD1200JS-")    // 120 GB
            || model.startsWith("WDC WD2000JB-")    // 200 GB
            || model.startsWith("WDC WD2500JB-")    // 250 GB
            || model.startsWith("WDC WD2500YD-")    // 250 GB
            || model.startsWith("WDC WD3200YS-")    // 320 GB
        ;
    }
    private static boolean is10000rpm(String model) {
        return
            // Fujitsu
               model.equals("MAW3073NP")            // 73 GB
            // Western Digital
            || model.startsWith("WDC WD740GD-")     // 74 GB
        ;
    }
    private static boolean is15000rpm(String model) {
        return
            // Seagate
            model.equals("ST3146855LC")             // 146 GB
        ;
    }

    /** Make no instances */
    private AOServClusterBuilder() {}

    /**
     * Determines if the provide AOServer is an enabled Dom0.
     */
    private static boolean isEnabledDom0(AOServer aoServer) throws SQLException, IOException {
        Server server = aoServer.getServer();
        if(!server.isMonitoringEnabled()) return false;
        OperatingSystemVersion osvObj = server.getOperatingSystemVersion();
        if(osvObj==null) return false;
        int osv = osvObj.getPkey();
        if(
            osv!=OperatingSystemVersion.CENTOS_5DOM0_I686
            && osv!=OperatingSystemVersion.CENTOS_5DOM0_X86_64
        ) return false;
        // This should be a physical server and not a virtual server
        PhysicalServer physicalServer = server.getPhysicalServer();
        if(physicalServer==null) throw new SQLException("Dom0 server is not a physical server: "+aoServer);
        VirtualServer virtualServer = server.getVirtualServer();
        if(virtualServer!=null) throw new SQLException("Dom0 server is a virtual server: "+aoServer);
        return true;
    }

    /**
     * Loads an unmodifiable set of the current cluster states from the AOServ system.
     * Only ServerFarms that have at least one enabled Dom0 are included.
     * 
     * @see Cluster
     */
    //public static SortedSet<Cluster> getClusters(final AOServConnector conn) throws InterruptedException, ExecutionException {
    //}
    private static SortedSet<Cluster> getClusters(
        final AOServConnector conn,
        final List<AOServer> aoServers,
        final Map<String,Map<String,String>> hddModelReports,
        final Map<String,AOServer.LvmReport> lvmReports
    ) throws SQLException, InterruptedException, ExecutionException, IOException {
        List<ServerFarm> serverFarms = conn.serverFarms.getRows();

        // Start concurrently
        List<Future<Cluster>> futures = new ArrayList<Future<Cluster>>(serverFarms.size());
        for(final ServerFarm serverFarm : serverFarms) {
            // Only create the cluster if there is at least one dom0 machine
            boolean foundDom0 = false;
            for(AOServer aoServer : aoServers) {
                if(isEnabledDom0(aoServer)) {
                    if(aoServer.getServer().getServerFarm().equals(serverFarm)) {
                        foundDom0 = true;
                        break;
                    }
                }
            }
            if(foundDom0) {
                futures.add(
                    RootNodeImpl.executorService.submit(
                        new Callable<Cluster>() {
                            public Cluster call() throws SQLException, InterruptedException, ExecutionException, ParseException, IOException {
                                return getCluster(conn, serverFarm, aoServers, hddModelReports, lvmReports);
                            }
                        }
                    )
                );
            }
        }

        // Package the results
        SortedSet<Cluster> clusters = new TreeSet<Cluster>();
        for(Future<Cluster> future : futures) {
            clusters.add(future.get());
        }
        return Collections.unmodifiableSortedSet(clusters);
    }

    /**
     * Loads a cluster for a single server farm.
     */
    //public static Cluster getCluster(AOServConnector conn, ServerFarm serverFarm) throws SQLException, InterruptedException, ExecutionException, ParseException {
    //    return getCluster(conn, serverFarm, getLvmReports(conn));
    //}
    private static Cluster getCluster(
        AOServConnector conn,
        ServerFarm serverFarm,
        List<AOServer> aoServers,
        Map<String,Map<String,String>> hddModelReports,
        Map<String,AOServer.LvmReport> lvmReports
    ) throws SQLException, InterruptedException, ExecutionException, ParseException, IOException {
        final String rootAccounting = conn.businesses.getRootAccounting();

        Cluster cluster = new Cluster(serverFarm.getName());

        // Get the Dom0s
        for(AOServer aoServer : aoServers) {
            if(isEnabledDom0(aoServer)) {
                Server server = aoServer.getServer();
                if(server.getServerFarm().equals(serverFarm)) {
                    PhysicalServer physicalServer = server.getPhysicalServer();
                    String hostname = aoServer.getHostname();
                    cluster = cluster.addDom0(
                        hostname,
                        /*rack,*/
                        physicalServer.getRam(),
                        ProcessorType.valueOf(physicalServer.getProcessorType().getType()),
                        ProcessorArchitecture.valueOf(server.getOperatingSystemVersion().getArchitecture(conn).getName().toUpperCase(Locale.ENGLISH)),
                        physicalServer.getProcessorSpeed(),
                        physicalServer.getProcessorCores(),
                        physicalServer.getSupportsHvm()
                    );
                    Dom0 dom0 = cluster.getDom0(hostname);
                    if(dom0==null) throw new AssertionError("dom0 is null");

                    // Add Dom0Disks when first needed for a physical volume
                    Map<String,String> hddModelReport = hddModelReports.get(hostname);
                    if(hddModelReport==null) throw new AssertionError("hddmodel report not found for "+hostname);
                    Set<String> addedDisks = new HashSet<String>(hddModelReport.size()*4/3+1);
                    // Add physical volumes
                    AOServer.LvmReport lvmReport = lvmReports.get(hostname);
                    if(lvmReport==null) throw new AssertionError("LvmReport not found for "+hostname);
                    for(Map.Entry<String,AOServer.LvmReport.PhysicalVolume> entry : lvmReport.getPhysicalVolumes().entrySet()) {
                        String partition = entry.getKey();
                        AOServer.LvmReport.PhysicalVolume lvmPhysicalVolume = entry.getValue();
                        // Count the number of digits on the right of partition
                        int digitCount = 0;
                        for(int c=partition.length()-1; c>=0; c--) {
                            char ch=partition.charAt(c);
                            if(ch>='0' && ch<='9') digitCount++;
                            else break;
                        }
                        if(digitCount==0) throw new AssertionError("No partition number found on physical volume: "+partition);
                        String device = partition.substring(0, partition.length()-digitCount);
                        short partitionNum = Short.parseShort(partition.substring(partition.length()-digitCount));
                        if(!addedDisks.contains(device)) {
                            // Add the Dom0Disk
                            String model = hddModelReport.get(device);
                            if(model==null) throw new AssertionError("device not found in hddmodel report: "+device+" on "+hostname);
                            int speed;
                            if(is7200rpm(model)) speed = 7200;
                            else if(is10000rpm(model)) speed = 10000;
                            else if(is15000rpm(model)) speed = 15000;
                            else throw new AssertionError("Unknown hard drive model: "+model);
                            cluster = cluster.addDom0Disk(hostname, device, speed);
                            addedDisks.add(device);
                        }
                        // Add the physical volume
                        long extents = lvmPhysicalVolume.getPvPeCount();
                        if(extents==0) {
                            // Not allocated, need to calculate ourselves using the default extents size
                            extents = lvmPhysicalVolume.getPvSize()/DomUDisk.EXTENTS_SIZE;
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
        for(Server server : conn.servers.getRows()) {
            if(server.isMonitoringEnabled() && server.getServerFarm().equals(serverFarm)) {
                // Should be either physical or virtual server
                PhysicalServer physicalServer = server.getPhysicalServer();
                VirtualServer virtualServer = server.getVirtualServer();
                if(physicalServer==null && virtualServer==null) throw new SQLException("Server is neither a physical server nor a virtual server: "+server);
                if(physicalServer!=null && virtualServer!=null) throw new SQLException("Server is both a physical server and a virtual server: "+server);
                if(virtualServer!=null) {
                    // Must always be in the package with the same name as the root business
                    String packageName = server.getPackage().getName();
                    if(!packageName.equals(rootAccounting)) throw new SQLException("All virtual servers should have a package name equal to the root business name: servers.package.name!=root_business.accounting: "+packageName+"!="+rootAccounting);
                    String hostname = server.getName();
                    cluster = cluster.addDomU(
                        hostname,
                        virtualServer.getPrimaryRam(),
                        virtualServer.getSecondaryRam(),
                        virtualServer.getMinimumProcessorType()==null ? null : ProcessorType.valueOf(virtualServer.getMinimumProcessorType().getType()),
                        ProcessorArchitecture.valueOf(virtualServer.getMinimumProcessorArchitecture().getName().toUpperCase(Locale.ENGLISH)),
                        virtualServer.getMinimumProcessorSpeed(),
                        virtualServer.getProcessorCores(),
                        virtualServer.getProcessorWeight(),
                        virtualServer.getRequiresHvm(),
                        virtualServer.isPrimaryPhysicalServerLocked(),
                        virtualServer.isSecondaryPhysicalServerLocked()
                    );
                    DomU domU = cluster.getDomU(hostname);
                    if(domU==null) throw new AssertionError("domU is null");
                    for(VirtualDisk virtualDisk : virtualServer.getVirtualDisks()) {
                        cluster = cluster.addDomUDisk(
                            hostname,
                            virtualDisk.getDevice(),
                            virtualDisk.getMinimumDiskSpeed(),
                            virtualDisk.getExtents(),
                            virtualDisk.getWeight(),
                            virtualDisk.getPrimaryPhysicalVolumesLocked(),
                            virtualDisk.getSecondaryPhysicalVolumesLocked()
                        );
                    }
                }
            }
        }

        return cluster;
    }

    /**
     * Loads an unmodifiable set of the current cluster configuration from the AOServ system.
     * 
     * @see  #getClusters
     * @see  #getClusterConfiguration
     */
    public static SortedSet<ClusterConfiguration> getClusterConfigurations(
        final Locale locale,
        final AOServConnector conn
    ) throws SQLException, ParseException, InterruptedException, ExecutionException, IOException {
        final List<AOServer> aoServers = conn.aoServers.getRows();
        final Map<String,Map<String,String>> hddModelReports = getHddModelReports(aoServers, locale);
        final Map<String,List<AOServer.DrbdReport>> drbdReports = getDrbdReports(aoServers, locale);
        final Map<String,AOServer.LvmReport> lvmReports = getLvmReports(aoServers, locale);

        // Start concurrently
        SortedSet<Cluster> clusters = getClusters(conn, aoServers, hddModelReports, lvmReports);
        List<Future<ClusterConfiguration>> futures = new ArrayList<Future<ClusterConfiguration>>(clusters.size());
        for(final Cluster cluster : clusters) {
            futures.add(
                RootNodeImpl.executorService.submit(
                    new Callable<ClusterConfiguration>() {
                        public ClusterConfiguration call() throws InterruptedException, ExecutionException, ParseException, IOException, SQLException {
                            return getClusterConfiguration(locale, conn, cluster, drbdReports, lvmReports);
                        }
                    }
                )
            );
        }

        // Package the results
        SortedSet<ClusterConfiguration> clusterConfigurations = new TreeSet<ClusterConfiguration>();
        for(Future<ClusterConfiguration> future : futures) {
            clusterConfigurations.add(future.get());
        }
        return Collections.unmodifiableSortedSet(clusterConfigurations);
    }

    /**
     * Concurrently gets all of the DRBD reports for the entire cluster.  This doesn't perform
     * any sanity checks on the data, it merely parses it and ensures correct values.
     */
    public static Map<String,List<AOServer.DrbdReport>> getDrbdReports(
        final List<AOServer> aoServers,
        final Locale locale
    ) throws SQLException, InterruptedException, ExecutionException, ParseException, IOException {
        // Query concurrently for each of the drbdcstate's to get a good snapshot and determine primary/secondary locations
        Map<String,Future<List<AOServer.DrbdReport>>> futures = new HashMap<String,Future<List<AOServer.DrbdReport>>>(aoServers.size()*4/3+1);
        for(final AOServer aoServer : aoServers) {
            if(isEnabledDom0(aoServer)) {
                futures.put(
                    aoServer.getHostname(),
                    RootNodeImpl.executorService.submit(
                        new Callable<List<AOServer.DrbdReport>>() {
                            public List<AOServer.DrbdReport> call() throws ParseException, IOException, SQLException {
                                return aoServer.getDrbdReport(locale);
                            }
                        }
                    )
                );
            }
        }
        Map<String,List<AOServer.DrbdReport>> drbdReports = new HashMap<String,List<AOServer.DrbdReport>>(futures.size()*4/3+1);

        // Get and parse the results, also perform sanity checks
        for(Map.Entry<String,Future<List<AOServer.DrbdReport>>> entry : futures.entrySet()) {
            drbdReports.put(
                entry.getKey(),
                entry.getValue().get()
            );
        }
        return Collections.unmodifiableMap(drbdReports);
    }

    /**
     * Concurrently gets all of the LVM reports for the all clusters.  This doesn't perform
     * any sanity checks on the data, it merely parses it and ensures correct values.
     */
    public static Map<String,AOServer.LvmReport> getLvmReports(
        final List<AOServer> aoServers,
        final Locale locale
    ) throws SQLException, InterruptedException, ExecutionException, ParseException, IOException {
        Map<String,Future<AOServer.LvmReport>> futures = new HashMap<String,Future<AOServer.LvmReport>>(aoServers.size()*4/3+1);
        for(final AOServer aoServer : aoServers) {
            if(isEnabledDom0(aoServer)) {
                futures.put(
                    aoServer.getHostname(),
                    RootNodeImpl.executorService.submit(
                        new Callable<AOServer.LvmReport>() {
                            public AOServer.LvmReport call() throws IOException, SQLException, ParseException {
                                return aoServer.getLvmReport(locale);
                            }
                        }
                    )
                );
            }
        }
        Map<String,AOServer.LvmReport> lvmReports = new HashMap<String,AOServer.LvmReport>(futures.size()*4/3+1);

        // Get and parse the results, also perform sanity checks
        for(Map.Entry<String,Future<AOServer.LvmReport>> entry : futures.entrySet()) {
            lvmReports.put(
                entry.getKey(),
                entry.getValue().get()
            );
        }
        return Collections.unmodifiableMap(lvmReports);
    }

    /**
     * Concurrently gets all of the hard drive model reports for the all clusters.  This doesn't perform
     * any sanity checks on the data, it merely parses it and ensures correct values.
     */
    public static Map<String,Map<String,String>> getHddModelReports(
        final List<AOServer> aoServers,
        final Locale locale
    ) throws SQLException, InterruptedException, ExecutionException, ParseException, IOException {
        Map<String,Future<Map<String,String>>> futures = new HashMap<String,Future<Map<String,String>>>(aoServers.size()*4/3+1);
        for(final AOServer aoServer : aoServers) {
            if(isEnabledDom0(aoServer)) {
                futures.put(
                    aoServer.getHostname(),
                    RootNodeImpl.executorService.submit(
                        new Callable<Map<String,String>>() {
                            public Map<String,String> call() throws ParseException, IOException, SQLException {
                                return aoServer.getHddModelReport(locale);
                            }
                        }
                    )
                );
            }
        }
        Map<String,Map<String,String>> reports = new HashMap<String,Map<String,String>>(futures.size()*4/3+1);

        // Get and parse the results, also perform sanity checks
        for(Map.Entry<String,Future<Map<String,String>>> entry : futures.entrySet()) {
            reports.put(
                entry.getKey(),
                entry.getValue().get()
            );
        }
        return Collections.unmodifiableMap(reports);
    }

    /**
     * Loads the configuration for the provided cluster.
     */
    public static ClusterConfiguration getClusterConfiguration(
        Locale locale,
        AOServConnector conn,
        Cluster cluster,
        Map<String,List<AOServer.DrbdReport>> drbdReports,
        Map<String,AOServer.LvmReport> lvmReports
    ) throws InterruptedException, ExecutionException, ParseException, IOException, SQLException {
        final String rootAccounting = conn.businesses.getRootAccounting();

        ClusterConfiguration clusterConfiguration = new ClusterConfiguration(cluster);

        Map<String,String> drbdPrimaryDom0s = new HashMap<String,String>();
        Map<String,String> drbdSecondaryDom0s = new HashMap<String,String>();

        // Get and primary and secondary Dom0s from the DRBD report.
        // Also performs sanity checks on all the DRBD information.
        for(Map.Entry<String,List<AOServer.DrbdReport>> entry : drbdReports.entrySet()) {
            String dom0Hostname = entry.getKey();
            int lineNum = 0;
            for(AOServer.DrbdReport report : entry.getValue()) {
                lineNum++;
                // Must be a virtual server
                String domUHostname = report.getResourceHostname();
                Server domUServer = conn.servers.get(rootAccounting+"/"+domUHostname);
                if(domUServer==null) throw new ParseException(
                    ApplicationResourcesAccessor.getMessage(
                        locale,
                        "AOServClusterBuilder.ParseException.serverNotFound",
                        domUHostname
                    ),
                    lineNum
                );
                VirtualServer domUVirtualServer = domUServer.getVirtualServer();
                if(domUVirtualServer==null) throw new ParseException(
                    ApplicationResourcesAccessor.getMessage(
                        locale,
                        "AOServClusterBuilder.ParseException.notVirtualServer",
                        domUHostname
                    ),
                    lineNum
                );
                String domUDevice = report.getResourceDevice();
                if(
                    domUDevice.length()!=4
                    || domUDevice.charAt(0)!='x'
                    || domUDevice.charAt(1)!='v'
                    || domUDevice.charAt(2)!='d'
                    || domUDevice.charAt(3)<'a'
                    || domUDevice.charAt(3)>'z'
                ) throw new ParseException(
                    ApplicationResourcesAccessor.getMessage(
                        locale,
                        "AOServClusterBuilder.ParseException.unexpectedResourceEnding",
                        domUDevice
                    ),
                    lineNum
                );
                AOServer.DrbdReport.Role localRole = report.getLocalRole();
                if(localRole==AOServer.DrbdReport.Role.Primary) {
                    // Is Primary
                    String previousValue = drbdPrimaryDom0s.put(domUHostname, dom0Hostname);
                    if(previousValue!=null && !previousValue.equals(dom0Hostname)) throw new ParseException(
                        ApplicationResourcesAccessor.getMessage(
                            locale,
                            "AOServClusterBuilder.ParseException.multiPrimary",
                            domUHostname,
                            previousValue,
                            dom0Hostname
                        ),
                        lineNum
                    );
                } else if(localRole==AOServer.DrbdReport.Role.Secondary) {
                    // Is Secondary
                    String previousValue = drbdSecondaryDom0s.put(domUHostname, dom0Hostname);
                    if(previousValue!=null && !previousValue.equals(dom0Hostname)) throw new ParseException(
                        ApplicationResourcesAccessor.getMessage(
                            locale,
                            "AOServClusterBuilder.ParseException.multiSecondary",
                            domUHostname,
                            previousValue,
                            dom0Hostname
                        ),
                        lineNum
                    );
                } else {
                    throw new ParseException(
                        ApplicationResourcesAccessor.getMessage(
                            locale,
                            "AOServClusterBuilder.ParseException.unexpectedState",
                            localRole
                        ),
                        lineNum
                    );
                }
                
                // Find the corresponding VirtualDisk
                VirtualDisk virtualDisk = domUVirtualServer.getVirtualDisk(domUDevice);
                if(virtualDisk==null) {
                    //System.err.println("-- "+domUHostname);
                    //System.err.println("INSERT INTO virtual_disks VALUES(DEFAULT, "+domUVirtualServer.getPkey()+", '"+device+"', NULL, extents, 1, false, false);");
                    throw new ParseException(
                        ApplicationResourcesAccessor.getMessage(
                            locale,
                            "AOServClusterBuilder.ParseException.virtualDiskNotFound",
                            domUHostname,
                            domUDevice
                        ),
                        lineNum
                    );
                }
            }
        }

        // Now, each and every enabled virtual server must have both a primary and a secondary
        for(Map.Entry<String,DomU> entry : cluster.getDomUs().entrySet()) {
            String domUHostname = entry.getKey();
            DomU domU = entry.getValue();
            Server domUServer = conn.servers.get(rootAccounting+"/"+domUHostname);
            VirtualServer domUVirtualServer = domUServer.getVirtualServer();

            String primaryDom0Hostname = drbdPrimaryDom0s.get(domUHostname);
            if(primaryDom0Hostname==null) throw new ParseException(
                ApplicationResourcesAccessor.getMessage(
                    locale,
                    "AOServClusterBuilder.ParseException.primaryNotFound",
                    domUHostname
                ),
                0
            );

            String secondaryDom0Hostname = drbdSecondaryDom0s.get(domUHostname);
            if(secondaryDom0Hostname==null) throw new ParseException(
                ApplicationResourcesAccessor.getMessage(
                    locale,
                    "AOServClusterBuilder.ParseException.secondaryNotFound",
                    domUHostname
                ),
                0
            );
            clusterConfiguration = clusterConfiguration.addDomUConfiguration(
                domU,
                cluster.getDom0(primaryDom0Hostname),
                cluster.getDom0(secondaryDom0Hostname)
            );
            //DomUConfiguration domUConfiguration = clusterConfiguration.getDomUConfiguration(domU);
            //assert domUConfiguration!=null : "domUConfiguration is null";

            // Add each DomUDisk
            for(DomUDisk domUDisk : domU.getDomUDisks().values()) {
                // Must have been found once, and only once, on the primary server DRBD report
                int foundCount = 0;
                for(AOServer.DrbdReport report : drbdReports.get(primaryDom0Hostname)) {
                    if(report.getResourceHostname().equals(domUHostname) && report.getResourceDevice().equals(domUDisk.getDevice())) foundCount++;
                }
                if(foundCount!=1) throw new ParseException(
                    ApplicationResourcesAccessor.getMessage(
                        locale,
                        "AOServClusterBuilder.ParseException.drbdDomUDiskShouldBeFoundOnce",
                        domUDisk.getDevice(),
                        primaryDom0Hostname
                    ),
                    0
                );

                // Must have been found once, and only once, on the secondary server DRBD report
                foundCount = 0;
                for(AOServer.DrbdReport report : drbdReports.get(secondaryDom0Hostname)) {
                    if(report.getResourceHostname().equals(domUHostname) && report.getResourceDevice().equals(domUDisk.getDevice())) foundCount++;
                }
                if(foundCount!=1) throw new ParseException(
                    ApplicationResourcesAccessor.getMessage(
                        locale,
                        "AOServClusterBuilder.ParseException.drbdDomUDiskShouldBeFoundOnce",
                        domUDisk.getDevice(),
                        secondaryDom0Hostname
                    ),
                    0
                );
                
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
            for(Map.Entry<String,AOServer.LvmReport> entry : lvmReports.entrySet()) {
                String dom0Hostname = entry.getKey();
                // Verify within this cluster only
                if(cluster.getDom0s().containsKey(dom0Hostname)) {
                    AOServer.LvmReport lvmReport = entry.getValue();
                    for(Map.Entry<String,AOServer.LvmReport.VolumeGroup> vgEntry : lvmReport.getVolumeGroups().entrySet()) {
                        String vgName = vgEntry.getKey();
                        AOServer.LvmReport.VolumeGroup volumeGroup = vgEntry.getValue();
                        // This should still validate the logical volumes in the off chance a virtual server is named "backup"
                        DomU domU = cluster.getDomU(vgName);
                        if(domU==null) {
                            if(!vgName.equals("backup")) throw new AssertionError("Volume group found but there is no virtual server of the same name: "+dom0Hostname+":/dev/"+vgName);
                        } else {
                            // Make sure primary or secondary on this Dom0
                            String domUHostname = domU.getHostname();
                            String primaryDom0Hostname = drbdPrimaryDom0s.get(domUHostname);
                            String secondaryDom0Hostname = drbdSecondaryDom0s.get(domUHostname);
                            if(
                                !primaryDom0Hostname.equals(dom0Hostname)
                                && !secondaryDom0Hostname.equals(dom0Hostname)
                            ) throw new AssertionError("Volume group found but the virtual server is neither primary nor secondary on this physical server: "+dom0Hostname+":/dev/"+vgName);

                            // Make sure every logical volume found in LVM equals a domUDisk
                            for(String lvName : volumeGroup.getLogicalVolumes().keySet()) {
                                if(!lvName.endsWith("-drbd")) throw new AssertionError("lvName does not end with -drbd: "+lvName);
                                DomUDisk domUDisk = domU.getDomUDisk(lvName.substring(0, lvName.length()-5));
                                if(domUDisk==null) throw new AssertionError("Logical volume found but the virtual server does not have a corresponding virtual disk: "+dom0Hostname+":/dev/"+vgName+"/"+lvName);
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
        Map<String,AOServer.LvmReport> lvmReports,
        String dom0Hostname
    ) {
        AOServer.LvmReport lvmReport = lvmReports.get(dom0Hostname);
        if(lvmReport==null) throw new AssertionError("No lvm report found for "+dom0Hostname);
        // Find the Dom0
        Dom0 dom0 = cluster.getDom0(dom0Hostname);
        if(dom0==null) throw new AssertionError("Dom0 not found: "+dom0Hostname);
        // Should have a volume group equal to its hostname
        String domUHostname = domUDisk.getDomUHostname();
        AOServer.LvmReport.VolumeGroup volumeGroup = lvmReport.getVolumeGroup(domUHostname);
        if(volumeGroup==null) throw new AssertionError("No volume group named "+domUHostname+" found on "+dom0Hostname);
        // Should have a logical volume named {device}-drbd
        String logicalVolumeName = domUDisk.getDevice()+"-drbd";
        AOServer.LvmReport.LogicalVolume logicalVolume = volumeGroup.getLogicalVolume(logicalVolumeName);
        if(logicalVolume==null) throw new AssertionError("No logical volume named "+logicalVolumeName+" found on "+dom0Hostname+":"+domUHostname);
        // Each logical volume may have any number of segments
        List<PhysicalVolumeConfiguration> configs = new ArrayList<PhysicalVolumeConfiguration>();
        for(AOServer.LvmReport.Segment segment : logicalVolume.getSegments()) {
            AOServer.LvmReport.SegmentType segmentType = segment.getSegtype();
            if(segmentType!=AOServer.LvmReport.SegmentType.linear && segmentType!=AOServer.LvmReport.SegmentType.striped) {
                throw new AssertionError("Only linear and striped segments currently supported");
            }
            // This is somewhat of an over-simplication as it treats any striped segment as if each stripe is linearly appended.
            // This should be close enough for the optimization purposes.
            long firstLogicalExtent = segment.getSegStartPe();
            // Each segment may have multiple stripes
            for(AOServer.LvmReport.Stripe stripe : segment.getStripes()) {
                assert stripe.getPhysicalVolume().getVolumeGroup().getVgName().equals(domUHostname) : "stripe.physicalVolume.volumeGroup.vgName!=domUHostname";
                String partition = stripe.getPhysicalVolume().getPvName();
                // Count the number of digits on the right of partition
                int digitCount = 0;
                for(int c=partition.length()-1; c>=0; c--) {
                    char ch=partition.charAt(c);
                    if(ch>='0' && ch<='9') digitCount++;
                    else break;
                }
                if(digitCount==0) throw new AssertionError("No partition number found on physical volume: "+partition);
                String device = partition.substring(0, partition.length()-digitCount);
                short partitionNum = Short.parseShort(partition.substring(partition.length()-digitCount));

                // Find the Dom0Disk
                Dom0Disk dom0Disk = dom0.getDom0Disk(device);
                if(dom0Disk==null) throw new AssertionError("Unable to find Dom0Disk: "+dom0Hostname+":"+device);

                // Find the Dom0 physical volume
                PhysicalVolume physicalVolume = dom0Disk.getPhysicalVolume(partitionNum);
                if(physicalVolume==null) throw new AssertionError("Unable to find PhysicalVolume: "+dom0Hostname+":"+device+partitionNum);

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
