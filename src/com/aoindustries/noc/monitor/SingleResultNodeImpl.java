/*
 * Copyright 2008-2009, 2014, 2018 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.SingleResult;
import com.aoindustries.noc.monitor.common.SingleResultListener;
import com.aoindustries.noc.monitor.common.SingleResultNode;
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
 * The node for single results.
 *
 * @author  AO Industries, Inc.
 */
abstract public class SingleResultNodeImpl extends NodeImpl implements SingleResultNode {

	private static final Logger logger = Logger.getLogger(SingleResultNodeImpl.class.getName());

	private static final long serialVersionUID = 1L;

	public final RootNodeImpl rootNode;
	protected final NodeImpl parent;
	private final SingleResultNodeWorker worker;

	final private List<SingleResultListener> singleResultListeners = new ArrayList<>();

	protected SingleResultNodeImpl(RootNodeImpl rootNode, NodeImpl parent, SingleResultNodeWorker worker, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws RemoteException {
		super(port, csf, ssf);
		this.rootNode = rootNode;
		this.parent = parent;
		this.worker = worker;
	}

	@Override
	final public NodeImpl getParent() {
		return parent;
	}

	@Override
	final public boolean getAllowsChildren() {
		return false;
	}

	@Override
	final public List<? extends NodeImpl> getChildren() {
		return Collections.emptyList();
	}

	@Override
	final public AlertLevel getAlertLevel() {
		AlertLevel alertLevel = worker.getAlertLevel();
		return constrainAlertLevel(alertLevel == null ? AlertLevel.UNKNOWN : alertLevel);
	}

	@Override
	final public String getAlertMessage() {
		Function<Locale,String> alertMessage = worker.getAlertMessage();
		return alertMessage == null ? null : alertMessage.apply(rootNode.locale);
	}

	public final void start() {
		worker.addSingleResultNodeImpl(this);
	}

	public final void stop() {
		worker.removeSingleResultNodeImpl(this);
	}

	@Override
	final public SingleResult getLastResult() {
		return worker.getLastResult();
	}

	/**
	 * Called by the worker when the alert level changes.
	 */
	final void nodeAlertLevelChanged(AlertLevel oldAlertLevel, AlertLevel newAlertLevel, Function<Locale,String> alertMessage) throws RemoteException {
		assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

		rootNode.nodeAlertLevelChanged(
			this,
			constrainAlertLevel(oldAlertLevel),
			constrainAlertLevel(newAlertLevel),
			alertMessage == null ? null : alertMessage.apply(rootNode.locale)
		);
	}

	@Override
	final public void addSingleResultListener(SingleResultListener singleResultListener) {
		synchronized(singleResultListeners) {
			singleResultListeners.add(singleResultListener);
		}
	}

	@Override
	final public void removeSingleResultListener(SingleResultListener singleResultListener) {
		synchronized(singleResultListeners) {
			for(int c=singleResultListeners.size()-1;c>=0;c--) {
				if(singleResultListeners.get(c).equals(singleResultListener)) {
					singleResultListeners.remove(c);
					// Remove only once, in case add and remove come in out of order with quick GUI changes?
					return;
				}
			}
		}
		logger.log(Level.WARNING, null, new AssertionError("Listener not found: " + singleResultListener));
	}

	/**
	 * Notifies all of the listeners.
	 */
	final void singleResultUpdated(SingleResult singleResult) {
		assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

		synchronized(singleResultListeners) {
			Iterator<SingleResultListener> I = singleResultListeners.iterator();
			while(I.hasNext()) {
				SingleResultListener singleResultListener = I.next();
				try {
					singleResultListener.singleResultUpdated(singleResult);
				} catch(RemoteException err) {
					I.remove();
					logger.log(Level.SEVERE, null, err);
				}
			}
		}
	}
}
