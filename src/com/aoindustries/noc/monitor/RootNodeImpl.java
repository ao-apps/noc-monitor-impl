/*
 * Copyright 2008 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.aoserv.client.AOServConnector;
import com.aoindustries.noc.common.AlertLevel;
import com.aoindustries.noc.common.AlertLevelChange;
import com.aoindustries.noc.common.Node;
import com.aoindustries.noc.common.NodeSnapshot;
import com.aoindustries.noc.common.RootNode;
import com.aoindustries.noc.common.TreeListener;
import java.io.File;
import java.io.IOException;
import java.rmi.RemoteException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.swing.SwingUtilities;

/**
 * The top-level node has one child for each of the servers.
 *
 * @author  AO Industries, Inc.
 */
public class RootNodeImpl extends NodeImpl implements RootNode {

    private static final boolean DEBUG = true;

    /**
     * One timer is shared by all instances.
     */
    private final static Timer timer = new Timer("noc-monitor");

    /**
     * One thread pool is shared by all instances.
     */
    public final static ExecutorService executorService = Executors.newCachedThreadPool();

    /** 
     * A bridge between a TimerTask and a Runnable.
     */
    public static class RunnableTimerTask extends TimerTask {

        private final ExecutorService executorService;
        private final Runnable runnable;
        volatile private Future<?> future;

        RunnableTimerTask(ExecutorService executorService, Runnable runnable) {
            this.executorService = executorService;
            this.runnable = runnable;
        }

        @Override
        public void run() {
            future = executorService.submit(runnable);
        }

        ExecutorService getExecutorService() {
            return executorService;
        }

        Runnable getRunnable() {
            return runnable;
        }

        /**
         * Gets the Future, which is only available once the timer has been reached.
         */
        Future<?> getFuture() {
            return future;
        }
    }

    /**
     * Schedules a task to be performed in the future.  It will be performed in a background thread via the ExecutorService.
     */
    public static RunnableTimerTask schedule(Runnable runnable, long delay) {
        RunnableTimerTask timerTask = new RunnableTimerTask(executorService, runnable);
        timer.schedule(
            timerTask,
            delay
        );
        return timerTask;
    }

    /**
     * Each root node is stored on a per locale, username, password basis.
     */
    private static class RootNodeCacheKey {

        final private Locale locale;
        final private AOServConnector connector;
        final private int port;
        final private RMIClientSocketFactory csf;
        final private RMIServerSocketFactory ssf;

        private RootNodeCacheKey(Locale locale, AOServConnector connector, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) {
            this.locale = locale;
            this.connector = connector;
            this.port = port;
            this.csf = csf;
            this.ssf = ssf;
        }

        @Override
        public boolean equals(Object O) {
            if(O==null) return false;
            if(!(O instanceof RootNodeCacheKey)) return false;
            RootNodeCacheKey other = (RootNodeCacheKey)O;
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

    private static final Map<RootNodeCacheKey, RootNodeImpl> rootNodeCache = new HashMap<RootNodeCacheKey, RootNodeImpl>();

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
                if(DEBUG) System.err.println("DEBUG: RootNodeImpl: Making new rootNode");
                final RootNodeImpl newRootNode = new RootNodeImpl(locale, connector, port, csf, ssf);
                // Start as a background task
                executorService.submit(
                    new Runnable() {
                        @Override
                        public void run() {
                            if(DEBUG) System.err.println("DEBUG: RootNodeImpl: Running start() in background task");
                            try {
                                newRootNode.start();
                            } catch(Exception err) {
                                newRootNode.conn.getErrorHandler().reportError(err, null);
                            }
                        }
                    }
                );
                rootNodeCache.put(key, newRootNode);
                rootNode = newRootNode;
            } else {
                if(DEBUG) System.err.println("DEBUG: RootNodeImpl: Reusing existing rootNode");
            }
            return rootNode;
        }
    }

    final Locale locale;
    final AOServConnector conn;

    volatile private OtherDevicesNode otherDevicesNode;
    volatile private PhysicalServersNode physicalServersNode;
    volatile private VirtualServersNode virtualServersNode;
    volatile private SignupsNode signupsNode;

    private RootNodeImpl(Locale locale, AOServConnector conn, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws RemoteException {
        super(port, csf, ssf);
        this.locale = locale;
        this.conn = conn;
    }

    @Override
    public Node getParent() {
        return null;
    }

    @Override
    public boolean getAllowsChildren() {
        return true;
    }

    @Override
    public List<? extends Node> getChildren() {
        List<NodeImpl> children = new ArrayList<NodeImpl>(4);

        OtherDevicesNode localOtherDevicesNode = this.otherDevicesNode;
        if(localOtherDevicesNode!=null) children.add(localOtherDevicesNode);

        PhysicalServersNode localPhysicalServersNode = this.physicalServersNode;
        if(localPhysicalServersNode!=null) children.add(localPhysicalServersNode);

        VirtualServersNode localVirtualServersNode = this.virtualServersNode;
        if(localVirtualServersNode!=null) children.add(localVirtualServersNode);

        SignupsNode localSignupsNode = this.signupsNode;
        if(localSignupsNode!=null) children.add(localSignupsNode);

        return Collections.unmodifiableList(children);
    }

    /**
     * The alert level is equal to the highest alert level of its children.
     */
    @Override
    public AlertLevel getAlertLevel() {
        AlertLevel level = AlertLevel.NONE;

        OtherDevicesNode localOtherDevicesNode = this.otherDevicesNode;
        if(localOtherDevicesNode!=null) {
            AlertLevel otherDevicesNodeLevel = localOtherDevicesNode.getAlertLevel();
            if(otherDevicesNodeLevel.compareTo(level)>0) level = otherDevicesNodeLevel;
        }

        PhysicalServersNode localPhysicalServersNode = this.physicalServersNode;
        if(localPhysicalServersNode!=null) {
            AlertLevel physicalServersNodeLevel = localPhysicalServersNode.getAlertLevel();
            if(physicalServersNodeLevel.compareTo(level)>0) level = physicalServersNodeLevel;
        }

        VirtualServersNode localVirtualServersNode = this.virtualServersNode;
        if(localVirtualServersNode!=null) {
            AlertLevel virtualServersNodeLevel = localVirtualServersNode.getAlertLevel();
            if(virtualServersNodeLevel.compareTo(level)>0) level = virtualServersNodeLevel;
        }

        SignupsNode localSignupsNode = this.signupsNode;
        if(localSignupsNode!=null) {
            AlertLevel signupsNodeLevel = localSignupsNode.getAlertLevel();
            if(signupsNodeLevel.compareTo(level)>0) level = signupsNodeLevel;
        }

        return level;
    }

    @Override
    public String getLabel() {
        return ApplicationResourcesAccessor.getMessage(locale, "RootNode.label");
    }

    /**
     * Starts the rootNode.
     */
    synchronized private void start() throws IOException, SQLException {
        assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

        if(otherDevicesNode==null) {
            otherDevicesNode = new OtherDevicesNode(this, port, csf, ssf);
            otherDevicesNode.start();
            otherDevicesNode.rootNode.nodeAdded();
        }

        if(physicalServersNode==null) {
            physicalServersNode = new PhysicalServersNode(this, port, csf, ssf);
            physicalServersNode.start();
            physicalServersNode.rootNode.nodeAdded();
        }

        if(virtualServersNode==null) {
            virtualServersNode = new VirtualServersNode(this, port, csf, ssf);
            virtualServersNode.start();
            virtualServersNode.rootNode.nodeAdded();
        }

        if(signupsNode==null) {
            signupsNode = new SignupsNode(this, port, csf, ssf);
            signupsNode.start();
            signupsNode.rootNode.nodeAdded();
        }
    }

    final private List<TreeListener> treeListeners = new ArrayList<TreeListener>();
    // Synchronized on treeListeners
    final private Map<TreeListener,NodeAddedSignaler> nodeAddedSignalers = new HashMap<TreeListener,NodeAddedSignaler>();
    final private Map<TreeListener,NodeRemovedSignaler> nodeRemovedSignalers = new HashMap<TreeListener,NodeRemovedSignaler>();
    final private Map<TreeListener,NodeAlertLevelChangedSignaler> nodeAlertLevelChangedSignalers = new HashMap<TreeListener,NodeAlertLevelChangedSignaler>();

    @Override
    public void addTreeListener(TreeListener treeListener) {
        synchronized(treeListeners) {
            treeListeners.add(treeListener);
        }
    }

    @Override
    public void removeTreeListener(TreeListener treeListener) {
        int foundCount = 0;
        synchronized(treeListeners) {
            for(int c=treeListeners.size()-1;c>=0;c--) {
                if(treeListeners.get(c)==treeListener) {
                    treeListeners.remove(c);
                    foundCount++;
                }
            }
        }
        if(foundCount!=1) conn.getErrorHandler().reportWarning(new AssertionError("Expected foundCount==1, got foundCount="+foundCount), null);
    }

    private class NodeAddedSignaler implements Runnable {

        final private TreeListener treeListener;

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
        public void run() {
            boolean removed = false;
            try {
                while(true) {
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
                        conn.getErrorHandler().reportWarning(err, null);
                    }
                }
            } catch(RemoteException err) {
                removeTreeListener(treeListener);
                conn.getErrorHandler().reportError(err, null);
            } finally {
                synchronized(treeListeners) {
                    if(!removed) nodeAddedSignalers.remove(treeListener);
                }
            }
        }
    }

    private class NodeRemovedSignaler implements Runnable {

        final private TreeListener treeListener;

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
        public void run() {
            boolean removed = false;
            try {
                while(true) {
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
                        conn.getErrorHandler().reportWarning(err, null);
                    }
                }
            } catch(RemoteException err) {
                removeTreeListener(treeListener);
                conn.getErrorHandler().reportError(err, null);
            } finally {
                synchronized(treeListeners) {
                    if(!removed) nodeRemovedSignalers.remove(treeListener);
                }
            }
        }
    }

    private class NodeAlertLevelChangedSignaler implements Runnable {

        final private TreeListener treeListener;

        private List<AlertLevelChange> queuedChanges;

        NodeAlertLevelChangedSignaler(TreeListener treeListener) {
            this.treeListener = treeListener;
        }
        
        void nodeAlertLevelChanged(AlertLevelChange change) {
            synchronized(treeListeners) {
                if(queuedChanges==null) queuedChanges = new ArrayList<AlertLevelChange>();
                queuedChanges.add(change);
            }
        }

        @Override
        public void run() {
            boolean removed = false;
            try {
                while(true) {
                    List<AlertLevelChange> changes;
                    synchronized(treeListeners) {
                        if(queuedChanges==null) {
                            nodeAlertLevelChangedSignalers.remove(treeListener);
                            removed = true;
                            break;
                        }
                        changes = queuedChanges;
                        queuedChanges = null;
                    }
                    treeListener.nodeAlertLevelChanged(changes);
                    try {
                        Thread.sleep(250);
                    } catch(InterruptedException err) {
                        conn.getErrorHandler().reportWarning(err, null);
                    }
                }
            } catch(RemoteException err) {
                removeTreeListener(treeListener);
                conn.getErrorHandler().reportError(err, null);
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
    void nodeAdded() {
        assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

        synchronized(treeListeners) {
            for(TreeListener treeListener : treeListeners) {
                NodeAddedSignaler nodeAddedSignaler = nodeAddedSignalers.get(treeListener);
                if(nodeAddedSignaler==null) {
                    nodeAddedSignaler = new NodeAddedSignaler(treeListener);
                    nodeAddedSignalers.put(treeListener, nodeAddedSignaler);
                    nodeAddedSignaler.nodeAdded();
                    executorService.execute(nodeAddedSignaler);
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
    void nodeRemoved() {
        synchronized(treeListeners) {
            for(TreeListener treeListener : treeListeners) {
                NodeRemovedSignaler nodeRemovedSignaler = nodeRemovedSignalers.get(treeListener);
                if(nodeRemovedSignaler==null) {
                    nodeRemovedSignaler = new NodeRemovedSignaler(treeListener);
                    nodeRemovedSignalers.put(treeListener, nodeRemovedSignaler);
                    nodeRemovedSignaler.nodeRemoved();
                    executorService.execute(nodeRemovedSignaler);
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
    void nodeAlertLevelChanged(NodeImpl node, AlertLevel oldAlertLevel, AlertLevel newAlertLevel, String alertMessage) throws RemoteException {
        assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

        AlertLevelChange change = new AlertLevelChange(
            node,
            node.getFullPath(locale),
            oldAlertLevel,
            newAlertLevel,
            alertMessage
        );
        synchronized(treeListeners) {
            for(TreeListener treeListener : treeListeners) {
                NodeAlertLevelChangedSignaler nodeAlertLevelChangedSignaler = nodeAlertLevelChangedSignalers.get(treeListener);
                if(nodeAlertLevelChangedSignaler==null) {
                    nodeAlertLevelChangedSignaler = new NodeAlertLevelChangedSignaler(treeListener);
                    nodeAlertLevelChangedSignalers.put(treeListener, nodeAlertLevelChangedSignaler);
                    nodeAlertLevelChangedSignaler.nodeAlertLevelChanged(change);
                    executorService.execute(nodeAlertLevelChangedSignaler);
                } else {
                    nodeAlertLevelChangedSignaler.nodeAlertLevelChanged(change);
                }
            }
        }
    }

    @Override
    public NodeSnapshot getSnapshot() throws RemoteException {
        return new NodeSnapshot(null, this);
    }

    /**
     * Gets the top-level persistence directory.
     */
    File getPersistenceDirectory() throws IOException {
        File dir = new File("persistence");
        if(!dir.exists()) {
            if(!dir.mkdir()) {
                throw new IOException(
                    ApplicationResourcesAccessor.getMessage(
                        locale,
                        "error.mkdirFailed",
                        dir.getCanonicalPath()
                    )
                );
            }
        }
        return dir;
    }
}
