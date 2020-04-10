/*
 * Copyright 2018, 2020 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import java.util.Iterator;

class CollectionUtils {

	static <T> boolean containsByIdentity(Iterable<T> iterable, T elem) {
		Iterator<T> iter = iterable.iterator();
		while(iter.hasNext()) {
			if(iter.next() == elem) return true;
		}
		return false;
	}

	private CollectionUtils() {}
}
