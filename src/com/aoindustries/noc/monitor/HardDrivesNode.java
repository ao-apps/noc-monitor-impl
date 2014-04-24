/*
 * Copyright 2008-2009, 2014 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.aoserv.client.AOServer;
import com.aoindustries.noc.monitor.common.AlertLevel;
import java.io.File;
import java.io.IOException;
import java.rmi.RemoteException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
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
	public ServerNode getParent() {
		return serverNode;
	}

	public AOServer getAOServer() {
		return _aoServer;
	}

	@Override
	public boolean getAllowsChildren() {
		return true;
	}

	@Override
	public List<HardDrivesTemperatureNode> getChildren() {
		return getSnapshot(this._hardDriveTemperatureNode);
	}

	/**
	 * The alert level is equal to the highest alert level of its children.
	 */
	@Override
	public AlertLevel getAlertLevel() {
		return constrainAlertLevel(
			AlertLevelUtils.getMaxAlertLevel(
				this._hardDriveTemperatureNode
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
