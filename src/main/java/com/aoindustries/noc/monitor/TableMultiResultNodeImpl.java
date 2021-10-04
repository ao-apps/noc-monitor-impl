/*
 * noc-monitor-impl - Implementation of Network Operations Center Monitoring.
 * Copyright (C) 2008-2012, 2014, 2018, 2020, 2021  AO Industries, Inc.
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

import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.TableMultiResult;
import com.aoindustries.noc.monitor.common.TableMultiResultListener;
import com.aoindustries.noc.monitor.common.TableMultiResultNode;
import java.rmi.RemoteException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;

/**
 * The node for table results.
 *
 * @author  AO Industries, Inc.
 */
public abstract class TableMultiResultNodeImpl<R extends TableMultiResult> extends NodeImpl implements TableMultiResultNode<R> {

	private static final Logger logger = Logger.getLogger(TableMultiResultNodeImpl.class.getName());

	private static final long serialVersionUID = 1L;

	public final RootNodeImpl rootNode;
	final NodeImpl parent;
	final TableMultiResultNodeWorker<?, R> worker;

	private final List<TableMultiResultListener<? super R>> tableMultiResultListeners = new ArrayList<>();

	protected TableMultiResultNodeImpl(RootNodeImpl rootNode, NodeImpl parent, TableMultiResultNodeWorker<?, R> worker, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws RemoteException {
		super(port, csf, ssf);
		this.rootNode = rootNode;
		this.parent = parent;
		this.worker = worker;
	}

	@Override
	public final NodeImpl getParent() {
		return parent;
	}

	@Override
	public final boolean getAllowsChildren() {
		return false;
	}

	@Override
	public final List<? extends NodeImpl> getChildren() {
		return Collections.emptyList();
	}

	@Override
	public final AlertLevel getAlertLevel() {
		AlertLevel alertLevel = worker.getAlertLevel();
		return constrainAlertLevel(alertLevel == null ? AlertLevel.UNKNOWN : alertLevel);
	}

	@Override
	public final String getAlertMessage() {
		Function<Locale, String> alertMessage = worker.getAlertMessage();
		return alertMessage == null ? null : alertMessage.apply(rootNode.locale);
	}

	public final void start() {
		worker.addTableMultiResultNodeImpl(this);
	}

	public final void stop() {
		worker.removeTableMultiResultNodeImpl(this);
	}

	@Override
	public final List<? extends R> getResults() {
		return worker.getResults();
	}

	/**
	 * Called by the worker when the alert level changes.
	 */
	final void nodeAlertLevelChanged(AlertLevel oldAlertLevel, AlertLevel newAlertLevel, Function<Locale, String> newAlertMessage) throws RemoteException {
		assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

		rootNode.nodeAlertLevelChanged(
			this,
			constrainAlertLevel(oldAlertLevel),
			constrainAlertLevel(newAlertLevel),
			newAlertMessage == null ? null : newAlertMessage.apply(rootNode.locale)
		);
	}

	@Override
	public final void addTableMultiResultListener(TableMultiResultListener<? super R> tableMultiResultListener) {
		synchronized(tableMultiResultListeners) {
			tableMultiResultListeners.add(tableMultiResultListener);
		}
	}

	@Override
	public final void removeTableMultiResultListener(TableMultiResultListener<? super R> tableMultiResultListener) {
		synchronized(tableMultiResultListeners) {
			for(int c=tableMultiResultListeners.size()-1;c>=0;c--) {
				if(tableMultiResultListeners.get(c).equals(tableMultiResultListener)) {
					tableMultiResultListeners.remove(c);
					// Remove only once, in case add and remove come in out of order with quick GUI changes
					return;
				}
			}
		}
		logger.log(Level.WARNING, null, new AssertionError("Listener not found: " + tableMultiResultListener));
	}

	/**
	 * Notifies all of the listeners.
	 */
	final void tableMultiResultAdded(R tableMultiResult) {
		assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

		synchronized(tableMultiResultListeners) {
			Iterator<TableMultiResultListener<? super R>> I = tableMultiResultListeners.iterator();
			while(I.hasNext()) {
				TableMultiResultListener<? super R> tableMultiResultListener = I.next();
				try {
					tableMultiResultListener.tableMultiResultAdded(tableMultiResult);
				} catch(RemoteException err) {
					I.remove();
					logger.log(Level.SEVERE, null, err);
				}
			}
		}
	}

	/**
	 * Notifies all of the listeners.
	 */
	final void tableMultiResultRemoved(R tableMultiResult) {
		assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

		synchronized(tableMultiResultListeners) {
			Iterator<TableMultiResultListener<? super R>> I = tableMultiResultListeners.iterator();
			while(I.hasNext()) {
				TableMultiResultListener<? super R> tableMultiResultListener = I.next();
				try {
					tableMultiResultListener.tableMultiResultRemoved(tableMultiResult);
				} catch(RemoteException err) {
					I.remove();
					logger.log(Level.SEVERE, null, err);
				}
			}
		}
	}
}
