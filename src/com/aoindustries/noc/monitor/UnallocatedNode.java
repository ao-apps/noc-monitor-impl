/*
 * Copyright 2014 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.noc.monitor.common.AlertLevel;
import java.io.File;
import java.io.IOException;
import java.rmi.RemoteException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.sql.SQLException;
import java.util.List;

/**
 * The node for unallocated resources that can still be monitored.
 *
 * @author  AO Industries, Inc.
 */
public class UnallocatedNode extends NodeImpl {

	private static final long serialVersionUID = 1L;

	final RootNodeImpl rootNode;

	volatile private IPAddressesNode _ipAddressesNode;

	UnallocatedNode(RootNodeImpl rootNode, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws RemoteException {
		super(port, csf, ssf);
		this.rootNode = rootNode;
	}

	@Override
	public RootNodeImpl getParent() {
		return rootNode;
	}

	@Override
	public boolean getAllowsChildren() {
		return true;
	}

	@Override
	public List<IPAddressesNode> getChildren() {
		return getSnapshot(
			this._ipAddressesNode
		);
	}

	/**
	 * The alert level is equal to the highest alert level of its children.
	 */
	@Override
	public AlertLevel getAlertLevel() {
		return constrainAlertLevel(
			AlertLevelUtils.getMaxAlertLevel(
				this._ipAddressesNode
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
		return accessor.getMessage(/*rootNode.locale,*/ "UnallocatedNode.label");
	}

	synchronized void start() throws IOException, SQLException {
		if(_ipAddressesNode==null) {
			_ipAddressesNode = new IPAddressesNode(this, port, csf, ssf);
			_ipAddressesNode.start();
			rootNode.nodeAdded();
		}
	}

	synchronized void stop() {
		if(_ipAddressesNode!=null) {
			_ipAddressesNode.stop();
			_ipAddressesNode = null;
			rootNode.nodeRemoved();
		}
	}

	File getPersistenceDirectory() throws IOException {
		File dir = new File(rootNode.getPersistenceDirectory(), "unallocated");
		if(!dir.exists()) {
			if(!dir.mkdir()) {
				throw new IOException(
					accessor.getMessage(
						//rootNode.locale,
						"error.mkdirFailed",
						dir.getCanonicalPath()
					)
				);
			}
		}
		return dir;
	}
}
