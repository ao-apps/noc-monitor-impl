/*
 * noc-monitor-impl - Implementation of Network Operations Center Monitoring.
 * Copyright (C) 2008, 2009, 2014, 2018, 2020  AO Industries, Inc.
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
package com.aoindustries.noc.monitor;

import com.aoindustries.collections.AoCollections;
import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.noc.monitor.common.AlertCategory;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.Node;
import java.rmi.RemoteException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Stack;

/**
 * One in the list of nodes that form the systems tree.
 *
 * @author  AO Industries, Inc.
 */
public abstract class NodeImpl extends UnicastRemoteObject implements Node {

	private static final long serialVersionUID = 1L;

	final protected int port;
	final protected RMIClientSocketFactory csf;
	final protected RMIServerSocketFactory ssf;

	protected NodeImpl(int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws RemoteException {
		super(port, csf, ssf);
		this.port = port;
		this.csf = csf;
		this.ssf = ssf;
	}

	@Override
	abstract public NodeImpl getParent();

	@Override
	abstract public List<? extends NodeImpl> getChildren();

	/**
	 * For thread safety and encapsulation, returns an unmodifiable copy of the list.
	 */
	protected static <E extends NodeImpl> List<E> getSnapshot(List<E> children) {
		return AoCollections.unmodifiableCopyList(children);
	}

	/**
	 * For thread safety and encapsulation, returns an unmodifiable copy of the list.
	 * Any null child is skipped.
	 */
	protected static <E extends NodeImpl> List<E> getSnapshot(
		E child
	) {
		if(child==null) return Collections.emptyList();
		return Collections.singletonList(child);
	}

	/**
	 * For thread safety and encapsulation, returns an unmodifiable copy of the list.
	 * Any null child is skipped.
	 */
	protected static <E extends NodeImpl> List<E> getSnapshot(
		E child1,
		E child2
	) {
		if(child1==null) return getSnapshot(child2);
		if(child2==null) return getSnapshot(child1);
		List<E> children = new ArrayList<>(2);
		children.add(child1);
		children.add(child2);
		return Collections.unmodifiableList(children);
	}

	/**
	 * For thread safety and encapsulation, returns an unmodifiable copy of the list.
	 * Any null child is skipped.
	 */
	protected static <E extends NodeImpl> List<E> getSnapshot(
		E child1,
		E child2,
		E child3
	) {
		if(child1==null) return getSnapshot(child2, child3);
		if(child2==null) return getSnapshot(child1, child3);
		if(child3==null) return getSnapshot(child1, child2);
		List<E> children = new ArrayList<>(3);
		children.add(child1);
		children.add(child2);
		children.add(child3);
		return Collections.unmodifiableList(children);
	}

	/**
	 * For thread safety and encapsulation, returns an unmodifiable copy of the list.
	 * Any null child is skipped.
	 */
	protected static <E extends NodeImpl> List<E> getSnapshot(
		E child1,
		E child2,
		E child3,
		E child4
	) {
		if(child1==null) return getSnapshot(child2, child3, child4);
		if(child2==null) return getSnapshot(child1, child3, child4);
		if(child3==null) return getSnapshot(child1, child2, child4);
		if(child4==null) return getSnapshot(child1, child2, child3);
		List<E> children = new ArrayList<>(4);
		children.add(child1);
		children.add(child2);
		children.add(child3);
		children.add(child4);
		return Collections.unmodifiableList(children);
	}

	/**
	 * For thread safety and encapsulation, returns an unmodifiable copy of the list.
	 * Any null child is skipped.
	 */
	@SafeVarargs
	protected static <E extends NodeImpl> List<E> getSnapshot(E ... children) {
		List<E> list = new ArrayList<>(children.length);
		for(E child : children) {
			if(child != null) list.add(child);
		}
		return AoCollections.optimalUnmodifiableList(list);
	}

	/**
	 * Every node may optionally constrain the maximum alert level for itself and all of
	 * its children.
	 */
	protected AlertLevel getMaxAlertLevel() {
		return AlertLevel.UNKNOWN;
	}

	/**
	 * Makes sure the alert level does not exceed the maximum for this
	 * node or any of its parents.  Will reduce the level to not exceed
	 * any maximum.
	 */
	protected AlertLevel constrainAlertLevel(AlertLevel level) {
		if(level == AlertLevel.NONE) return AlertLevel.NONE;
		NodeImpl node = this;
		do {
			AlertLevel maxAlertLevel = node.getMaxAlertLevel();
			if(level.compareTo(maxAlertLevel) > 0) {
				level = maxAlertLevel;
				if(level == AlertLevel.NONE) return AlertLevel.NONE;
			}
			node = node.getParent();
		} while(node != null);
		return level;
	}

	/**
	 * This alert level must be constrained by the maximum alert level of this
	 * node and all of its parents.
	 * 
	 * @see #constrainAlertLevel(com.aoindustries.noc.monitor.NodeImpl, com.aoindustries.noc.monitor.common.AlertLevel) 
	 */
	@Override
	abstract public AlertLevel getAlertLevel();

	@Override
	abstract public String getAlertMessage();

	/**
	 * Defaults to the alert category of the parent node.
	 *
	 * @see  #getParent()
	 *
	 * @throws  NullPointerException when there is no parent
	 */
	@Override
	public AlertCategory getAlertCategory() {
		return getParent().getAlertCategory();
	}

	@Override
	abstract public boolean getAllowsChildren();

	@Override
	abstract public String getLabel();

	/**
	 * The default toString is the label.
	 */
	@Override
	public String toString() {
		return getLabel();
	}

	/**
	 * Gets the full path to the node.
	 */
	String getFullPath(Locale locale) throws RemoteException {
		String pathSeparator = accessor.getMessage(locale, "Node.nodeAlertLevelChanged.alertMessage.pathSeparator");
		final StringBuilder fullPath = new StringBuilder();
		Stack<Node> path = new Stack<>();
		Node parent = this;
		while(parent.getParent()!=null) {
			path.push(parent);
			parent = parent.getParent();
		}
		while(!path.isEmpty()) {
			if(fullPath.length()>0) fullPath.append(pathSeparator);
			fullPath.append(path.pop().getLabel());
		}
		return fullPath.toString();
	}
}
