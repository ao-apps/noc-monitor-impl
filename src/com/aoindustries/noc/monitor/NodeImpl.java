/*
 * Copyright 2008-2009 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.Node;
import java.rmi.RemoteException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.UnicastRemoteObject;
import java.util.List;
import java.util.Locale;
import java.util.Stack;

/**
 * One in the list of nodes that form the systems tree.
 *
 * @author  AO Industries, Inc.
 */
public abstract class NodeImpl extends UnicastRemoteObject implements Node {

    final protected int port;
    final protected RMIClientSocketFactory csf;
    final protected RMIServerSocketFactory ssf;

    NodeImpl(int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws RemoteException {
        super(port, csf, ssf);
        this.port = port;
        this.csf = csf;
        this.ssf = ssf;
    }

    @Override
    abstract public NodeImpl getParent();

    @Override
    abstract public List<? extends NodeImpl> getChildren();

    @Override
    abstract public AlertLevel getAlertLevel();
    
    @Override
    abstract public String getAlertMessage();
    
    @Override
    abstract public String getLabel();
    
    @Override
    abstract public boolean getAllowsChildren();
    
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
        String pathSeparator = accessor.getMessage(/*locale,*/ "Node.nodeAlertLevelChanged.alertMessage.pathSeparator");
        final StringBuilder fullPath = new StringBuilder();
        Stack<Node> path = new Stack<Node>();
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
