/*
 * Copyright 2008 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.noc.common.AlertLevel;
import com.aoindustries.noc.common.Node;
import com.aoindustries.noc.common.TableMultiResult;
import com.aoindustries.noc.common.TableMultiResultListener;
import com.aoindustries.noc.common.TableMultiResultNode;
import java.rmi.RemoteException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import javax.swing.SwingUtilities;

/**
 * The node for table results.
 *
 * @author  AO Industries, Inc.
 */
abstract public class TableMultiResultNodeImpl extends NodeImpl implements TableMultiResultNode {

    final RootNodeImpl rootNode;
    final Node parent;
    final TableMultiResultNodeWorker worker;

    final private List<TableMultiResultListener> tableMultiResultListeners = new ArrayList<TableMultiResultListener>();

    TableMultiResultNodeImpl(RootNodeImpl rootNode, Node parent, TableMultiResultNodeWorker worker, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws RemoteException {
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
        worker.addTableMultiResultNodeImpl(this);
    }

    final void stop() {
        worker.removeTableMultiResultNodeImpl(this);
    }

    @Override
    final public List<? extends TableMultiResult> getResults() {
        return worker.getResults();
    }

    /**
     * Called by the worker when the alert level changes.
     */
    final void nodeAlertLevelChanged(AlertLevel oldAlertLevel, AlertLevel newAlertLevel, String newAlertMessage) throws RemoteException {
        assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

        rootNode.nodeAlertLevelChanged(
            this,
            oldAlertLevel,
            newAlertLevel,
            newAlertMessage
        );
    }

    @Override
    final public void addTableMultiResultListener(TableMultiResultListener tableMultiResultListener) {
        synchronized(tableMultiResultListeners) {
            tableMultiResultListeners.add(tableMultiResultListener);
        }
    }

    // TODO: Remove only once, in case add and remove come in out of order with quick GUI changes?
    @Override
    final public void removeTableMultiResultListener(TableMultiResultListener tableMultiResultListener) {
        int foundCount = 0;
        synchronized(tableMultiResultListeners) {
            for(int c=tableMultiResultListeners.size()-1;c>=0;c--) {
                if(tableMultiResultListeners.get(c).equals(tableMultiResultListener)) {
                    tableMultiResultListeners.remove(c);
                    foundCount++;
                }
            }
        }
        if(foundCount!=1) rootNode.conn.getErrorHandler().reportWarning(new AssertionError("Expected foundCount==1, got foundCount="+foundCount), null);
    }

    /**
     * Notifies all of the listeners.
     */
    final void tableMultiResultAdded(TableMultiResult tableMultiResult) {
        assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

        synchronized(tableMultiResultListeners) {
            Iterator<TableMultiResultListener> I = tableMultiResultListeners.iterator();
            while(I.hasNext()) {
                TableMultiResultListener tableMultiResultListener = I.next();
                try {
                    tableMultiResultListener.tableMultiResultAdded(tableMultiResult);
                } catch(RemoteException err) {
                    I.remove();
                    rootNode.conn.getErrorHandler().reportError(err, null);
                }
            }
        }
    }

    /**
     * Notifies all of the listeners.
     */
    final void tableMultiResultRemoved(TableMultiResult tableMultiResult) {
        assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

        synchronized(tableMultiResultListeners) {
            Iterator<TableMultiResultListener> I = tableMultiResultListeners.iterator();
            while(I.hasNext()) {
                TableMultiResultListener tableMultiResultListener = I.next();
                try {
                    tableMultiResultListener.tableMultiResultRemoved(tableMultiResult);
                } catch(RemoteException err) {
                    I.remove();
                    rootNode.conn.getErrorHandler().reportError(err, null);
                }
            }
        }
    }
}
