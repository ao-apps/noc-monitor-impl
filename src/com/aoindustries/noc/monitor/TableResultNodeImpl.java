/*
 * Copyright 2008-2009 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.noc.common.AlertLevel;
import com.aoindustries.noc.common.Node;
import com.aoindustries.noc.common.TableResult;
import com.aoindustries.noc.common.TableResultListener;
import com.aoindustries.noc.common.TableResultNode;
import java.io.IOException;
import java.rmi.RemoteException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;

/**
 * The node for table results.
 *
 * @author  AO Industries, Inc.
 */
abstract public class TableResultNodeImpl extends NodeImpl implements TableResultNode {

    private static final Logger logger = Logger.getLogger(TableResultNodeImpl.class.getName());

    final RootNodeImpl rootNode;
    final Node parent;
    final TableResultNodeWorker worker;

    final private List<TableResultListener> tableResultListeners = new ArrayList<TableResultListener>();

    TableResultNodeImpl(RootNodeImpl rootNode, Node parent, TableResultNodeWorker worker, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws RemoteException {
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
    public boolean getAllowsChildren() {
        return false;
    }

    @Override
    public List<? extends Node> getChildren() {
        return Collections.emptyList();
    }

    @Override
    public AlertLevel getAlertLevel() {
        return worker.getAlertLevel();
    }
    
    @Override
    final public String getAlertMessage() {
        return worker.getAlertMessage();
    }

    void start() throws IOException {
        worker.addTableResultNodeImpl(this);
    }

    void stop() {
        worker.removeTableResultNodeImpl(this);
    }

    @Override
    final public TableResult getLastResult() {
        return worker.getLastResult();
    }

    /**
     * Called by the worker when the alert level changes.
     */
    final void nodeAlertLevelChanged(AlertLevel oldAlertLevel, AlertLevel newAlertLevel, TableResult result) throws RemoteException {
        assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

        rootNode.nodeAlertLevelChanged(
            this,
            oldAlertLevel,
            newAlertLevel,
            worker.getAlertLevelAndMessage(rootNode.locale, result).getAlertMessage()
        );
    }

    @Override
    final public void addTableResultListener(TableResultListener tableResultListener) {
        synchronized(tableResultListeners) {
            tableResultListeners.add(tableResultListener);
        }
    }

    // TODO: Remove only once, in case add and remove come in out of order with quick GUI changes?
    @Override
    final public void removeTableResultListener(TableResultListener tableResultListener) {
        int foundCount = 0;
        synchronized(tableResultListeners) {
            for(int c=tableResultListeners.size()-1;c>=0;c--) {
                if(tableResultListeners.get(c).equals(tableResultListener)) {
                    tableResultListeners.remove(c);
                    foundCount++;
                }
            }
        }
        if(foundCount!=1) {
            logger.log(Level.WARNING, null, new AssertionError("Expected foundCount==1, got foundCount="+foundCount));
        }
    }

    /**
     * Notifies all of the listeners.
     */
    final void tableResultUpdated(TableResult tableResult) {
        assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

        synchronized(tableResultListeners) {
            Iterator<TableResultListener> I = tableResultListeners.iterator();
            while(I.hasNext()) {
                TableResultListener tableResultListener = I.next();
                try {
                    tableResultListener.tableResultUpdated(tableResult);
                } catch(RemoteException err) {
                    I.remove();
                    logger.log(Level.SEVERE, null, err);
                }
            }
        }
    }
}
