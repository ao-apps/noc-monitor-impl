/*
 * noc-monitor-impl - Implementation of Network Operations Center Monitoring.
 * Copyright (C) 2008, 2009, 2016, 2018, 2019, 2020  AO Industries, Inc.
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
 * along with noc-monitor-impl.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.aoindustries.noc.monitor.cluster;

import com.aoindustries.aoserv.client.AOServConnector;
import com.aoindustries.aoserv.client.linux.Server;
import com.aoindustries.aoserv.cluster.Cluster;
import com.aoindustries.aoserv.cluster.ClusterConfiguration;
import com.aoindustries.aoserv.cluster.ProcessorArchitecture;
import com.aoindustries.aoserv.cluster.ProcessorType;
import com.aoindustries.aoserv.cluster.analyze.AlertLevel;
import com.aoindustries.aoserv.cluster.analyze.AnalyzedClusterConfiguration;
import com.aoindustries.aoserv.cluster.analyze.AnalyzedClusterConfigurationPrinter;
import com.aoindustries.aoserv.cluster.optimize.ClusterOptimizer;
import com.aoindustries.aoserv.cluster.optimize.ExponentialDeviationHeuristicFunction;
import com.aoindustries.aoserv.cluster.optimize.ExponentialDeviationWithNoneHeuristicFunction;
import com.aoindustries.aoserv.cluster.optimize.ExponentialHeuristicFunction;
import com.aoindustries.aoserv.cluster.optimize.HeuristicFunction;
import com.aoindustries.aoserv.cluster.optimize.LeastInformedHeuristicFunction;
import com.aoindustries.aoserv.cluster.optimize.LinearHeuristicFunction;
import com.aoindustries.aoserv.cluster.optimize.ListElement;
import com.aoindustries.aoserv.cluster.optimize.SimpleHeuristicFunction;
import com.aoindustries.aoserv.cluster.optimize.Transition;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Tests ClusterResourceManager.
 * 
 * Helpful SQL queries for tuning system:
select
  vs.server,
  se.name,
  (vs.primary_ram || '/' || vs.primary_ram_target) as primary_ram,
  (coalesce(vs.secondary_ram::text, 'NULL') || '/' || coalesce(vs.secondary_ram_target::text, 'NULL')) as secondary_ram,
  (coalesce(vs.minimum_processor_speed::text, 'NULL') || '/' || coalesce(vs.minimum_processor_speed_target::text, 'NULL')) as minimum_processor_speed,
  (vs.processor_cores || '/' || vs.processor_cores_target) as processor_cores,
  (vs.processor_weight || '/' || vs.processor_weight_target) as processor_weight
from
  servers se
  inner join virtual_servers vs on se.pkey=vs.server
where
  vs.primary_ram!=vs.primary_ram_target
  or coalesce(vs.secondary_ram::text, 'NULL')!=coalesce(vs.secondary_ram_target::text, 'NULL')
  or coalesce(vs.minimum_processor_speed::text, 'NULL')!=coalesce(vs.minimum_processor_speed_target::text, 'NULL')
  or vs.processor_cores!=vs.processor_cores_target
  or vs.processor_weight!=vs.processor_weight_target
order by net."Host.reverseFqdn"(se.name);


select
  vd.pkey,
  se.name,
  vd.device,
  (coalesce(vd.minimum_disk_speed::text, 'NULL') || '/' || coalesce(vd.minimum_disk_speed_target::text, 'NULL')) as minimum_disk_speed,
  vd.extents,
  (coalesce(vd.weight::text, 'NULL') || '/' || coalesce(vd.weight_target::text, 'NULL')) as weight
from
  virtual_disks vd
  inner join servers se on vd.virtual_server=se.pkey
where
  coalesce(vd.minimum_disk_speed::text, 'NULL')!=coalesce(vd.minimum_disk_speed_target::text, 'NULL')
  or coalesce(vd.weight::text, 'NULL')!=coalesce(vd.weight_target::text, 'NULL')
order by net."Host.reverseFqdn"(se.name), vd.device;

 * @author  AO Industries, Inc.
 */
@SuppressWarnings("UseOfSystemOutOrSystemErr")
public class ClusterResourceManagerTODO extends TestCase {

	private static final Logger logger = Logger.getLogger(ClusterResourceManagerTODO.class.getName());

	private static final boolean FIND_SHORTEST_PATH = true;

	private static final boolean USE_TARGET = false;

	private static final boolean ALLOW_PATH_THROUGH_CRITICAL = false;

	private static final boolean RANDOMIZE_CHILDREN = false;

	private AOServConnector conn;
	private SortedSet<ClusterConfiguration> clusterConfigurations;

	public ClusterResourceManagerTODO(String testName) {
		super(testName);
	}

	private static SortedSet<Cluster> addDom0(
		SortedSet<Cluster> oldClusters,
		String clusterName,
		String hostname,
		//Rack rack,
		int ram,
		ProcessorType processorType,
		ProcessorArchitecture processorArchitecture,
		int processorSpeed,
		int processorCores,
		boolean supportsHvm
	) {
		// Find the cluster
		Cluster oldCluster = null;
		for(Cluster cluster : oldClusters) {
			if(cluster.getName().equals(clusterName)) {
				oldCluster = cluster;
				break;
			}
		}
		if(oldCluster==null) throw new AssertionError("Cluster not found: "+clusterName);

		// Make sure not already in cluster
		if(oldCluster.getDom0(hostname)!=null) throw new AssertionError("Cluster already has Dom0 named "+hostname);

		Cluster newCluster = oldCluster.addDom0(hostname, ram, processorType, processorArchitecture, processorSpeed, processorCores, supportsHvm);

		// Build the new set from the old, replacing the appropriate cluster configuration
		SortedSet<Cluster> newClusters = new TreeSet<>();
		for(Cluster cluster : oldClusters) {
			newClusters.add(cluster==oldCluster ? newCluster : cluster);
		}
		return Collections.unmodifiableSortedSet(newClusters);
	}

	private static SortedSet<Cluster> addDom0Disk(
		SortedSet<Cluster> oldClusters,
		String clusterName,
		String hostname,
		String device,
		int speed
	) {
		// Find the cluster
		Cluster oldCluster = null;
		for(Cluster cluster : oldClusters) {
			if(cluster.getName().equals(clusterName)) {
				oldCluster = cluster;
				break;
			}
		}
		if(oldCluster==null) throw new AssertionError("Cluster not found: "+clusterName);

		Cluster newCluster = oldCluster.addDom0Disk(hostname, device, speed);

		// Build the new set from the old, replacing the appropriate cluster configuration
		SortedSet<Cluster> newClusters = new TreeSet<>();
		for(Cluster cluster : oldClusters) {
			newClusters.add(cluster==oldCluster ? newCluster : cluster);
		}
		return Collections.unmodifiableSortedSet(newClusters);
	}

	private static SortedSet<Cluster> addPhysicalVolume(
		SortedSet<Cluster> oldClusters,
		String clusterName,
		String hostname,
		String device,
		short parition,
		long extents
	) {
		// Find the cluster
		Cluster oldCluster = null;
		for(Cluster cluster : oldClusters) {
			if(cluster.getName().equals(clusterName)) {
				oldCluster = cluster;
				break;
			}
		}
		if(oldCluster==null) throw new AssertionError("Cluster not found: "+clusterName);

		Cluster newCluster = oldCluster.addPhysicalVolume(hostname, device, parition, extents);

		// Build the new set from the old, replacing the appropriate cluster configuration
		SortedSet<Cluster> newClusters = new TreeSet<>();
		for(Cluster cluster : oldClusters) {
			newClusters.add(cluster==oldCluster ? newCluster : cluster);
		}
		return Collections.unmodifiableSortedSet(newClusters);
	}

	/**
	 * Adds a 146 GB SCSI drive to the provided server.
	 */
	private static SortedSet<Cluster> addScsi146(SortedSet<Cluster> clusters, String clusterName, String hostname, String scsiDevice) {
		short[] scsiPartitions30 = {1, 2, 3, 5};
		short[] scsiLastPartitions = {6};
		clusters = addDom0Disk(clusters, clusterName, hostname, scsiDevice, 15000);
		for(short partition : scsiPartitions30) {
			clusters = addPhysicalVolume(clusters, clusterName, hostname, scsiDevice, partition, 896);
		}
		for(short partition : scsiLastPartitions) {
			clusters = addPhysicalVolume(clusters, clusterName, hostname, scsiDevice, partition, 790);
		}
		return clusters;
	}

	/**
	 * Adds a 500 GB SATA drive to the provided server.
	 */
	private static SortedSet<Cluster> addSata500(SortedSet<Cluster> clusters, String clusterName, String hostname, String sataDevice) {
		short[] sataPartitions60 = {1, 2, 3};
		short[] sataPartitions30 = {5, 6, 7, 8, 9, 10, 11, 12, 13, 14};
		short[] sataLastPartitions = {15};
		clusters = addDom0Disk(clusters, clusterName, hostname, sataDevice, 7200);
		for(short partition : sataPartitions60) {
			clusters = addPhysicalVolume(clusters, clusterName, hostname, sataDevice, partition, 1792);
		}
		for(short partition : sataPartitions30) {
			clusters = addPhysicalVolume(clusters, clusterName, hostname, sataDevice, partition, 896);
		}
		for(short partition : sataLastPartitions) {
			clusters = addPhysicalVolume(clusters, clusterName, hostname, sataDevice, partition, 561);
		}
		return clusters;
	}

	private static SortedSet<Cluster> addXen9146(SortedSet<Cluster> clusters) {
		clusters = addDom0(clusters, "fc", "xen914-6.fc.aoindustries.com", 24576, ProcessorType.XEON_LV, ProcessorArchitecture.X86_64, 2333, 8, true);
		// Add drives
		String[] sataDevices = {
			"/dev/sda",
			"/dev/sdb",
			"/dev/sdc",
			"/dev/sdd",
			"/dev/sde",
			"/dev/sdf",
			"/dev/sdg",
			"/dev/sdh",
			"/dev/sdi",
			"/dev/sdj",
			"/dev/sdk",
			"/dev/sdl"
		};
		for(String sataDevice : sataDevices) {
			clusters = addSata500(clusters, "fc", "xen914-6.fc.aoindustries.com", sataDevice);
		}
		String[] scsiDevices = {
			"/dev/sdm",
			"/dev/sdn",
			"/dev/sdo",
			"/dev/sdp"
		};
		for(String scsiDevice : scsiDevices) {
			clusters = addScsi146(clusters, "fc", "xen914-6.fc.aoindustries.com", scsiDevice);
		}
		return clusters;
	}

	private static SortedSet<Cluster> addXen9147(SortedSet<Cluster> clusters) {
		clusters = addDom0(clusters, "fc", "xen914-7.fc.aoindustries.com", 24576, ProcessorType.XEON_LV, ProcessorArchitecture.X86_64, 2333, 8, true);
		// Add drives
		String[] sataDevices = {
			"/dev/sda",
			"/dev/sdb",
			"/dev/sdc",
			"/dev/sdd",
			"/dev/sde",
			"/dev/sdf",
			"/dev/sdg",
			"/dev/sdh",
			"/dev/sdi",
			"/dev/sdj",
			"/dev/sdk",
			"/dev/sdl"
		};
		for(String sataDevice : sataDevices) {
			clusters = addSata500(clusters, "fc", "xen914-7.fc.aoindustries.com", sataDevice);
		}
		String[] scsiDevices = {
			"/dev/sdm",
			"/dev/sdn",
			"/dev/sdo",
			"/dev/sdp"
		};
		for(String scsiDevice : scsiDevices) {
			clusters = addScsi146(clusters, "fc", "xen914-7.fc.aoindustries.com", scsiDevice);
		}
		return clusters;
	}

	private static SortedSet<Cluster> addDrivesXen9071(SortedSet<Cluster> clusters, boolean sata, boolean scsi) {
		if(sata) {
			// Add drives
			String[] sataDevices = {
				"/dev/sdo",
				"/dev/sdp"
			};
			for(String sataDevice : sataDevices) {
				clusters = addSata500(clusters, "fc", "xen907-1.fc.aoindustries.com", sataDevice);
			}
		}
		if(scsi) {
			String[] scsiDevices = {
				"/dev/sdq",
				"/dev/sdr"
			};
			for(String scsiDevice : scsiDevices) {
				clusters = addScsi146(clusters, "fc", "xen907-1.fc.aoindustries.com", scsiDevice);
			}
		}
		return clusters;
	}

	private static SortedSet<Cluster> addDrivesXen9145(SortedSet<Cluster> clusters) {
		// Add drives
		String[] sataDevices = {
			"/dev/sdg",
			"/dev/sdh",
			"/dev/sdi",
			"/dev/sdj",
			"/dev/sdk",
			"/dev/sdl"
		};
		for(String sataDevice : sataDevices) {
			clusters = addSata500(clusters, "fc", "xen914-5.fc.lnxhosting.ca", sataDevice);
		}
		String[] scsiDevices = {
			"/dev/sdm",
			"/dev/sdn",
			"/dev/sdo",
			"/dev/sdp"
		};
		for(String scsiDevice : scsiDevices) {
			clusters = addScsi146(clusters, "fc", "xen914-5.fc.lnxhosting.ca", scsiDevice);
		}
		return clusters;
	}

	private static SortedSet<Cluster> addDrivesXen9175(SortedSet<Cluster> clusters, boolean sata, boolean scsi) {
		if(sata) {
			// Add drives
			String[] sataDevices = {
				"/dev/sdh",
				"/dev/sdi",
				"/dev/sdj",
				"/dev/sdk",
				"/dev/sdl",
				"/dev/sdm",
				"/dev/sdn"
			};
			for(String sataDevice : sataDevices) {
				clusters = addSata500(clusters, "fc", "xen917-5.fc.aoindustries.com", sataDevice);
			}
		}
		if(scsi) {
			String[] scsiDevices = {
				"/dev/sdo",
				"/dev/sdp"
			};
			for(String scsiDevice : scsiDevices) {
				clusters = addScsi146(clusters, "fc", "xen917-5.fc.aoindustries.com", scsiDevice);
			}
		}
		return clusters;
	}

	@Override
	@SuppressWarnings({"UseSpecificCatch", "TooBroadCatch"})
	protected void setUp() throws Exception {
		conn = AOServConnector.getConnector();
		try {
			List<Server> linuxServers = conn.getLinux().getServer().getRows();
			Locale locale = Locale.getDefault();
			Map<String,Map<String,String>> hddModelReports = AOServClusterBuilder.getHddModelReports(linuxServers, locale);
			Map<String,Server.LvmReport> lvmReports = AOServClusterBuilder.getLvmReports(linuxServers, locale);
			Map<String,List<Server.DrbdReport>> drbdReports = AOServClusterBuilder.getDrbdReports(linuxServers, locale);
			SortedSet<Cluster> clusters = AOServClusterBuilder.getClusters(conn, linuxServers, hddModelReports, lvmReports, USE_TARGET);
			if(USE_TARGET) {
				// See what happens if we add an additional server
				clusters = addDrivesXen9071(clusters, true, true);
				clusters = addDrivesXen9145(clusters);
				clusters = addDrivesXen9175(clusters, true, true);
				clusters = addXen9146(clusters);
				clusters = addXen9147(clusters);
			} else {
				// Exploring what we can do with only additional SATA hard drives
				//clusters = addDrivesXen9071(clusters, true, false);
				//clusters = addDrivesXen9175(clusters, true, false);
			}
			// TODO: Because can't enforce disk weights, can only control through allocation
			// TODO: Allocate and check disks matched by weight.
			clusterConfigurations = AOServClusterBuilder.getClusterConfigurations(Locale.getDefault(), conn, clusters, drbdReports, lvmReports);
		} catch(Exception err) {
			logger.log(Level.SEVERE, null, err);
			throw err;
		}
	}

	@Override
	protected void tearDown() throws Exception {
		clusterConfigurations = null;
		conn = null;
	}

	public static Test suite() {
		TestSuite suite = new TestSuite(ClusterResourceManagerTODO.class);
		return suite;
	}

	public void testAnalyzeCluster() throws Exception {
		List<AnalyzedClusterConfiguration> analyzedClusterConfigurations = new ArrayList<>(clusterConfigurations.size());
		for(ClusterConfiguration clusterConfiguration : clusterConfigurations) {
			analyzedClusterConfigurations.add(new AnalyzedClusterConfiguration(clusterConfiguration));
		}
		PrintWriter out = new PrintWriter(System.out);
		try {
			AnalyzedClusterConfigurationPrinter.print(analyzedClusterConfigurations, out, AlertLevel.NONE);
		} finally {
			out.flush();
		}
	}

	public void testHeuristicFunctions() throws Exception {
		List<HeuristicFunction> heuristicFunctions = new ArrayList<>();
		heuristicFunctions.add(new LeastInformedHeuristicFunction());
		heuristicFunctions.add(new SimpleHeuristicFunction());
		heuristicFunctions.add(new LinearHeuristicFunction());
		heuristicFunctions.add(new ExponentialHeuristicFunction());
		heuristicFunctions.add(new ExponentialDeviationHeuristicFunction());
		heuristicFunctions.add(new ExponentialDeviationWithNoneHeuristicFunction());
		for(ClusterConfiguration clusterConfiguration : clusterConfigurations) {
			System.out.println(clusterConfiguration);
			for(HeuristicFunction heuristicFunction : heuristicFunctions) {
				System.out.println("    "+heuristicFunction.getClass().getName()+": "+heuristicFunction.getHeuristic(clusterConfiguration, 0));
			}
		}
	}

	/*
	public void testSaveClusterConfigurations() throws Exception {
		ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream("clusterConfigurations.ser")));
		try {
			out.writeObject(clusterConfigurations);
		} finally {
			out.close();
		}
	}*/

	private static void printTransitions(ListElement path) {
		ListElement previous = path.getPrevious();
		if(previous!=null) printTransitions(previous);
		Transition transition = path.getTransition();
		if(transition==null) {
			System.out.println("            Initial State ("+path.getHeuristic()+")");
		} else {
			System.out.println("            "+transition+" ("+path.getHeuristic()+")");
		}
	}

	public void testOptimizedCluster() throws Exception {
		List<HeuristicFunction> heuristicFunctions = new ArrayList<>();
		//heuristicFunctions.add(new LeastInformedHeuristicFunction());
		//heuristicFunctions.add(new SimpleHeuristicFunction());
		//heuristicFunctions.add(new LinearHeuristicFunction());
		//heuristicFunctions.add(new ExponentialHeuristicFunction());
		heuristicFunctions.add(new ExponentialDeviationHeuristicFunction());
		//heuristicFunctions.add(new ExponentialDeviationWithNoneHeuristicFunction());
		//heuristicFunctions.add(new RandomHeuristicFunction());
		for(final ClusterConfiguration clusterConfiguration : clusterConfigurations) {
			System.out.println(clusterConfiguration);
			for(final HeuristicFunction heuristicFunction : heuristicFunctions) {
				System.out.println("    "+heuristicFunction.getClass().getName());
				//System.out.println("        Initial State ("+heuristicFunction.getHeuristic(clusterConfiguration, 0)+")");
				ClusterOptimizer optimized = new ClusterOptimizer(clusterConfiguration, heuristicFunction, ALLOW_PATH_THROUGH_CRITICAL, RANDOMIZE_CHILDREN);
				ListElement shortestPath = optimized.getOptimizedClusterConfiguration((ListElement path, long loopCount) -> {
					// TODO: Emphasize anything with a critical alert level when showing transitions
					System.out.println("        Goal found using "+path.getPathLen()+(path.getPathLen()==1 ? " transition" : " transitions")+" in "+loopCount+(loopCount==1?" iteration" : " iterations"));
					printTransitions(path);
					// Stop at the first one found
					return FIND_SHORTEST_PATH;
				});
				if(shortestPath==null) System.out.println("        Goal not found");
				else if(FIND_SHORTEST_PATH && shortestPath.getPathLen()>0) System.out.println("        Yeah! Shortest path to optimal configuration found!!!");
			}
		}
	}
}
