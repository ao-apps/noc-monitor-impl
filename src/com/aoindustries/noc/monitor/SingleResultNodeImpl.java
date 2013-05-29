/*
 * Copyright 2008-2009 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.SingleResult;
import com.aoindustries.noc.monitor.common.SingleResultListener;
import com.aoindustries.noc.monitor.common.SingleResultNode;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The node for single results.
 *
 * @author  AO Industries, Inc.
 */
abstract public class SingleResultNodeImpl extends NodeImpl implements SingleResultNode {

    private static final Logger logger = Logger.getLogger(SingleResultNodeImpl.class.getName());

    private static final long serialVersionUID = 1L;

    protected final RootNodeImpl rootNode;
    protected final NodeImpl parent;
    private final SingleResultNodeWorker worker;

    final private List<SingleResultListener> singleResultListeners = new ArrayList<SingleResultListener>();

    SingleResultNodeImpl(RootNodeImpl rootNode, NodeImpl parent, SingleResultNodeWorker worker) {
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
        return worker.getAlertLevel();
    }

    @Override
    final public String getAlertMessage() {
        return worker.getAlertMessage();
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
    final void nodeAlertLevelChanged(AlertLevel oldAlertLevel, AlertLevel newAlertLevel, SingleResult result) {
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
        if(foundCount!=1) logger.log(Level.WARNING, null, new AssertionError("Expected foundCount==1, got foundCount="+foundCount));
    }

    /**
     * Notifies all of the listeners.
     */
    final void singleResultUpdated(SingleResult singleResult) {
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
