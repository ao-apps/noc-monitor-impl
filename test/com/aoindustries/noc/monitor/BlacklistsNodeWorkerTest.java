package com.aoindustries.noc.monitor;

/*
 * Copyright 2009 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
import java.util.HashSet;
import java.util.Set;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * @author  AO Industries, Inc.
 */
public class BlacklistsNodeWorkerTest extends TestCase {

    public BlacklistsNodeWorkerTest(String testName) {
        super(testName);
    }

    public static Test suite() {
        TestSuite suite = new TestSuite(BlacklistsNodeWorkerTest.class);
        return suite;
    }

    public void testCheckForDuplicateBlacklists() throws Exception {
        /*
        // Make sure there are no duplicates
        System.out.println("Total of "+BlacklistsNodeWorker.rblBlacklists.length+" RBL blacklists");
        Set<String> basenames = new HashSet<String>(BlacklistsNodeWorker.rblBlacklists.length*4/3+1);
        for(BlacklistsNodeWorker.RblBlacklist rblBlacklist : BlacklistsNodeWorker.rblBlacklists) {
            if(!basenames.add(rblBlacklist.basename)) {
                System.err.println(rblBlacklist.basename);
                throw new RuntimeException("Duplicate basename: "+rblBlacklist.basename);
            } else {
                System.out.println(rblBlacklist.basename);
            }
        }*/
    }
}
