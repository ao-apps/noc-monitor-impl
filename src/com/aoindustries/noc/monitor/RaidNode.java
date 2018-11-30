/*
 * Copyright 2008-2013, 2014, 2016, 2018 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.aoserv.client.distribution.OperatingSystemVersion;
import com.aoindustries.aoserv.client.linux.AOServer;
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
 * The node for RAID devices.
 *
 * @author  AO Industries, Inc.
 */
public class RaidNode extends NodeImpl {

	private static final long serialVersionUID = 1L;

	final ServerNode serverNode;
	private final AOServer aoServer;

	private boolean started;

	volatile private ThreeWareRaidNode _threeWareRaidNode;
	volatile private MdStatNode _mdStatNode;
	volatile private MdMismatchNode _mdMismatchNode;
	volatile private DrbdNode _drbdNode;

	RaidNode(ServerNode serverNode, AOServer aoServer, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws RemoteException {
		super(port, csf, ssf);
		this.serverNode = serverNode;
		this.aoServer = aoServer;
	}

	@Override
	public ServerNode getParent() {
		return serverNode;
	}

	public AOServer getAOServer() {
		return aoServer;
	}

	@Override
	public boolean getAllowsChildren() {
		return true;
	}

	@Override
	public List<NodeImpl> getChildren() {
		return getSnapshot(
			this._threeWareRaidNode,
			this._mdStatNode,
			this._mdMismatchNode,
			this._drbdNode
		);
	}

	/**
	 * The alert level is equal to the highest alert level of its children.
	 */
	@Override
	public AlertLevel getAlertLevel() {
		return constrainAlertLevel(
			AlertLevelUtils.getMaxAlertLevel(
				this._threeWareRaidNode,
				this._mdStatNode,
				this._mdMismatchNode,
				this._drbdNode
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
		return accessor.getMessage(serverNode.serversNode.rootNode.locale, "RaidNode.label");
	}

	void start() throws IOException, SQLException {
		// TODO: Operating system versions can change on-the-fly:
		// We only have 3ware cards in xen outers
		int osv = aoServer.getServer().getOperatingSystemVersion().getPkey();
		synchronized(this) {
			if(started) throw new IllegalStateException();
			started = true;
			if(
				osv == OperatingSystemVersion.CENTOS_5_DOM0_I686
				|| osv == OperatingSystemVersion.CENTOS_5_DOM0_X86_64
			) {
				if(_threeWareRaidNode==null) {
					_threeWareRaidNode = new ThreeWareRaidNode(this, port, csf, ssf);
					_threeWareRaidNode.start();
					serverNode.serversNode.rootNode.nodeAdded();
				}
			}
			// Any machine may have MD RAID (at least until all services run in Xen outers)
			if(_mdStatNode==null) {
				_mdStatNode = new MdStatNode(this, port, csf, ssf);
				_mdStatNode.start();
				serverNode.serversNode.rootNode.nodeAdded();
			}
			if(_mdMismatchNode==null) {
				_mdMismatchNode = new MdMismatchNode(this, port, csf, ssf);
				_mdMismatchNode.start();
				serverNode.serversNode.rootNode.nodeAdded();
			}
			// We only run DRBD in xen outers
			if(
				osv == OperatingSystemVersion.CENTOS_5_DOM0_I686
				|| osv == OperatingSystemVersion.CENTOS_5_DOM0_X86_64
				|| osv == OperatingSystemVersion.CENTOS_7_DOM0_X86_64
			) {
				if(_drbdNode==null) {
					_drbdNode = new DrbdNode(this, port, csf, ssf);
					_drbdNode.start();
					serverNode.serversNode.rootNode.nodeAdded();
				}
			}
		}
	}

	void stop() {
		synchronized(this) {
			started = false;
			if(_threeWareRaidNode!=null) {
				_threeWareRaidNode.stop();
				_threeWareRaidNode = null;
				serverNode.serversNode.rootNode.nodeRemoved();
			}
			if(_mdStatNode!=null) {
				_mdStatNode.stop();
				_mdStatNode = null;
				serverNode.serversNode.rootNode.nodeRemoved();
			}
			if(_mdMismatchNode!=null) {
				_mdMismatchNode.stop();
				_mdMismatchNode = null;
				serverNode.serversNode.rootNode.nodeRemoved();
			}
			if(_drbdNode!=null) {
				_drbdNode.stop();
				_drbdNode = null;
				serverNode.serversNode.rootNode.nodeRemoved();
			}
		}
	}

	File getPersistenceDirectory() throws IOException {
		File dir = new File(serverNode.getPersistenceDirectory(), "raid");
		if(!dir.exists()) {
			if(!dir.mkdir()) {
				throw new IOException(
					accessor.getMessage(
						serverNode.serversNode.rootNode.locale,
						"error.mkdirFailed",
						dir.getCanonicalPath()
					)
				);
			}
		}
		return dir;
	}
}
