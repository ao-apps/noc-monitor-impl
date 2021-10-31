/*
 * noc-monitor-impl - Implementation of Network Operations Center Monitoring.
 * Copyright (C) 2009-2013, 2016, 2018, 2020  AO Industries, Inc.
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
package com.aoindustries.noc.monitor.email;

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
		Set<String> basenames = AoCollections.newHashSet(BlacklistsNodeWorker.rblBlacklists.length);
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
