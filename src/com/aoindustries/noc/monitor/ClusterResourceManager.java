/*
 * Copyright 2008-2009 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.aoserv.client.AOServConnector;
import com.aoindustries.aoserv.client.AOServer;
import com.aoindustries.aoserv.client.OperatingSystemVersion;
import com.aoindustries.aoserv.client.PhysicalServer;
import com.aoindustries.aoserv.client.Server;
import com.aoindustries.aoserv.client.ServerFarm;
import com.aoindustries.aoserv.client.VirtualServer;
import com.aoindustries.aoserv.cluster.Cluster;
import com.aoindustries.aoserv.cluster.ClusterConfiguration;
import com.aoindustries.aoserv.cluster.Dom0;
import com.aoindustries.aoserv.cluster.DomU;
import com.aoindustries.aoserv.cluster.ProcessorArchitecture;
import com.aoindustries.aoserv.cluster.ProcessorType;
import com.aoindustries.util.StringUtility;
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
 * Managed cluster resources.
 *
 * @author  AO Industries, Inc.
 */
public class ClusterResourceManager {

    /** Make no instances */
    private ClusterResourceManager() {}

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
                            public Cluster call() throws SQLException, InterruptedException, ExecutionException {
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
    public static Cluster getCluster(AOServConnector conn, ServerFarm serverFarm) throws SQLException, InterruptedException, ExecutionException {
        List<AOServer> aoServers = conn.aoServers.getRows();

        Cluster cluster = new Cluster(serverFarm.getName());
        Map<PhysicalServer,Dom0> dom0s = new HashMap<PhysicalServer,Dom0>();

        // Get the Dom0s
        for(AOServer aoServer : aoServers) {
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
                        Dom0 dom0 = cluster.getDom0(hostname);
                        assert dom0!=null : "dom0 is null";
                        dom0s.put(physicalServer, dom0);
                    }
                }
            }
        }

        String rootAccounting = conn.businesses.getRootAccounting();

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
                    cluster = cluster.addDomU(
                        server.getName(),
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
     * Loads the configuration for the provided cluster.
     */
    public static ClusterConfiguration getClusterConfiguration(Locale locale, AOServConnector conn, Cluster cluster) throws InterruptedException, ExecutionException, ParseException {
        ClusterConfiguration clusterConfiguration = new ClusterConfiguration(cluster);
        Map<String,Dom0> dom0s = cluster.getDom0s();
        // Query concurrently for each of the drbdcstate's to get a good snapshot and determine primary/secondary locations
        Map<String,Future<String>> futures = new HashMap<String,Future<String>>(dom0s.size()*4/3+1);
        for(String hostname : dom0s.keySet()) {
            final AOServer aoServer = conn.aoServers.get(hostname);
            assert aoServer!=null : "aoServer is null";
            futures.put(
                hostname,
                RootNodeImpl.executorService.submit(
                    new Callable<String>() {
                        public String call() {
                            return aoServer.getDrbdReport();
                        }
                    }
                )
            );
        }
        
        // For each DomU, we keep track of its primaryDom0 - it is an error if more than one primaryDom0 is found
        Map<String,String> primaryDom0s = new HashMap<String,String>();

        // For each DomU, we keep track of its secondaryDom0 - it is an error if more than one secondaryDom0 is found
        Map<String,String> secondaryDom0s = new HashMap<String,String>();

        String rootAccounting = conn.businesses.getRootAccounting();
        
        // Get and parse the results, also perform sanity checks
        for(Map.Entry<String,Future<String>> entry : futures.entrySet()) {
            String dom0Hostname = entry.getKey();
            String report = entry.getValue().get();
            List<String> lines = StringUtility.splitLines(report);
            int lineNum = 0;
            for(String line : lines) {
                lineNum++;
                String[] values = StringUtility.splitString(line, '\t');
                if(values.length!=5) {
                    throw new ParseException(
                        com.aoindustries.noc.monitor.ApplicationResourcesAccessor.getMessage(
                            locale,
                            "ClusterResourceManager.ParseException.badColumnCount",
                            line
                        ),
                        lineNum
                    );
                }
                String resource = values[1];
                // Should have a - near the end
                int dashPos = resource.lastIndexOf('-');
                if(dashPos==-1) throw new ParseException(
                    com.aoindustries.noc.monitor.ApplicationResourcesAccessor.getMessage(
                        locale,
                        "ClusterResourceManager.ParseException.noDash",
                        resource
                    ),
                    lineNum
                );
                String domUHostname = resource.substring(0, dashPos);
                // Must be a virtual server
                Server domUServer = conn.servers.get(rootAccounting+"/"+domUHostname);
                if(domUServer==null) throw new ParseException(
                    com.aoindustries.noc.monitor.ApplicationResourcesAccessor.getMessage(
                        locale,
                        "ClusterResourceManager.ParseException.serverNotFound",
                        domUHostname
                    ),
                    lineNum
                );
                VirtualServer domUVirtualServer = domUServer.getVirtualServer();
                if(domUVirtualServer==null) throw new ParseException(
                    com.aoindustries.noc.monitor.ApplicationResourcesAccessor.getMessage(
                        locale,
                        "ClusterResourceManager.ParseException.notVirtualServer",
                        domUHostname
                    ),
                    lineNum
                );
                String ending = resource.substring(dashPos+1);
                if(
                    ending.length()!=4
                    || ending.charAt(0)!='x'
                    || ending.charAt(1)!='v'
                    || ending.charAt(2)!='d'
                    || ending.charAt(3)<'a'
                    || ending.charAt(3)>'z'
                ) throw new ParseException(
                    com.aoindustries.noc.monitor.ApplicationResourcesAccessor.getMessage(
                        locale,
                        "ClusterResourceManager.ParseException.unexpectedResourceEnding",
                        ending
                    ),
                    lineNum
                );

                String state = values[4];
                int slashPos = state.indexOf('/');
                if(slashPos==-1) throw new ParseException(
                    com.aoindustries.noc.monitor.ApplicationResourcesAccessor.getMessage(
                        locale,
                        "ClusterResourceManager.ParseException.noSlashInState",
                        state
                    ),
                    lineNum
                );
                state = state.substring(0, slashPos);
                if("Primary".equals(state)) {
                    // Is Primary
                    String previousValue = primaryDom0s.put(domUHostname, dom0Hostname);
                    if(previousValue!=null && !previousValue.equals(dom0Hostname)) throw new ParseException(
                        com.aoindustries.noc.monitor.ApplicationResourcesAccessor.getMessage(
                            locale,
                            "ClusterResourceManager.ParseException.multiPrimary",
                            domUHostname,
                            previousValue,
                            dom0Hostname
                        ),
                        lineNum
                    );
                } else if("Secondary".equals(state)) {
                    // Is Secondary
                    String previousValue = secondaryDom0s.put(domUHostname, dom0Hostname);
                    if(previousValue!=null && !previousValue.equals(dom0Hostname)) throw new ParseException(
                        com.aoindustries.noc.monitor.ApplicationResourcesAccessor.getMessage(
                            locale,
                            "ClusterResourceManager.ParseException.multiSecondary",
                            domUHostname,
                            previousValue,
                            dom0Hostname
                        ),
                        lineNum
                    );
                } else {
                    throw new ParseException(
                        com.aoindustries.noc.monitor.ApplicationResourcesAccessor.getMessage(
                            locale,
                            "ClusterResourceManager.ParseException.unexpectedState",
                            state
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
            String primaryDom0Hostname = primaryDom0s.get(domUHostname);
            if(primaryDom0Hostname==null) throw new ParseException(
                com.aoindustries.noc.monitor.ApplicationResourcesAccessor.getMessage(
                    locale,
                    "ClusterResourceManager.ParseException.primaryNotFound",
                    domUHostname
                ),
                0
            );
            String secondaryDom0Hostname = secondaryDom0s.get(domUHostname);
            if(secondaryDom0Hostname==null) throw new ParseException(
                com.aoindustries.noc.monitor.ApplicationResourcesAccessor.getMessage(
                    locale,
                    "ClusterResourceManager.ParseException.secondaryNotFound",
                    domUHostname
                ),
                0
            );
            clusterConfiguration = clusterConfiguration.addDomUConfiguration(
                domU,
                dom0s.get(primaryDom0Hostname),
                dom0s.get(secondaryDom0Hostname)
            );
        }

        // TODO: add resources appropriately
        return clusterConfiguration;
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
