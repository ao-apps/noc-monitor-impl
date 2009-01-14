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
import com.aoindustries.aoserv.client.VirtualServer;
import com.aoindustries.aoserv.cluster.Cluster;
import com.aoindustries.aoserv.cluster.Dom0;
import com.aoindustries.aoserv.cluster.DomU;
import com.aoindustries.aoserv.cluster.ProcessorArchitecture;
import com.aoindustries.aoserv.cluster.ProcessorType;
import com.aoindustries.noc.monitor.RootNodeImpl;
import java.sql.SQLException;
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

    /**
     * Loads an unmodifiable set of the current cluster states from the AOServ system.
     * Only ServerFarms that have at least one Dom0 are included.
     * It tries to do as much as possible in parallel to minimize the
     * time and get the best possible snapshot.
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
                if(server.getServerFarm().equals(serverFarm)) {
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
     * Loads a cluster from a single server farm with as much concurrency as possible to get the best snapshot.
     */
    public static Cluster getCluster(AOServConnector conn, ServerFarm serverFarm) throws SQLException, InterruptedException, ExecutionException {
        List<AOServer> aoServers = conn.aoServers.getRows();

        final Cluster cluster = new Cluster(serverFarm.getName());
        final Map<PhysicalServer,Dom0> dom0s = new HashMap<PhysicalServer,Dom0>();

        // Get the Dom0s with concurrency
        //List<Future<Dom0>> dom0Futures = new ArrayList<Future<Dom0>>(aoServers.size());
        for(final AOServer aoServer : aoServers) {
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
                        Dom0 dom0 = cluster.addDom0(
                            aoServer.getHostname(),
                            /*rack,*/
                            physicalServer.getRam(),
                            ProcessorType.valueOf(physicalServer.getProcessorType().getType()),
                            ProcessorArchitecture.valueOf(osvObj.getArchitecture(conn).getName().toUpperCase(Locale.ENGLISH)),
                            physicalServer.getProcessorSpeed(),
                            physicalServer.getProcessorCores(),
                            physicalServer.getSupportsHvm()
                        );
                        dom0s.put(physicalServer, dom0);
                        /*dom0Futures.add(
                            RootNodeImpl.executorService.submit(
                                new Callable<Dom0>() {
                                    public Dom0 call() {
                                        return addDom0(cluster, aoServer);
                                    }
                                }
                            )
                        );*/
                    }
                }
            }
        }

        // Wait for all Dom0s to be ready
        /*Map<String,Dom0> dom0s = new HashMap<String,Dom0>(dom0Futures.size()*4/3+1);
        for(Future<Dom0> future : dom0Futures) {
            Dom0 dom0 = future.get();
            dom0s.put(dom0.getHostname(), dom0);
        }*/

        // Get the DomUs with concurrency
        for(Server server : conn.servers.getRows()) {
            if(server.isMonitoringEnabled() && server.getServerFarm().equals(serverFarm)) {
                // Should be either physical or virtual server
                PhysicalServer physicalServer = server.getPhysicalServer();
                VirtualServer virtualServer = server.getVirtualServer();
                if(physicalServer==null && virtualServer==null) throw new SQLException("Server is neither a physical server nor a virtual server: "+server);
                if(physicalServer!=null && virtualServer!=null) throw new SQLException("Server is both a physical server and a virtual server: "+server);
                if(virtualServer!=null) {
                    DomU domU = cluster.addDomU(
                        server.toString(),
                        virtualServer.getPrimaryRam(),
                        virtualServer.getSecondaryRam(),
                        virtualServer.getPrimaryMinimumProcessorType()==null ? null : ProcessorType.valueOf(virtualServer.getPrimaryMinimumProcessorType().getType()),
                        virtualServer.getSecondaryMinimumProcessorType()==null ? null : ProcessorType.valueOf(virtualServer.getSecondaryMinimumProcessorType().getType()),
                        ProcessorArchitecture.valueOf(virtualServer.getMinimumProcessorArchitecture().getName().toUpperCase(Locale.ENGLISH)),
                        virtualServer.getPrimaryMinimumProcessorSpeed(),
                        virtualServer.getSecondaryMinimumProcessorSpeed(),
                        virtualServer.getPrimaryProcessorCores(),
                        virtualServer.getSecondaryProcessorCores(),
                        virtualServer.getPrimaryProcessorWeight(),
                        virtualServer.getSecondaryProcessorWeight(),
                        virtualServer.getRequiresHvm(),
                        dom0s.get(virtualServer.getPrimaryPhysicalServer()),
                        virtualServer.isPrimaryPhysicalServerLocked(),
                        dom0s.get(virtualServer.getSecondaryPhysicalServer()),
                        virtualServer.isSecondaryPhysicalServerLocked()
                    );
                }
            }
        }

        return cluster;
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
