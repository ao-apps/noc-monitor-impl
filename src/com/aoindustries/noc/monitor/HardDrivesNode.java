/*
 * Copyright 2008-2012 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.aoserv.client.AOServer;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.Node;
import java.io.File;
import java.io.IOException;
import java.rmi.RemoteException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The node for hard drives.
 *
 * @author  AO Industries, Inc.
 */
public class HardDrivesNode extends NodeImpl {

    private static final long serialVersionUID = 1L;

    final ServerNode serverNode;
    private final AOServer _aoServer;

    volatile private HardDrivesTemperatureNode _hardDriveTemperatureNode;

    HardDrivesNode(ServerNode serverNode, AOServer aoServer, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws RemoteException {
        super(port, csf, ssf);
        this.serverNode = serverNode;
        this._aoServer = aoServer;
    }

    @Override
    public Node getParent() {
        return serverNode;
    }

    public AOServer getAOServer() {
        return _aoServer;
    }

    @Override
    public boolean getAllowsChildren() {
        return true;
    }

    /**
     * For thread safety and encapsulation, returns an unmodifiable copy of the list.
     */
    @Override
    public List<? extends Node> getChildren() {
        List<NodeImpl> children = new ArrayList<NodeImpl>(1);
        HardDrivesTemperatureNode hardDriveTemperatureNode = this._hardDriveTemperatureNode;
        if(hardDriveTemperatureNode!=null) children.add(hardDriveTemperatureNode);
        return Collections.unmodifiableList(children);
    }

    /**
     * The alert level is equal to the highest alert level of its children.
     */
    @Override
    public AlertLevel getAlertLevel() {
        AlertLevel level = AlertLevel.NONE;

        HardDrivesTemperatureNode hardDriveTemperatureNode = this._hardDriveTemperatureNode;
        if(hardDriveTemperatureNode!=null) {
            AlertLevel hardDriveTemperatureNodeLevel = hardDriveTemperatureNode.getAlertLevel();
            if(hardDriveTemperatureNodeLevel.compareTo(level)>0) level = hardDriveTemperatureNodeLevel;
        }
        return level;
    }

    /**
     * No alert messages.
     */
    @Override
    public String getAlertMessage() {
        return null;
    }

    @Override
    public String getId() {
        return "hard_drives";
    }

    @Override
    public String getLabel() {
        return accessor.getMessage(/*serverNode.serversNode.rootNode.locale,*/ "HardDrivesNode.label");
    }
    
    synchronized void start() throws IOException {
        if(_hardDriveTemperatureNode==null) {
            _hardDriveTemperatureNode = new HardDrivesTemperatureNode(this, port, csf, ssf);
            _hardDriveTemperatureNode.start();
            serverNode.serversNode.rootNode.nodeAdded();
        }
    }

    synchronized void stop() {
        if(_hardDriveTemperatureNode!=null) {
            _hardDriveTemperatureNode.stop();
            _hardDriveTemperatureNode = null;
            serverNode.serversNode.rootNode.nodeRemoved();
        }
    }

    File getPersistenceDirectory() throws IOException {
        File dir = new File(serverNode.getPersistenceDirectory(), "hard_drives");
        if(!dir.exists()) {
            if(!dir.mkdir()) {
                throw new IOException(
                    accessor.getMessage(
                        //serverNode.serversNode.rootNode.locale,
                        "error.mkdirFailed",
                        dir.getCanonicalPath()
                    )
                );
            }
        }
        return dir;
    }
}
