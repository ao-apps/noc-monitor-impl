package com.aoindustries.noc.monitor;

/*
 * Copyright 2008-2009 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
import com.aoindustries.aoserv.client.AOServConnector;
import com.aoindustries.aoserv.cluster.ClusterConfiguration;
import com.aoindustries.aoserv.cluster.analyze.AnalyzedClusterConfiguration;
import com.aoindustries.aoserv.cluster.analyze.AnalyzedClusterConfigurationPrinter;
import com.aoindustries.aoserv.cluster.optimize.ExponentialDeviationHeuristicFunction;
import com.aoindustries.aoserv.cluster.optimize.ExponentialHeuristicFunction;
import com.aoindustries.aoserv.cluster.optimize.HeuristicFunction;
import com.aoindustries.aoserv.cluster.optimize.LeastInformedHeuristicFunction;
import com.aoindustries.aoserv.cluster.optimize.LinearHeuristicFunction;
import com.aoindustries.aoserv.cluster.optimize.ClusterOptimizer;
import com.aoindustries.aoserv.cluster.optimize.OptimizedClusterConfigurationHandler;
import com.aoindustries.aoserv.cluster.optimize.SimpleHeuristicFunction;
import com.aoindustries.aoserv.cluster.optimize.Transition;
import com.aoindustries.util.StandardErrorHandler;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.SortedSet;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Tests ClusterResourceManager.
 * 
 * Helpful SQL queries for tuning system:
 *   select vs.server, se.name, vs.primary_ram, vs.secondary_ram from servers se inner join virtual_servers vs on se.pkey=vs.server order by se.name;
 *
 * @author  AO Industries, Inc.
 */
public class ClusterResourceManagerTest extends TestCase {

    private AOServConnector conn;
    private SortedSet<ClusterConfiguration> clusterConfigurations;

    public ClusterResourceManagerTest(String testName) {
        super(testName);
    }

    @Override
    protected void setUp() throws Exception {
        conn = AOServConnector.getConnector(new StandardErrorHandler());
        clusterConfigurations = ClusterResourceManager.getClusterConfigurations(Locale.getDefault(), conn);
    }

    @Override
    protected void tearDown() throws Exception {
        clusterConfigurations = null;
        conn = null;
    }

    public static Test suite() {
        TestSuite suite = new TestSuite(ClusterResourceManagerTest.class);
        return suite;
    }

    public void testAnalyzeCluster() throws Exception {
        List<AnalyzedClusterConfiguration> analyzedClusterConfigurations = new ArrayList<AnalyzedClusterConfiguration>(clusterConfigurations.size());
        for(ClusterConfiguration clusterConfiguration : clusterConfigurations) analyzedClusterConfigurations.add(new AnalyzedClusterConfiguration(clusterConfiguration));
        PrintWriter out = new PrintWriter(System.out);
        try {
            AnalyzedClusterConfigurationPrinter.print(analyzedClusterConfigurations, out);
        } finally {
            out.flush();
        }
    }

    public void testHeuristicFunctions() throws Exception {
        List<HeuristicFunction> heuristicFunctions = new ArrayList<HeuristicFunction>();
        heuristicFunctions.add(new LeastInformedHeuristicFunction());
        heuristicFunctions.add(new SimpleHeuristicFunction());
        heuristicFunctions.add(new LinearHeuristicFunction());
        heuristicFunctions.add(new ExponentialHeuristicFunction());
        heuristicFunctions.add(new ExponentialDeviationHeuristicFunction());
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

    public void testOptimizedCluster() throws Exception {
        List<HeuristicFunction> heuristicFunctions = new ArrayList<HeuristicFunction>();
        //heuristicFunctions.add(new LeastInformedHeuristicFunction());
        //heuristicFunctions.add(new SimpleHeuristicFunction());
        //heuristicFunctions.add(new LinearHeuristicFunction());
        //heuristicFunctions.add(new ExponentialHeuristicFunction());
        heuristicFunctions.add(new ExponentialDeviationHeuristicFunction());
        //heuristicFunctions.add(new RandomHeuristicFunction());
        for(final ClusterConfiguration clusterConfiguration : clusterConfigurations) {
            System.out.println(clusterConfiguration);
            for(final HeuristicFunction heuristicFunction : heuristicFunctions) {
                System.out.println("    "+heuristicFunction.getClass().getName());
                ClusterOptimizer optimized = new ClusterOptimizer(clusterConfiguration, heuristicFunction, false);
                List<Transition> transitions = optimized.getOptimizedClusterConfiguration(
                    new OptimizedClusterConfigurationHandler() {
                        private List<Transition> shortestPath = null;
                        public boolean handleOptimizedClusterConfiguration(List<Transition> path, long loopCount) {
                            if(shortestPath==null || path.size()<shortestPath.size()) {
                                shortestPath = path;
                                // TODO: Emphasize anything with a critical alert level when showing transitions
                                System.out.println("        Goal found using "+path.size()+(path.size()==1 ? " transition" : " transitions")+" in "+loopCount+(loopCount==1?" iteration" : " iterations"));
                                System.out.println("            Initial State ("+heuristicFunction.getHeuristic(clusterConfiguration, 0)+")");
                                for(int move=0, size=path.size(); move<size; move++) {
                                    Transition transition = path.get(move);
                                    System.out.println("            "+transition+" ("+heuristicFunction.getHeuristic(transition.getAfterClusterConfiguration(), move)+")");
                                }
                            }
                            return true;
                        }
                    }
                );
                if(transitions==null) System.out.println("        Goal not found");
                else System.out.println("Yeah! Shortest path to optimal configuration found!!!");
            }
        }
    }
}
