/*
 * noc-monitor-impl - Implementation of Network Operations Center Monitoring.
 * Copyright (C) 2008, 2009, 2014, 2016, 2018, 2020, 2021  AO Industries, Inc.
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
package com.aoindustries.noc.monitor;

import com.aoapps.concurrent.Executors;
import com.aoapps.lang.io.IoUtils;
import com.aoindustries.aoserv.client.AOServConnector;
import static com.aoindustries.noc.monitor.Resources.PACKAGE_RESOURCES;
import com.aoindustries.noc.monitor.common.AlertCategory;
import com.aoindustries.noc.monitor.common.AlertChange;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.NodeSnapshot;
import com.aoindustries.noc.monitor.common.RootNode;
import com.aoindustries.noc.monitor.common.TreeListener;
import com.aoindustries.noc.monitor.infrastructure.PhysicalServersNode;
import com.aoindustries.noc.monitor.infrastructure.VirtualServersNode;
import com.aoindustries.noc.monitor.net.OtherDevicesNode;
import com.aoindustries.noc.monitor.net.UnallocatedNode;
import com.aoindustries.noc.monitor.signup.SignupsNode;
import java.io.File;
import java.io.IOException;
import java.rmi.RemoteException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;

/**
 * The top-level node has one child for each of the servers.
 *
 * There is no stop here because root nodes keep running forever in the background to be reconnected to.
 * The overhead of this is reduced by using workers and only creating one rootNode per user.
 *
 * @author  AO Industries, Inc.
 */
public class RootNodeImpl extends NodeImpl implements RootNode {

	private static final Logger logger = Logger.getLogger(RootNodeImpl.class.getName());

	/**
	 * A fast pseudo-random number generator for non-cryptographic purposes.
	 */
	public static final Random fastRandom = new Random(IoUtils.bufferToLong(new SecureRandom().generateSeed(Long.BYTES)));

	private static final long serialVersionUID = 1L;

	/**
	 * One thread pool is shared by all components, and it is never disposed.
	 */
	public static final Executors executors = new Executors();

	/**
	 * Schedules a task to be performed in the future.  It will be performed in a background thread via the ExecutorService.
	 */
	public static Future<?> schedule(Runnable task, long delay) {
		return executors.getUnbounded().submit(task, delay);
	}

	/**
	 * Each root node is stored on a per locale, username, password basis.
	 */
	private static class RootNodeCacheKey {

		private final Locale locale;
		private final AOServConnector connector;
		private final int port;
		private final RMIClientSocketFactory csf;
		private final RMIServerSocketFactory ssf;

		private RootNodeCacheKey(Locale locale, AOServConnector connector, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) {
			this.locale = locale;
			this.connector = connector;
			this.port = port;
			this.csf = csf;
			this.ssf = ssf;
		}

		@Override
		public boolean equals(Object obj) {
			if(!(obj instanceof RootNodeCacheKey)) return false;
			RootNodeCacheKey other = (RootNodeCacheKey)obj;
			return
				locale.equals(other.locale)
				&& connector.equals(other.connector)
				&& port==other.port
				&& csf.equals(other.csf)
				&& ssf.equals(other.ssf)
			;
		}

		@Override
		public int hashCode() {
			return
				locale.hashCode()
				^ (connector.hashCode()*7)
				^ (port*11)
				^ (csf.hashCode()*13)
				^ (ssf.hashCode()*17)
			;
		}
	}

	private static final Map<RootNodeCacheKey, RootNodeImpl> rootNodeCache = new HashMap<>();

	@SuppressWarnings({"UseSpecificCatch", "TooBroadCatch"})
	static RootNodeImpl getRootNode(
		Locale locale,
		AOServConnector connector,
		int port,
		RMIClientSocketFactory csf,
		RMIServerSocketFactory ssf
	) throws RemoteException {
		RootNodeCacheKey key = new RootNodeCacheKey(locale, connector, port, csf, ssf);
		synchronized(rootNodeCache) {
			RootNodeImpl rootNode = rootNodeCache.get(key);
			if(rootNode==null) {
				logger.fine("Making new rootNode");
				final RootNodeImpl newRootNode = new RootNodeImpl(locale, connector, port, csf, ssf);
				// Start as a background task
				executors.getUnbounded().submit(() -> {
					logger.finer("Running start() in background task");
					try {
						newRootNode.start();
					} catch(ThreadDeath td) {
						throw td;
					} catch(Throwable t) {
						logger.log(Level.SEVERE, null, t);
					}
				});
				rootNodeCache.put(key, newRootNode);
				rootNode = newRootNode;
			} else {
				logger.finer("Reusing existing rootNode");
			}
			return rootNode;
		}
	}

	public final Locale locale;
	public final AOServConnector conn;

	private volatile OtherDevicesNode otherDevicesNode;
	private volatile PhysicalServersNode physicalServersNode;
	private volatile VirtualServersNode virtualServersNode;
	private volatile UnallocatedNode unallocatedNode;
	private volatile SignupsNode signupsNode;

	private RootNodeImpl(Locale locale, AOServConnector conn, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws RemoteException {
		super(port, csf, ssf);
		this.locale = locale;
		this.conn = conn;
	}

	@Override
	public NodeImpl getParent() {
		return null;
	}

	@Override
	public boolean getAllowsChildren() {
		return true;
	}

	@Override
	public List<NodeImpl> getChildren() {
		return getSnapshot(
			this.otherDevicesNode,
			this.physicalServersNode,
			this.virtualServersNode,
			this.unallocatedNode,
			this.signupsNode
		);
	}

	/**
	 * The alert level is equal to the highest alert level of its children.
	 */
	@Override
	public AlertLevel getAlertLevel() {
		return constrainAlertLevel(
			AlertLevelUtils.getMaxAlertLevel(
				this.otherDevicesNode,
				this.physicalServersNode,
				this.virtualServersNode,
				this.unallocatedNode,
				this.signupsNode
			)
		);
	}

	/**
	 * No alert messages.
	 */
	@Override
	public String getAlertMessage() {
		return null;
	}

	@Override
	public AlertCategory getAlertCategory() {
		return AlertCategory.UNCATEGORIZED;
	}

	@Override
	public String getLabel() {
		return PACKAGE_RESOURCES.getMessage(locale, "RootNode.label");
	}

	/**
	 * Starts the rootNode.
	 */
	private synchronized void start() throws IOException, SQLException {
		assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

		if(otherDevicesNode==null) {
			logger.fine("new OtherDevicesNode");
			otherDevicesNode = new OtherDevicesNode(this, port, csf, ssf);
			otherDevicesNode.start();
			nodeAdded();
		}

		if(physicalServersNode==null) {
			logger.fine("new PhysicalServersNode");
			physicalServersNode = new PhysicalServersNode(this, port, csf, ssf);
			physicalServersNode.start();
			nodeAdded();
		}

		if(virtualServersNode==null) {
			logger.fine("new VirtualServersNode");
			virtualServersNode = new VirtualServersNode(this, port, csf, ssf);
			virtualServersNode.start();
			nodeAdded();
		}

		if(unallocatedNode==null) {
			logger.fine("new UnallocatedNode");
			unallocatedNode = new UnallocatedNode(this, port, csf, ssf);
			unallocatedNode.start();
			nodeAdded();
		}

		if(signupsNode==null) {
			logger.fine("new SignupsNode");
			signupsNode = new SignupsNode(this, port, csf, ssf);
			signupsNode.start();
			nodeAdded();
		}
	}

	private final List<TreeListener> treeListeners = new ArrayList<>();
	// Synchronized on treeListeners
	private final Map<TreeListener, NodeAddedSignaler> nodeAddedSignalers = new HashMap<>();
	private final Map<TreeListener, NodeRemovedSignaler> nodeRemovedSignalers = new HashMap<>();
	private final Map<TreeListener, NodeAlertLevelChangedSignaler> nodeAlertLevelChangedSignalers = new HashMap<>();

	@Override
	public void addTreeListener(TreeListener treeListener) {
		synchronized(treeListeners) {
			treeListeners.add(treeListener);
		}
	}

	@Override
	public void removeTreeListener(TreeListener treeListener) {
		synchronized(treeListeners) {
			for(int c=treeListeners.size()-1;c>=0;c--) {
				if(treeListeners.get(c)==treeListener) {
					treeListeners.remove(c);
					// Remove only once, in case add and remove come in out of order with quick GUI changes
					return;
				}
			}
		}
		logger.log(Level.WARNING, null, new AssertionError("Listener not found: " + treeListener));
	}

	private class NodeAddedSignaler implements Runnable {

		private final TreeListener treeListener;

		private long lastCounterSent = 0;
		private long currentCounter = 0;

		NodeAddedSignaler(TreeListener treeListener) {
			this.treeListener = treeListener;
		}

		void nodeAdded() {
			synchronized(treeListeners) {
				currentCounter++;
			}
		}

		@Override
		@SuppressWarnings("SleepWhileInLoop")
		public void run() {
			boolean removed = false;
			try {
				while(!Thread.currentThread().isInterrupted()) {
					long sendingCounter;
					synchronized(treeListeners) {
						if(lastCounterSent<currentCounter) {
							nodeAddedSignalers.remove(treeListener);
							removed = true;
							break;
						}
						sendingCounter = currentCounter;
					}
					treeListener.nodeAdded();
					synchronized(treeListeners) {
						lastCounterSent = sendingCounter;
					}
					try {
						Thread.sleep(250);
					} catch(InterruptedException err) {
						logger.log(Level.WARNING, null, err);
						// Restore the interrupted status
						Thread.currentThread().interrupt();
					}
				}
			} catch(RemoteException err) {
				removeTreeListener(treeListener);
				logger.log(Level.SEVERE, null, err);
			} finally {
				synchronized(treeListeners) {
					if(!removed) nodeAddedSignalers.remove(treeListener);
				}
			}
		}
	}

	private class NodeRemovedSignaler implements Runnable {

		private final TreeListener treeListener;

		private long lastCounterSent = 0;
		private long currentCounter = 0;

		NodeRemovedSignaler(TreeListener treeListener) {
			this.treeListener = treeListener;
		}

		void nodeRemoved() {
			synchronized(treeListeners) {
				currentCounter++;
			}
		}

		@Override
		@SuppressWarnings("SleepWhileInLoop")
		public void run() {
			boolean removed = false;
			try {
				while(!Thread.currentThread().isInterrupted()) {
					long sendingCounter;
					synchronized(treeListeners) {
						if(lastCounterSent<currentCounter) {
							nodeRemovedSignalers.remove(treeListener);
							removed = true;
							break;
						}
						sendingCounter = currentCounter;
					}
					treeListener.nodeRemoved();
					synchronized(treeListeners) {
						lastCounterSent = sendingCounter;
					}
					try {
						Thread.sleep(250);
					} catch(InterruptedException err) {
						logger.log(Level.WARNING, null, err);
						// Restore the interrupted status
						Thread.currentThread().interrupt();
					}
				}
			} catch(RemoteException err) {
				removeTreeListener(treeListener);
				logger.log(Level.SEVERE, null, err);
			} finally {
				synchronized(treeListeners) {
					if(!removed) nodeRemovedSignalers.remove(treeListener);
				}
			}
		}
	}

	private class NodeAlertLevelChangedSignaler implements Runnable {

		private final TreeListener treeListener;

		private List<AlertChange> queuedChanges;

		NodeAlertLevelChangedSignaler(TreeListener treeListener) {
			this.treeListener = treeListener;
		}

		void nodeAlertLevelChanged(AlertChange change) {
			synchronized(treeListeners) {
				if(queuedChanges==null) queuedChanges = new ArrayList<>();
				queuedChanges.add(change);
			}
		}

		@Override
		@SuppressWarnings("SleepWhileInLoop")
		public void run() {
			boolean removed = false;
			try {
				while(!Thread.currentThread().isInterrupted()) {
					List<AlertChange> changes;
					synchronized(treeListeners) {
						if(queuedChanges==null) {
							nodeAlertLevelChangedSignalers.remove(treeListener);
							removed = true;
							break;
						}
						changes = queuedChanges;
						queuedChanges = null;
					}
					treeListener.nodeAlertChanged(changes);
					try {
						Thread.sleep(250);
					} catch(InterruptedException err) {
						logger.log(Level.WARNING, null, err);
						// Restore the interrupted status
						Thread.currentThread().interrupt();
					}
				}
			} catch(RemoteException err) {
				removeTreeListener(treeListener);
				logger.log(Level.SEVERE, null, err);
			} finally {
				synchronized(treeListeners) {
					if(!removed) nodeAlertLevelChangedSignalers.remove(treeListener);
				}
			}
		}
	}

	/**
	 * Notifies all of the listeners.  Batches the calls into a per-listener background task.  Each of the background tasks may
	 * send one event representing any number of changes.  Each background task will wait 250 ms between each send.
	 */
	public void nodeAdded() {
		assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

		synchronized(treeListeners) {
			for(TreeListener treeListener : treeListeners) {
				NodeAddedSignaler nodeAddedSignaler = nodeAddedSignalers.get(treeListener);
				if(nodeAddedSignaler==null) {
					nodeAddedSignaler = new NodeAddedSignaler(treeListener);
					nodeAddedSignalers.put(treeListener, nodeAddedSignaler);
					nodeAddedSignaler.nodeAdded();
					executors.getUnbounded().submit(nodeAddedSignaler);
				} else {
					nodeAddedSignaler.nodeAdded();
				}
			}
		}
	}

	/**
	 * Notifies all of the listeners.  Batches the calls into a per-listener background task.  Each of the background tasks may
	 * send one event representing any number of changes.  Each background task will wait 250 ms between each send.
	 */
	public void nodeRemoved() {
		synchronized(treeListeners) {
			for(TreeListener treeListener : treeListeners) {
				NodeRemovedSignaler nodeRemovedSignaler = nodeRemovedSignalers.get(treeListener);
				if(nodeRemovedSignaler==null) {
					nodeRemovedSignaler = new NodeRemovedSignaler(treeListener);
					nodeRemovedSignalers.put(treeListener, nodeRemovedSignaler);
					nodeRemovedSignaler.nodeRemoved();
					executors.getUnbounded().submit(nodeRemovedSignaler);
				} else {
					nodeRemovedSignaler.nodeRemoved();
				}
			}
		}
	}

	/**
	 * Notifies all of the listeners.  Batches the calls into a per-listener background task.  Each of the background tasks may
	 * send one event representing any number of changes.  Each background task will wait 250 ms between each send.
	 */
	void nodeAlertLevelChanged(NodeImpl node, AlertLevel oldAlertLevel, AlertLevel newAlertLevel, String alertMessage, AlertCategory oldAlertCategory, AlertCategory newAlertCategory) throws RemoteException {
		assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

		if(oldAlertLevel != newAlertLevel) {
			AlertChange change = new AlertChange(
				node,
				node.getFullPath(locale),
				oldAlertLevel,
				newAlertLevel,
				alertMessage,
				oldAlertCategory,
				newAlertCategory
			);
			synchronized(treeListeners) {
				for(TreeListener treeListener : treeListeners) {
					NodeAlertLevelChangedSignaler nodeAlertLevelChangedSignaler = nodeAlertLevelChangedSignalers.get(treeListener);
					if(nodeAlertLevelChangedSignaler==null) {
						nodeAlertLevelChangedSignaler = new NodeAlertLevelChangedSignaler(treeListener);
						nodeAlertLevelChangedSignalers.put(treeListener, nodeAlertLevelChangedSignaler);
						nodeAlertLevelChangedSignaler.nodeAlertLevelChanged(change);
						executors.getUnbounded().submit(nodeAlertLevelChangedSignaler);
					} else {
						nodeAlertLevelChangedSignaler.nodeAlertLevelChanged(change);
					}
				}
			}
		}
	}

	/**
	 * Uses the {@link AlertCategory} of the node for both {@code oldAlertCategory} and {@code newAlertCategory}.
	 *
	 * @see  #nodeAlertLevelChanged(com.aoindustries.noc.monitor.NodeImpl, com.aoindustries.noc.monitor.common.AlertLevel, com.aoindustries.noc.monitor.common.AlertLevel, java.lang.String)
	 */
	public void nodeAlertLevelChanged(NodeImpl node, AlertLevel oldAlertLevel, AlertLevel newAlertLevel, String alertMessage) throws RemoteException {
		assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";
		AlertCategory alertCategory = node.getAlertCategory();
		nodeAlertLevelChanged(node, oldAlertLevel, newAlertLevel, alertMessage, alertCategory, alertCategory);
	}

	@Override
	public NodeSnapshot getSnapshot() throws RemoteException {
		return new NodeSnapshot(null, this);
	}

	/**
	 * Gets the top-level persistence directory.
	 */
	public File getPersistenceDirectory() throws IOException {
		File dir = new File("persistence");
		if(!dir.exists()) {
			if(!dir.mkdir()) {
				throw new IOException(
					PACKAGE_RESOURCES.getMessage(
						locale,
						"error.mkdirFailed",
						dir.getCanonicalPath()
					)
				);
			}
		}
		return dir;
	}

	private static int lastStartupDelay5;
	private static final Object lastStartupDelay5Lock = new Object();
	static int getNextStartupDelayFiveMinutes() {
		synchronized(lastStartupDelay5Lock) {
			lastStartupDelay5 = (lastStartupDelay5 + 5037) % (5 * 60 * 1000);
			return lastStartupDelay5;
		}
	}

	private static int lastStartupDelay15;
	private static final Object lastStartupDelay15Lock = new Object();
	public static int getNextStartupDelayFifteenMinutes() {
		synchronized(lastStartupDelay15Lock) {
			lastStartupDelay15= (lastStartupDelay15 + 15037) % (15 * 60 * 1000);
			return lastStartupDelay15;
		}
	}
}
