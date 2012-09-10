/*
 * Copyright 2008-2012 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.Node;
import java.util.List;
import java.util.Locale;
import java.util.Stack;

/**
 * One in the list of nodes that form the systems tree.
 *
 * @author  AO Industries, Inc.
 */
public abstract class NodeImpl implements Node {

    private static final long serialVersionUID = 1L;

    NodeImpl() {
    }

    @Override
    abstract public NodeImpl getParent();

    @Override
    abstract public List<? extends Node> getChildren();

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
    String getFullPath(Locale locale) {
        String pathSeparator = accessor.getMessage(/*locale,*/ "Node.nodeAlertLevelChanged.alertMessage.pathSeparator");
        final StringBuilder fullPath = new StringBuilder();
        Stack<NodeImpl> path = new Stack<NodeImpl>();
        NodeImpl parent = this;
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
