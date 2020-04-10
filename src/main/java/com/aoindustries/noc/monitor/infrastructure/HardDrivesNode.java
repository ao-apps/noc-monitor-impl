/*
 * noc-monitor-impl - Implementation of Network Operations Center Monitoring.
 * Copyright (C) 2008, 2009, 2014, 2016, 2018, 2019, 2020  AO Industries, Inc.
 *     support@aoindustries.com
 *     7262 Bull Pen Cir
 *     Mobile, AL 36695
 *
 * This file is part of noc-monitor-impl.
 *
 * noc-monitor-impl is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * noc-monitor-impl is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with noc-monitor-impl.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.aoindustries.noc.monitor.infrastructure;

import com.aoindustries.aoserv.client.linux.Server;
import com.aoindustries.noc.monitor.AlertLevelUtils;
import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.noc.monitor.NodeImpl;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.net.HostNode;
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

	final HostNode hostNode;
	private final Server _linuxServer;

	private boolean started;

	volatile private HardDrivesTemperatureNode _hardDriveTemperatureNode;

	public HardDrivesNode(HostNode hostNode, Server linuxServer, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws RemoteException {
		super(port, csf, ssf);
		this.hostNode = hostNode;
		this._linuxServer = linuxServer;
	}

	@Override
	public HostNode getParent() {
		return hostNode;
	}

	public Server getLinuxServer() {
		return _linuxServer;
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
		return accessor.getMessage(hostNode.hostsNode.rootNode.locale, "HardDrivesNode.label");
	}

	public void start() throws IOException {
		synchronized(this) {
			if(started) throw new IllegalStateException();
			started = true;
			if(_hardDriveTemperatureNode==null) {
				_hardDriveTemperatureNode = new HardDrivesTemperatureNode(this, port, csf, ssf);
				_hardDriveTemperatureNode.start();
				hostNode.hostsNode.rootNode.nodeAdded();
			}
		}
	}

	public void stop() {
		synchronized(this) {
			started = false;
			if(_hardDriveTemperatureNode!=null) {
				_hardDriveTemperatureNode.stop();
				_hardDriveTemperatureNode = null;
				hostNode.hostsNode.rootNode.nodeRemoved();
			}
		}
	}

	File getPersistenceDirectory() throws IOException {
		File dir = new File(hostNode.getPersistenceDirectory(), "hard_drives");
		if(!dir.exists()) {
			if(!dir.mkdir()) {
				throw new IOException(
					accessor.getMessage(hostNode.hostsNode.rootNode.locale,
						"error.mkdirFailed",
						dir.getCanonicalPath()
					)
				);
			}
		}
		return dir;
	}
}
