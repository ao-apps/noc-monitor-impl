package com.aoindustries.noc.monitor.cluster;

/*
 * Copyright 2008-2009 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
import com.aoindustries.aoserv.client.AOServConnector;
import com.aoindustries.aoserv.cluster.Cluster;
import com.aoindustries.aoserv.cluster.analyze.AnalyzedCluster;
import com.aoindustries.aoserv.cluster.analyze.AnalyzedClusterPrinter;
import com.aoindustries.aoserv.cluster.optimize.ExponentialHeuristicFunction;
import com.aoindustries.aoserv.cluster.optimize.HeuristicFunction;
import com.aoindustries.aoserv.cluster.optimize.LinearHeuristicFunction;
import com.aoindustries.aoserv.cluster.optimize.OptimizedCluster;
import com.aoindustries.aoserv.cluster.optimize.SimpleHeuristicFunction;
import com.aoindustries.aoserv.cluster.optimize.Transition;
import com.aoindustries.util.StandardErrorHandler;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedSet;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Tests ClusterResourceManager.
 *
 * @author  AO Industries, Inc.
 */
public class ClusterResourceManagerTest extends TestCase {

    private AOServConnector conn;
    private SortedSet<Cluster> clusters;

    public ClusterResourceManagerTest(String testName) {
        super(testName);
    }

    @Override
    protected void setUp() throws Exception {
        conn = AOServConnector.getConnector(new StandardErrorHandler());
        clusters = ClusterResourceManager.getClusters(conn);
    }

    @Override
    protected void tearDown() throws Exception {
        clusters = null;
        conn = null;
    }

    public static Test suite() {
        TestSuite suite = new TestSuite(ClusterResourceManagerTest.class);
        return suite;
    }

    public void testAnalyzeCluster() throws Exception {
        List<AnalyzedCluster> analyzedClusters = new ArrayList<AnalyzedCluster>(clusters.size());
        for(Cluster cluster : clusters) analyzedClusters.add(new AnalyzedCluster(cluster));
        PrintWriter out = new PrintWriter(System.out);
        try {
            AnalyzedClusterPrinter.print(analyzedClusters, out);
        } finally {
            out.flush();
        }
    }

    public void testHeuristicFunctions() throws Exception {
        List<HeuristicFunction> heuristicFunctions = new ArrayList<HeuristicFunction>();
        heuristicFunctions.add(new SimpleHeuristicFunction());
        heuristicFunctions.add(new LinearHeuristicFunction());
        heuristicFunctions.add(new ExponentialHeuristicFunction());
        for(Cluster cluster : clusters) {
            System.out.println(cluster);
            AnalyzedCluster analysis = new AnalyzedCluster(cluster);
            for(HeuristicFunction heuristicFunction : heuristicFunctions) {
                System.out.println("    "+heuristicFunction.getClass().getName()+": "+heuristicFunction.getHeuristic(analysis, 0));
            }
        }
    }

    /*public void testSaveClusters() throws Exception {
        ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream("clusters.ser")));
        try {
            out.writeObject(clusters);
        } finally {
            out.close();
        }
    }*/

    public void testOptimizedCluster() throws Exception {
        List<HeuristicFunction> heuristicFunctions = new ArrayList<HeuristicFunction>();
        heuristicFunctions.add(new SimpleHeuristicFunction());
        heuristicFunctions.add(new LinearHeuristicFunction());
        heuristicFunctions.add(new ExponentialHeuristicFunction());
        for(Cluster cluster : clusters) {
            System.out.println(cluster);
            for(HeuristicFunction heuristicFunction : heuristicFunctions) {
                System.out.println("    "+heuristicFunction.getClass().getName());
                OptimizedCluster optimized = new OptimizedCluster(cluster, heuristicFunction);
                List<Transition> transitions = optimized.getPath();
                if(transitions==null) System.out.println("        Goal not found in "+optimized.getLoopCount()+(optimized.getLoopCount()==1?" iteration" : " iterations"));
                else {
                    System.out.println("        Goal found using "+transitions.size()+(transitions.size()==1 ? " transition" : " transitions")+" in "+optimized.getLoopCount()+(optimized.getLoopCount()==1?" iteration" : " iterations"));
                    int move = 0;
                    for(Transition transition : transitions) {
                        System.out.println("            "+transition+" ("+heuristicFunction.getHeuristic(new AnalyzedCluster(transition.getAfterCluster()), move)+")");
                        move++;
                    }
                }
            }
        }
    }
}
