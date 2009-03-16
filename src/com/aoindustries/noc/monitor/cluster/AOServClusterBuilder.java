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
import com.aoindustries.aoserv.cluster.DomU;
import com.aoindustries.aoserv.cluster.DomUDisk;
import com.aoindustries.aoserv.cluster.ProcessorArchitecture;
import com.aoindustries.aoserv.cluster.ProcessorType;
import com.aoindustries.noc.monitor.RootNodeImpl;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    /** Make no instances */
    private AOServClusterBuilder() {}

    /**
     * Loads an unmodifiable set of the current cluster states from the AOServ system.
     * Only ServerFarms that have at least one enabled Dom0 are included.
     * 
     * @see Cluster
     */
    public static SortedSet<Cluster> getClusters(final AOServConnector conn) throws InterruptedException, ExecutionException {
        List<AOServer> aoServers = conn.aoServers.getRows();
        List<ServerFarm> serverFarms = conn.serverFarms.getRows();

        // Start concurrently
        List<Future<Cluster>> futures = new ArrayList<Future<Cluster>>(serverFarms.size());
        for(final ServerFarm serverFarm : serverFarms) {
            // Only create the cluster if there is at least one dom0 machine
            boolean foundDom0 = false;
            for(AOServer aoServer : aoServers) {
                Server server = aoServer.getServer();
                if(server.isMonitoringEnabled() && server.getServerFarm().equals(serverFarm)) {
                    OperatingSystemVersion osvObj = server.getOperatingSystemVersion();
                    if(osvObj!=null) {
                        int osv = osvObj.getPkey();
                        if(osv==OperatingSystemVersion.CENTOS_5DOM0_I686 || osv==OperatingSystemVersion.CENTOS_5DOM0_X86_64) {
                            foundDom0 = true;
                            break;
                        }
                    }
                }
            }
            if(foundDom0) {
                futures.add(
                    RootNodeImpl.executorService.submit(
                        new Callable<Cluster>() {
                            public Cluster call() throws SQLException, InterruptedException, ExecutionException, ParseException {
                                return getCluster(conn, serverFarm);
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
    public static Cluster getCluster(AOServConnector conn, ServerFarm serverFarm) throws SQLException, InterruptedException, ExecutionException, ParseException {
        final String rootAccounting = conn.businesses.getRootAccounting();

        Cluster cluster = new Cluster(serverFarm.getName());

        // Get the Dom0s
        for(AOServer aoServer : conn.aoServers.getRows()) {
            Server server = aoServer.getServer();
            if(server.isMonitoringEnabled() && server.getServerFarm().equals(serverFarm)) {
                OperatingSystemVersion osvObj = server.getOperatingSystemVersion();
                if(osvObj!=null) {
                    int osv = osvObj.getPkey();
                    if(osv==OperatingSystemVersion.CENTOS_5DOM0_I686 || osv==OperatingSystemVersion.CENTOS_5DOM0_X86_64) {
                        // This should be a physical server and not a virtual server
                        PhysicalServer physicalServer = server.getPhysicalServer();
                        if(physicalServer==null) throw new SQLException("Dom0 server is not a physical server: "+aoServer);
                        VirtualServer virtualServer = server.getVirtualServer();
                        if(virtualServer!=null) throw new SQLException("Dom0 server is a virtual server: "+aoServer);
                        String hostname = aoServer.getHostname();
                        cluster = cluster.addDom0(
                            hostname,
                            /*rack,*/
                            physicalServer.getRam(),
                            ProcessorType.valueOf(physicalServer.getProcessorType().getType()),
                            ProcessorArchitecture.valueOf(osvObj.getArchitecture(conn).getName().toUpperCase(Locale.ENGLISH)),
                            physicalServer.getProcessorSpeed(),
                            physicalServer.getProcessorCores(),
                            physicalServer.getSupportsHvm()
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

        // Concurrently get the results of pvdisplay and 
        /*for(Map.Entry<String,List<PvDisplay>> entry : PvDisplay.getPvDisplays(cluster.getDom0s().values(), conn, Locale.getDefault()).entrySet()) {
            String dom0Hostname = entry.getKey();
            List<PvDisplay> reports = entry.getValue();
            Dom0 dom0 = cluster.getDom0(dom0Hostname);
            if(dom0==null) throw new AssertionError("dom0 is null");
            //cluster.addDomUDisk(
         * TODO
        }*/
        return cluster;
    }

    /**
     * Loads an unmodifiable set of the current cluster configuration from the AOServ system.
     * 
     * @see  #getClusters
     * @see  #getClusterConfiguration
     */
    public static SortedSet<ClusterConfiguration> getClusterConfigurations(final Locale locale, final AOServConnector conn) throws InterruptedException, ExecutionException {
        SortedSet<Cluster> clusters = getClusters(conn);
        // Start concurrently
        List<Future<ClusterConfiguration>> futures = new ArrayList<Future<ClusterConfiguration>>(clusters.size());
        for(final Cluster cluster : clusters) {
            futures.add(
                RootNodeImpl.executorService.submit(
                    new Callable<ClusterConfiguration>() {
                        public ClusterConfiguration call() throws InterruptedException, ExecutionException, ParseException {
                            return getClusterConfiguration(locale, conn, cluster);
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
        Cluster cluster,
        AOServConnector conn,
        final Locale locale
    ) throws InterruptedException, ExecutionException, ParseException {
        Map<String,Dom0> dom0s = cluster.getDom0s();
        int mapSize = dom0s.size()*4/3+1;

        // Query concurrently for each of the drbdcstate's to get a good snapshot and determine primary/secondary locations
        Map<String,Future<List<AOServer.DrbdReport>>> futures = new HashMap<String,Future<List<AOServer.DrbdReport>>>(mapSize);
        for(String hostname : dom0s.keySet()) {
            final AOServer aoServer = conn.aoServers.get(hostname);
            if(aoServer==null) throw new AssertionError("aoServer is null");
            futures.put(
                hostname,
                RootNodeImpl.executorService.submit(
                    new Callable<List<AOServer.DrbdReport>>() {
                        public List<AOServer.DrbdReport> call() throws ParseException {
                            return aoServer.getDrbdReport(locale);
                        }
                    }
                )
            );
        }
        Map<String,List<AOServer.DrbdReport>> drbdReports = new HashMap<String,List<AOServer.DrbdReport>>(mapSize);

        // Get and parse the results, also perform sanity checks
        for(Map.Entry<String,Future<List<AOServer.DrbdReport>>> entry : futures.entrySet()) {
            drbdReports.put(
                entry.getKey(),
                entry.getValue().get()
            );
        }
        return drbdReports;
    }

    /**
     * Loads the configuration for the provided cluster.
     */
    public static ClusterConfiguration getClusterConfiguration(Locale locale, AOServConnector conn, Cluster cluster) throws InterruptedException, ExecutionException, ParseException {
        final String rootAccounting = conn.businesses.getRootAccounting();

        ClusterConfiguration clusterConfiguration = new ClusterConfiguration(cluster);
        
        Map<String,List<AOServer.DrbdReport>> drbdReports = getDrbdReports(cluster, conn, locale);

        Map<String,String> drbdPrimaryDom0s = new HashMap<String,String>();
        Map<String,String> drbdSecondaryDom0s = new HashMap<String,String>();
        processDrbdReports(conn, locale, drbdReports, drbdPrimaryDom0s, drbdSecondaryDom0s);

        // Now, each and every enabled virtual server must have both a primary and a secondary
        for(Map.Entry<String,DomU> entry : cluster.getDomUs().entrySet()) {
            String domUHostname = entry.getKey();
            DomU domU = entry.getValue();
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
            
            // Add each DomUDisk
            for(DomUDisk domUDisk : domU.getDomUDisks().values()) {
                // Must have been found once, and only once, on the primary server DRBD report
                int foundCount = 0;
                for(AOServer.DrbdReport report : drbdReports.get(primaryDom0Hostname)) {
                    if(report.getDomUHostname().equals(domUHostname) && report.getDomUDevice().equals(domUDisk.getDevice())) foundCount++;
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
                    if(report.getDomUHostname().equals(domUHostname) && report.getDomUDevice().equals(domUDisk.getDevice())) foundCount++;
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
                
                // TODO
            }
        }

        return clusterConfiguration;
    }

    /**
     * Makes sure that everything in the DRBD reports is for valid virtual servers
     * and virtual disks and has sane values.
     */
    private static void processDrbdReports(
        AOServConnector conn,
        Locale locale,
        Map<String,List<AOServer.DrbdReport>> drbdReports,
        Map<String,String> drbdPrimaryDom0s,
        Map<String,String> drbdSecondaryDom0s
    ) throws ParseException {
        final String rootAccounting = conn.businesses.getRootAccounting();

        // Get and primary and secondary Dom0s from the DRBD report.
        // Also performs sanity checks on all the DRBD information.
        for(Map.Entry<String,List<AOServer.DrbdReport>> entry : drbdReports.entrySet()) {
            String dom0Hostname = entry.getKey();
            int lineNum = 0;
            for(AOServer.DrbdReport report : entry.getValue()) {
                lineNum++;
                // Must be a virtual server
                String domUHostname = report.getDomUHostname();
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
                String domUDevice = report.getDomUDevice();
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
    }

    /**
     * Adds a single Dom0 to cluster.
     */
    /*private static Dom0 addDom0(Cluster cluster, AOServer aoServer) {
        // Load info
        String hostname = aoServer.getHostname();
        
        // These could be done concurrently if needed
        String nodeinfo = aoServer.getNodeInfoReport();
        String cpuinfo = aoServer.getCpuInfoReport();
        String pvdisplay = aoServer.getPvDisplayReport();
        String lvdisplay = aoServer.getLvDisplayReport();
        //String drbdconf; // Needs to include primary/secondary
        String xmlist; // To determine current primary state
        String xenconfig; // To determine RAM allocations, CPU cores, and CPU weights for any machine running on this Dom0

        int ram = 2048; // TODO: get from nodeInfo
        ProcessorType processorType = ProcessorType.P4; // TODO: get from cpuInfo
        ProcessorArchitecture processorArchitecture = ProcessorArchitecture.I686; // TODO: get from osv and confirm with nodeInfo
        int processorSpeed = 2800; // TODO: get from cpuInfo "model name"
        int processorCores = 2; // TODO: get from nodeInfo "CPU(s):"

        synchronized(cluster) {
            Dom0 dom0 = cluster.addDom0(hostname, /*rack,/ ram, processorType, processorArchitecture, processorSpeed, processorCores);

            // Add Dom0Disks
            // TODO: Add PhysicalVolumes
            //      device from pvdisplay
            //      raidtype assume single for now
            //      DiskType:
            //          /dev/hd? - IDE
            //          /dev/sd? - check /sys/block/sda/device/model, hard-code as needed
            //      DiskSpeed:
            //          /dev/hd? - 7200 RPM
            //          /dev/sd? - check /sys/block/sda/device/model, hard-code as needed
            /*Dom0Disk gw1Hda = gw1.addDom0Disk("/dev/hda", RaidType.SINGLE, DiskType.IDE, 7200);
             * 
             * partition - get from pvdisplay
             * extents - get from pvdisplay
                PhysicalVolume gw1Hda5 = gw1Hda.addPhysicalVolume(5, 896);
                PhysicalVolume gw1Hda6 = gw1Hda.addPhysicalVolume(6, 896);
                PhysicalVolume gw1Hda7 = gw1Hda.addPhysicalVolume(7, 253);
             
             * skip any pv with vg of "backup"
             * 
             * Make sure 
             *
            return dom0;
        }
    }*/
}
