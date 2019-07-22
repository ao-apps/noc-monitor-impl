/*
 * Copyright 2008-2013, 2014, 2016, 2018, 2019 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor.net;

import com.aoindustries.aoserv.client.distribution.OperatingSystemVersion;
import com.aoindustries.aoserv.client.infrastructure.PhysicalServer;
import com.aoindustries.aoserv.client.linux.Server;
import com.aoindustries.aoserv.client.net.Host;
import com.aoindustries.aoserv.client.web.HttpdServer;
import com.aoindustries.noc.monitor.AlertLevelUtils;
import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.noc.monitor.NodeImpl;
import com.aoindustries.noc.monitor.backup.BackupsNode;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.infrastructure.HardDrivesNode;
import com.aoindustries.noc.monitor.infrastructure.UpsNode;
import com.aoindustries.noc.monitor.linux.FilesystemsNode;
import com.aoindustries.noc.monitor.linux.LoadAverageNode;
import com.aoindustries.noc.monitor.linux.MemoryNode;
import com.aoindustries.noc.monitor.linux.RaidNode;
import com.aoindustries.noc.monitor.linux.TimeNode;
import com.aoindustries.noc.monitor.mysql.ServersNode;
import com.aoindustries.noc.monitor.pki.CertificatesNode;
import com.aoindustries.noc.monitor.web.HttpdServersNode;
import com.aoindustries.table.Table;
import com.aoindustries.table.TableListener;
import com.aoindustries.util.WrappedException;
import java.io.File;
import java.io.IOException;
import java.rmi.RemoteException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.sql.SQLException;
import java.util.List;
import javax.swing.SwingUtilities;

/**
 * The node per server.
 *
 * @author  AO Industries, Inc.
 */
public class HostNode extends NodeImpl {

	private static final long serialVersionUID = 1L;

	public final HostsNode hostsNode;
	private final Host _host;
	private final int _pack;
	private final String _name;
	private final String _label;

	private boolean started;
	volatile private BackupsNode _backupsNode;
	volatile private DevicesNode _netDevicesNode;
	volatile private HttpdServersNode _httpdServersNode;
	volatile private ServersNode _mysqlServersNode;
	volatile private HardDrivesNode _hardDrivesNode;
	volatile private RaidNode _raidNode;
	volatile private CertificatesNode _sslCertificatesNode;
	volatile private UpsNode _upsNode;
	volatile private FilesystemsNode _filesystemsNode;
	volatile private LoadAverageNode _loadAverageNode;
	volatile private MemoryNode _memoryNode;
	volatile private TimeNode _timeNode;

	HostNode(HostsNode hostsNode, Host host, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws RemoteException, IOException, SQLException {
		super(port, csf, ssf);
		this.hostsNode = hostsNode;
		this._host = host;
		this._pack = host.getPackageId();
		this._name = host.getName();
		this._label = host.toString();
	}

	@Override
	public HostsNode getParent() {
		return hostsNode;
	}

	public Host getHost() {
		return _host;
	}

	@Override
	public boolean getAllowsChildren() {
		return true;
	}

	@Override
	public List<NodeImpl> getChildren() {
		return getSnapshot(
			this._backupsNode,
			this._netDevicesNode,
			this._httpdServersNode,
			this._mysqlServersNode,
			this._hardDrivesNode,
			this._raidNode,
			this._sslCertificatesNode,
			this._upsNode,
			this._filesystemsNode,
			this._loadAverageNode,
			this._memoryNode,
			this._timeNode
		);
	}

	/**
	 * The alert level is equal to the highest alert level of its children.
	 */
	@Override
	public AlertLevel getAlertLevel() {
		return constrainAlertLevel(
			AlertLevelUtils.getMaxAlertLevel(
				this._backupsNode,
				this._netDevicesNode,
				this._httpdServersNode,
				this._mysqlServersNode,
				this._hardDrivesNode,
				this._raidNode,
				this._sslCertificatesNode,
				this._upsNode,
				this._filesystemsNode,
				this._loadAverageNode,
				this._memoryNode,
				this._timeNode
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
		return _label;
	}

	private final TableListener tableListener = (Table<?> table) -> {
		try {
			verifyNetDevices();
			verifyHttpdServers();
			verifyMySQLServers();
			verifyHardDrives();
			verifyRaid();
			verifySslCertificates();
			verifyUps();
			verifyFilesystems();
			verifyLoadAverage();
			verifyMemory();
			verifyTime();
		} catch(IOException | SQLException err) {
			throw new WrappedException(err);
		}
	};

	/**
	 * Starts this node after it is added to the parent.
	 */
	public void start() throws IOException, SQLException {
		synchronized(this) {
			if(started) throw new IllegalStateException();
			started = true;
			hostsNode.rootNode.conn.getLinux().getServer().addTableListener(tableListener, 100);
			hostsNode.rootNode.conn.getNet().getDevice().addTableListener(tableListener, 100);
			hostsNode.rootNode.conn.getWeb().getHttpdServer().addTableListener(tableListener, 100);
			hostsNode.rootNode.conn.getMysql().getServer().addTableListener(tableListener, 100);
			hostsNode.rootNode.conn.getInfrastructure().getPhysicalServer().addTableListener(tableListener, 100);
			hostsNode.rootNode.conn.getNet().getHost().addTableListener(tableListener, 100);
			hostsNode.rootNode.conn.getPki().getCertificate().addTableListener(tableListener, 100);
			if(_backupsNode==null) {
				_backupsNode = new BackupsNode(this, port, csf, ssf);
				_backupsNode.start();
				hostsNode.rootNode.nodeAdded();
			}
		}
		verifyNetDevices();
		verifyHttpdServers();
		verifyMySQLServers();
		verifyHardDrives();
		verifyRaid();
		verifySslCertificates();
		verifyUps();
		verifyFilesystems();
		verifyLoadAverage();
		verifyMemory();
		verifyTime();
	}

	/**
	 * Stops this node before it is removed from the parent.
	 */
	public void stop() {
		synchronized(this) {
			started = false;
			hostsNode.rootNode.conn.getLinux().getServer().removeTableListener(tableListener);
			hostsNode.rootNode.conn.getNet().getDevice().removeTableListener(tableListener);
			hostsNode.rootNode.conn.getWeb().getHttpdServer().removeTableListener(tableListener);
			hostsNode.rootNode.conn.getMysql().getServer().removeTableListener(tableListener);
			hostsNode.rootNode.conn.getInfrastructure().getPhysicalServer().removeTableListener(tableListener);
			hostsNode.rootNode.conn.getNet().getHost().removeTableListener(tableListener);
			hostsNode.rootNode.conn.getPki().getCertificate().removeTableListener(tableListener);
			if(_timeNode!=null) {
				_timeNode.stop();
				_timeNode = null;
				hostsNode.rootNode.nodeRemoved();
			}
			if(_memoryNode!=null) {
				_memoryNode.stop();
				_memoryNode = null;
				hostsNode.rootNode.nodeRemoved();
			}
			if(_loadAverageNode!=null) {
				_loadAverageNode.stop();
				_loadAverageNode = null;
				hostsNode.rootNode.nodeRemoved();
			}
			if(_filesystemsNode!=null) {
				_filesystemsNode.stop();
				_filesystemsNode = null;
				hostsNode.rootNode.nodeRemoved();
			}
			if(_upsNode!=null) {
				_upsNode.stop();
				_upsNode = null;
				hostsNode.rootNode.nodeRemoved();
			}
			if(_sslCertificatesNode!=null) {
				_sslCertificatesNode.stop();
				_sslCertificatesNode = null;
				hostsNode.rootNode.nodeRemoved();
			}
			if(_raidNode!=null) {
				_raidNode.stop();
				_raidNode = null;
				hostsNode.rootNode.nodeRemoved();
			}
			if(_hardDrivesNode!=null) {
				_hardDrivesNode.stop();
				_hardDrivesNode = null;
				hostsNode.rootNode.nodeRemoved();
			}
			if(_mysqlServersNode!=null) {
				_mysqlServersNode.stop();
				_mysqlServersNode = null;
				hostsNode.rootNode.nodeRemoved();
			}
			if(_httpdServersNode!=null) {
				_httpdServersNode.stop();
				_httpdServersNode = null;
				hostsNode.rootNode.nodeRemoved();
			}
			if(_netDevicesNode!=null) {
				_netDevicesNode.stop();
				_netDevicesNode = null;
				hostsNode.rootNode.nodeRemoved();
			}
			if(_backupsNode!=null) {
				_backupsNode.stop();
				_backupsNode = null;
				hostsNode.rootNode.nodeRemoved();
			}
		}
	}

	private void verifyNetDevices() throws IOException, SQLException {
		assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";
		synchronized(this) {
			if(started) {
				if(_netDevicesNode==null) {
					_netDevicesNode = new DevicesNode(this, _host, port, csf, ssf);
					_netDevicesNode.start();
					hostsNode.rootNode.nodeAdded();
				}
			}
		}
	}

	private void verifyHttpdServers() throws IOException, SQLException {
		assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

		synchronized(this) {
			if(!started) return;
		}

		Server linuxServer = _host.getLinuxServer();
		List<HttpdServer> httpdServers = linuxServer==null ? null : linuxServer.getHttpdServers();
		synchronized(this) {
			if(started) {
				if(httpdServers!=null && !httpdServers.isEmpty()) {
					// Has HTTPD server
					if(_httpdServersNode == null) {
						_httpdServersNode = new HttpdServersNode(this, linuxServer, port, csf, ssf);
						_httpdServersNode.start();
						hostsNode.rootNode.nodeAdded();
					}
				} else {
					// No HTTPD server
					if(_httpdServersNode != null) {
						_httpdServersNode.stop();
						_httpdServersNode = null;
						hostsNode.rootNode.nodeRemoved();
					}
				}
			}
		}
	}

	private void verifyMySQLServers() throws IOException, SQLException {
		assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

		synchronized(this) {
			if(!started) return;
		}

		Server linuxServer = _host.getLinuxServer();
		List<com.aoindustries.aoserv.client.mysql.Server> mysqlServers = linuxServer==null ? null : linuxServer.getMySQLServers();
		synchronized(this) {
			if(started) {
				if(mysqlServers!=null && !mysqlServers.isEmpty()) {
					// Has MySQL server
					if(_mysqlServersNode==null) {
						_mysqlServersNode = new ServersNode(this, linuxServer, port, csf, ssf);
						_mysqlServersNode.start();
						hostsNode.rootNode.nodeAdded();
					}
				} else {
					// No MySQL server
					if(_mysqlServersNode!=null) {
						_mysqlServersNode.stop();
						_mysqlServersNode = null;
						hostsNode.rootNode.nodeRemoved();
					}
				}
			}
		}
	}

	private void verifyHardDrives() throws IOException, SQLException {
		assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

		synchronized(this) {
			if(!started) return;
		}

		Server linuxServer = _host.getLinuxServer();
		OperatingSystemVersion osvObj = _host.getOperatingSystemVersion();
		int osv = osvObj==null ? -1 : osvObj.getPkey();
		synchronized(this) {
			if(started) {
				if(
					linuxServer!=null
					&& (
						osv == OperatingSystemVersion.CENTOS_5_DOM0_I686
						|| osv == OperatingSystemVersion.CENTOS_5_DOM0_X86_64
						|| osv == OperatingSystemVersion.CENTOS_7_DOM0_X86_64
					)
				) {
					// Has hddtemp monitoring
					if(_hardDrivesNode==null) {
						_hardDrivesNode = new HardDrivesNode(this, linuxServer, port, csf, ssf);
						_hardDrivesNode.start();
						hostsNode.rootNode.nodeAdded();
					}
				} else {
					// No hddtemp monitoring
					if(_hardDrivesNode!=null) {
						_hardDrivesNode.stop();
						_hardDrivesNode = null;
						hostsNode.rootNode.nodeRemoved();
					}
				}
			}
		}
	}

	private void verifyRaid() throws IOException, SQLException {
		assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

		synchronized(this) {
			if(!started) return;
		}

		Server linuxServer = _host.getLinuxServer();
		synchronized(this) {
			if(started) {
				if(linuxServer==null) {
					// No raid monitoring
					if(_raidNode!=null) {
						_raidNode.stop();
						_raidNode = null;
						hostsNode.rootNode.nodeRemoved();
					}
				} else {
					// Has raid monitoring
					if(_raidNode==null) {
						_raidNode = new RaidNode(this, linuxServer, port, csf, ssf);
						_raidNode.start();
						hostsNode.rootNode.nodeAdded();
					}
				}
			}
		}
	}

	private void verifySslCertificates() throws IOException, SQLException {
		assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

		synchronized(this) {
			if(!started) return;
		}

		Server linuxServer = _host.getLinuxServer();
		int numCerts = linuxServer == null ? 0 : linuxServer.getSslCertificates().size();
		synchronized(this) {
			if(started) {
				if(numCerts == 0) {
					// No SSL certificate monitoring or no certificates to monitor
					if(_sslCertificatesNode != null) {
						_sslCertificatesNode.stop();
						_sslCertificatesNode = null;
						hostsNode.rootNode.nodeRemoved();
					}
				} else {
					// Has monitored SSL certificates
					if(_sslCertificatesNode == null) {
						_sslCertificatesNode = new CertificatesNode(this, linuxServer, port, csf, ssf);
						_sslCertificatesNode.start();
						hostsNode.rootNode.nodeAdded();
					}
				}
			}
		}
	}

	private void verifyUps() throws IOException, SQLException {
		assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

		synchronized(this) {
			if(!started) return;
		}

		Server linuxServer = _host.getLinuxServer();
		PhysicalServer physicalServer = _host.getPhysicalServer();
		synchronized(this) {
			if(started) {
				if(
					linuxServer==null
					|| physicalServer==null
					|| physicalServer.getUpsType()!=PhysicalServer.UpsType.apc
				) {
					// No UPS monitoring
					if(_upsNode!=null) {
						_upsNode.stop();
						_upsNode = null;
						hostsNode.rootNode.nodeRemoved();
					}
				} else {
					// Has UPS monitoring
					if(_upsNode==null) {
						_upsNode = new UpsNode(this, linuxServer, port, csf, ssf);
						_upsNode.start();
						hostsNode.rootNode.nodeAdded();
					}
				}
			}
		}
	}

	private void verifyFilesystems() throws IOException, SQLException {
		assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

		synchronized(this) {
			if(!started) return;
		}

		Server linuxServer = _host.getLinuxServer();
		synchronized(this) {
			if(started) {
				if(linuxServer==null) {
					// No filesystem monitoring
					if(_filesystemsNode!=null) {
						_filesystemsNode.stop();
						_filesystemsNode = null;
						hostsNode.rootNode.nodeRemoved();
					}
				} else {
					// Has filesystem monitoring
					if(_filesystemsNode==null) {
						_filesystemsNode = new FilesystemsNode(this, linuxServer, port, csf, ssf);
						_filesystemsNode.start();
						hostsNode.rootNode.nodeAdded();
					}
				}
			}
		}
	}

	private void verifyLoadAverage() throws IOException, SQLException {
		assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

		synchronized(this) {
			if(!started) return;
		}

		Server linuxServer = _host.getLinuxServer();
		synchronized(this) {
			if(started) {
				if(linuxServer==null) {
					// No load monitoring
					if(_loadAverageNode!=null) {
						_loadAverageNode.stop();
						_loadAverageNode = null;
						hostsNode.rootNode.nodeRemoved();
					}
				} else {
					// Has load monitoring
					if(_loadAverageNode==null) {
						_loadAverageNode = new LoadAverageNode(this, linuxServer, port, csf, ssf);
						_loadAverageNode.start();
						hostsNode.rootNode.nodeAdded();
					}
				}
			}
		}
	}

	private void verifyMemory() throws IOException, SQLException {
		assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

		synchronized(this) {
			if(!started) return;
		}

		Server linuxServer = _host.getLinuxServer();
		synchronized(this) {
			if(started) {
				if(linuxServer==null) {
					// No memory monitoring
					if(_memoryNode!=null) {
						_memoryNode.stop();
						_memoryNode = null;
						hostsNode.rootNode.nodeRemoved();
					}
				} else {
					// Has memory monitoring
					if(_memoryNode==null) {
						_memoryNode = new MemoryNode(this, linuxServer, port, csf, ssf);
						_memoryNode.start();
						hostsNode.rootNode.nodeAdded();
					}
				}
			}
		}
	}

	private void verifyTime() throws IOException, SQLException {
		assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

		synchronized(this) {
			if(!started) return;
		}

		Server linuxServer = _host.getLinuxServer();
		synchronized(this) {
			if(started) {
				if(linuxServer == null) {
					// No time monitoring
					if(_timeNode!=null) {
						_timeNode.stop();
						_timeNode = null;
						hostsNode.rootNode.nodeRemoved();
					}
				} else {
					// Has time monitoring
					if(_timeNode==null) {
						_timeNode = new TimeNode(this, linuxServer, port, csf, ssf);
						_timeNode.start();
						hostsNode.rootNode.nodeAdded();
					}
				}
			}
		}
	}

	public File getPersistenceDirectory() throws IOException {
		File packDir = new File(hostsNode.getPersistenceDirectory(), Integer.toString(_pack));
		if(!packDir.exists()) {
			if(!packDir.mkdir()) {
				throw new IOException(
					accessor.getMessage(hostsNode.rootNode.locale,
						"error.mkdirFailed",
						packDir.getCanonicalPath()
					)
				);
			}
		}
		File serverDir = new File(packDir, _name);
		if(!serverDir.exists()) {
			if(!serverDir.mkdir()) {
				throw new IOException(
					accessor.getMessage(hostsNode.rootNode.locale,
						"error.mkdirFailed",
						serverDir.getCanonicalPath()
					)
				);
			}
		}
		return serverDir;
	}
}
