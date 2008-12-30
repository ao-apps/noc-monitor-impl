/*
 * Copyright 2008 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.noc.common.AlertLevel;
import com.aoindustries.noc.common.Node;
import com.aoindustries.noc.common.SingleResult;
import com.aoindustries.noc.common.SingleResultListener;
import com.aoindustries.noc.common.SingleResultNode;
import java.rmi.RemoteException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import javax.swing.SwingUtilities;

/**
 * The node for single results.
 *
 * @author  AO Industries, Inc.
 */
abstract public class SingleResultNodeImpl extends NodeImpl implements SingleResultNode {

    protected final RootNodeImpl rootNode;
    protected final Node parent;
    private final SingleResultNodeWorker worker;

    final private List<SingleResultListener> singleResultListeners = new ArrayList<SingleResultListener>();

    SingleResultNodeImpl(RootNodeImpl rootNode, Node parent, SingleResultNodeWorker worker, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws RemoteException {
        super(port, csf, ssf);
        this.rootNode = rootNode;
        this.parent = parent;
        this.worker = worker;
    }

    @Override
    final public Node getParent() {
        return parent;
    }

    @Override
    final public boolean getAllowsChildren() {
        return false;
    }

    @Override
    final public List<? extends Node> getChildren() {
        return Collections.emptyList();
    }

    @Override
    final public AlertLevel getAlertLevel() {
        return worker.getAlertLevel();
    }

    final void start() {
        worker.addSingleResultNodeImpl(this);
    }

    final void stop() {
        worker.removeSingleResultNodeImpl(this);
    }

    @Override
    final public SingleResult getLastResult() {
        return worker.getLastResult();
    }

    /**
     * Called by the worker when the alert level changes.
     */
    final void nodeAlertLevelChanged(AlertLevel oldAlertLevel, AlertLevel newAlertLevel, SingleResult result) throws RemoteException {
        assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

        rootNode.nodeAlertLevelChanged(
            this,
            oldAlertLevel,
            newAlertLevel,
            worker.getAlertLevelAndMessage(rootNode.locale, result).getAlertMessage()
        );
    }

    @Override
    final public void addSingleResultListener(SingleResultListener singleResultListener) {
        synchronized(singleResultListeners) {
            singleResultListeners.add(singleResultListener);
        }
    }

    // TODO: Remove only once, in case add and remove come in out of order with quick GUI changes?
    @Override
    final public void removeSingleResultListener(SingleResultListener singleResultListener) {
        int foundCount = 0;
        synchronized(singleResultListeners) {
            for(int c=singleResultListeners.size()-1;c>=0;c--) {
                if(singleResultListeners.get(c).equals(singleResultListener)) {
                    singleResultListeners.remove(c);
                    foundCount++;
                }
            }
        }
        if(foundCount!=1) rootNode.conn.getErrorHandler().reportWarning(new AssertionError("Expected foundCount==1, got foundCount="+foundCount), null);
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
                    rootNode.conn.getErrorHandler().reportError(err, null);
                }
            }
        }
    }
}
