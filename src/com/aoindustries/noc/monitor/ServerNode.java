/*
 * Copyright 2008-2012 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.aoserv.client.AOServer;
import com.aoindustries.aoserv.client.MySQLServer;
import com.aoindustries.aoserv.client.OperatingSystemVersion;
import com.aoindustries.aoserv.client.PhysicalServer;
import com.aoindustries.aoserv.client.Server;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.Node;
import com.aoindustries.table.Table;
import com.aoindustries.table.TableListener;
import com.aoindustries.util.WrappedException;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The node per server.
 *
 * @author  AO Industries, Inc.
 */
public class ServerNode extends NodeImpl {

    private static final long serialVersionUID = 1L;

    final ServersNode serversNode;
    private final Server _server;
    private final int _pack;
    private final String _name;
    private final String _label;

    volatile private BackupsNode _backupsNode;
    volatile private NetDevicesNode _netDevicesNode;
    volatile private MySQLServersNode _mysqlServersNode;
    volatile private HardDrivesNode _hardDrivesNode;
    volatile private RaidNode _raidNode;
    volatile private UpsNode _upsNode;
    volatile private FilesystemsNode _filesystemsNode;
    volatile private LoadAverageNode _loadAverageNode;
    volatile private MemoryNode _memoryNode;
    volatile private TimeNode _timeNode;

    ServerNode(ServersNode serversNode, Server server) {
        this.serversNode = serversNode;
        this._server = server;
        this._pack = server.getPackageId();
        this._name = server.getName();
        this._label = server.toString();
    }

    @Override
    public ServersNode getParent() {
        return serversNode;
    }

    public Server getServer() {
        return _server;
    }

    @Override
    public boolean getAllowsChildren() {
        return true;
    }

    /**
     * For thread safety and encapsulation, returns an unmodifiable copy of the array.
     */
    @Override
    public List<? extends Node> getChildren() {
        List<NodeImpl> children = new ArrayList<NodeImpl>(9);
        BackupsNode backupsNode = this._backupsNode;
        if(backupsNode!=null) children.add(backupsNode);
        NetDevicesNode netDevicesNode = this._netDevicesNode;
        if(netDevicesNode!=null) children.add(netDevicesNode);
        MySQLServersNode mysqlServersNode = this._mysqlServersNode;
        if(mysqlServersNode!=null) children.add(mysqlServersNode);
        HardDrivesNode hardDrivesNode = this._hardDrivesNode;
        if(hardDrivesNode!=null) children.add(hardDrivesNode);
        RaidNode raidNode = this._raidNode;
        if(raidNode!=null) children.add(raidNode);
        UpsNode upsNode = this._upsNode;
        if(upsNode!=null) children.add(upsNode);
        FilesystemsNode filesystemsNode = this._filesystemsNode;
        if(filesystemsNode!=null) children.add(filesystemsNode);
        LoadAverageNode loadAverageNode = this._loadAverageNode;
        if(loadAverageNode!=null) children.add(loadAverageNode);
        MemoryNode memoryNode = this._memoryNode;
        if(memoryNode!=null) children.add(memoryNode);
        TimeNode timeNode = this._timeNode;
        if(timeNode!=null) children.add(timeNode);
        return Collections.unmodifiableList(children);
    }

    /**
     * The alert level is equal to the highest alert level of its children.
     */
    @Override
    public AlertLevel getAlertLevel() {
        AlertLevel level = AlertLevel.NONE;

        BackupsNode backupsNode = this._backupsNode;
        if(backupsNode!=null) {
            AlertLevel backupsNodeLevel = backupsNode.getAlertLevel();
            if(backupsNodeLevel.compareTo(level)>0) level = backupsNodeLevel;
        }
        NetDevicesNode netDevicesNode = this._netDevicesNode;
        if(netDevicesNode!=null) {
            AlertLevel netDevicesNodeLevel = netDevicesNode.getAlertLevel();
            if(netDevicesNodeLevel.compareTo(level)>0) level = netDevicesNodeLevel;
        }
        MySQLServersNode mysqlServersNode = this._mysqlServersNode;
        if(mysqlServersNode!=null) {
            AlertLevel mysqlServersNodeLevel = mysqlServersNode.getAlertLevel();
            if(mysqlServersNodeLevel.compareTo(level)>0) level = mysqlServersNodeLevel;
        }
        HardDrivesNode hardDrivesNode = this._hardDrivesNode;
        if(hardDrivesNode!=null) {
            AlertLevel hardDrivesNodeLevel = hardDrivesNode.getAlertLevel();
            if(hardDrivesNodeLevel.compareTo(level)>0) level = hardDrivesNodeLevel;
        }
        RaidNode raidNode = this._raidNode;
        if(raidNode!=null) {
            AlertLevel raidNodeLevel = raidNode.getAlertLevel();
            if(raidNodeLevel.compareTo(level)>0) level = raidNodeLevel;
        }
        UpsNode upsNode = this._upsNode;
        if(upsNode!=null) {
            AlertLevel upsNodeLevel = upsNode.getAlertLevel();
            if(upsNodeLevel.compareTo(level)>0) level = upsNodeLevel;
        }
        FilesystemsNode filesystemsNode = this._filesystemsNode;
        if(filesystemsNode!=null) {
            AlertLevel filesystemsNodeLevel = filesystemsNode.getAlertLevel();
            if(filesystemsNodeLevel.compareTo(level)>0) level = filesystemsNodeLevel;
        }
        LoadAverageNode loadAverageNode = this._loadAverageNode;
        if(loadAverageNode!=null) {
            AlertLevel loadAverageNodeLevel = loadAverageNode.getAlertLevel();
            if(loadAverageNodeLevel.compareTo(level)>0) level = loadAverageNodeLevel;
        }
        MemoryNode memoryNode = this._memoryNode;
        if(memoryNode!=null) {
            AlertLevel memoryNodeLevel = memoryNode.getAlertLevel();
            if(memoryNodeLevel.compareTo(level)>0) level = memoryNodeLevel;
        }
        TimeNode timeNode = this._timeNode;
        if(timeNode!=null) {
            AlertLevel timeNodeLevel = timeNode.getAlertLevel();
            if(timeNodeLevel.compareTo(level)>0) level = timeNodeLevel;
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
        return _label;
    }

    @Override
    public String getLabel() {
        return _label;
    }

    private TableListener tableListener = new TableListener() {
        @Override
        public void tableUpdated(Table<?> table) {
            try {
                verifyNetDevices();
                verifyMySQLServers();
                verifyHardDrives();
                verifyRaid();
                verifyUps();
                verifyFilesystems();
                verifyLoadAverage();
                verifyMemory();
                verifyTime();
            } catch(IOException err) {
                throw new WrappedException(err);
            } catch(SQLException err) {
                throw new WrappedException(err);
            }
        }
    };

    /**
     * Starts this node after it is added to the parent.
     */
    synchronized void start() throws IOException, SQLException {
        serversNode.rootNode.conn.getAoServers().addTableListener(tableListener, 100);
        serversNode.rootNode.conn.getNetDevices().addTableListener(tableListener, 100);
        serversNode.rootNode.conn.getMysqlServers().addTableListener(tableListener, 100);
        serversNode.rootNode.conn.getPhysicalServers().addTableListener(tableListener, 100);
        serversNode.rootNode.conn.getServers().addTableListener(tableListener, 100);
        if(_backupsNode==null) {
            _backupsNode = new BackupsNode(this);
            _backupsNode.start();
            serversNode.rootNode.nodeAdded();
        }
        verifyNetDevices();
        verifyMySQLServers();
        verifyHardDrives();
        verifyRaid();
        verifyUps();
        verifyFilesystems();
        verifyLoadAverage();
        verifyMemory();
        verifyTime();
    }

    /**
     * Stops this node before it is removed from the parent.
     */
    synchronized void stop() {
        serversNode.rootNode.conn.getAoServers().removeTableListener(tableListener);
        serversNode.rootNode.conn.getNetDevices().removeTableListener(tableListener);
        serversNode.rootNode.conn.getMysqlServers().removeTableListener(tableListener);
        serversNode.rootNode.conn.getPhysicalServers().removeTableListener(tableListener);
        serversNode.rootNode.conn.getServers().removeTableListener(tableListener);
        if(_timeNode!=null) {
            _timeNode.stop();
            serversNode.rootNode.nodeRemoved();
            _timeNode = null;
        }
        if(_memoryNode!=null) {
            _memoryNode.stop();
            serversNode.rootNode.nodeRemoved();
            _memoryNode = null;
        }
        if(_loadAverageNode!=null) {
            _loadAverageNode.stop();
            serversNode.rootNode.nodeRemoved();
            _loadAverageNode = null;
        }
        if(_filesystemsNode!=null) {
            _filesystemsNode.stop();
            serversNode.rootNode.nodeRemoved();
            _filesystemsNode = null;
        }
        if(_upsNode!=null) {
            _upsNode.stop();
            serversNode.rootNode.nodeRemoved();
            _upsNode = null;
        }
        if(_raidNode!=null) {
            _raidNode.stop();
            serversNode.rootNode.nodeRemoved();
            _raidNode = null;
        }
        if(_hardDrivesNode!=null) {
            _hardDrivesNode.stop();
            serversNode.rootNode.nodeRemoved();
            _hardDrivesNode = null;
        }
        if(_mysqlServersNode!=null) {
            _mysqlServersNode.stop();
            serversNode.rootNode.nodeRemoved();
            _mysqlServersNode = null;
        }
        if(_netDevicesNode!=null) {
            _netDevicesNode.stop();
            serversNode.rootNode.nodeRemoved();
            _netDevicesNode = null;
        }
        if(_backupsNode!=null) {
            _backupsNode.stop();
            serversNode.rootNode.nodeRemoved();
            _backupsNode = null;
        }
    }

    synchronized private void verifyNetDevices() throws IOException, SQLException {
        if(_netDevicesNode==null) {
            _netDevicesNode = new NetDevicesNode(this, _server);
            _netDevicesNode.start();
            serversNode.rootNode.nodeAdded();
        }
    }

    synchronized private void verifyMySQLServers() throws IOException, SQLException {
        AOServer aoServer = _server.getAOServer();
        List<MySQLServer> mysqlServers = aoServer==null ? null : aoServer.getMySQLServers();
        if(mysqlServers!=null && !mysqlServers.isEmpty()) {
            // Has MySQL server
            if(_mysqlServersNode==null) {
                _mysqlServersNode = new MySQLServersNode(this, aoServer);
                _mysqlServersNode.start();
                serversNode.rootNode.nodeAdded();
            }
        } else {
            // No MySQL server
            if(_mysqlServersNode!=null) {
                _mysqlServersNode.stop();
                serversNode.rootNode.nodeRemoved();
                _mysqlServersNode = null;
            }
        }
    }

    synchronized private void verifyHardDrives() throws IOException, SQLException {
        AOServer aoServer = _server.getAOServer();
        OperatingSystemVersion osvObj = _server.getOperatingSystemVersion();
        int osv = osvObj==null ? -1 : osvObj.getPkey();
        if(
            aoServer!=null
            && (
                osv==OperatingSystemVersion.CENTOS_5DOM0_I686
                || osv==OperatingSystemVersion.CENTOS_5DOM0_X86_64
            )
        ) {
            // Has hddtemp monitoring
            if(_hardDrivesNode==null) {
                _hardDrivesNode = new HardDrivesNode(this, aoServer);
                _hardDrivesNode.start();
                serversNode.rootNode.nodeAdded();
            }
        } else {
            // No hddtemp monitoring
            if(_hardDrivesNode!=null) {
                _hardDrivesNode.stop();
                serversNode.rootNode.nodeRemoved();
                _hardDrivesNode = null;
            }
        }
    }

    synchronized private void verifyRaid() throws IOException, SQLException {
        AOServer aoServer = _server.getAOServer();
        if(aoServer==null) {
            // No raid monitoring
            if(_raidNode!=null) {
                _raidNode.stop();
                serversNode.rootNode.nodeRemoved();
                _raidNode = null;
            }
        } else {
            // Has raid monitoring
            if(_raidNode==null) {
                _raidNode = new RaidNode(this, aoServer);
                _raidNode.start();
                serversNode.rootNode.nodeAdded();
            }
        }
    }

    synchronized private void verifyUps() throws IOException, SQLException {
        AOServer aoServer = _server.getAOServer();
        PhysicalServer physicalServer = _server.getPhysicalServer();
        if(
            aoServer==null
            || physicalServer==null
            || physicalServer.getUpsType()!=PhysicalServer.UpsType.apc
        ) {
            // No UPS monitoring
            if(_upsNode!=null) {
                _upsNode.stop();
                serversNode.rootNode.nodeRemoved();
                _upsNode = null;
            }
        } else {
            // Has UPS monitoring
            if(_upsNode==null) {
                _upsNode = new UpsNode(this, aoServer);
                _upsNode.start();
                serversNode.rootNode.nodeAdded();
            }
        }
    }

    synchronized private void verifyFilesystems() throws IOException, SQLException {
        AOServer aoServer = _server.getAOServer();
        if(aoServer==null) {
            // No filesystem monitoring
            if(_filesystemsNode!=null) {
                _filesystemsNode.stop();
                serversNode.rootNode.nodeRemoved();
                _filesystemsNode = null;
            }
        } else {
            // Has filesystem monitoring
            if(_filesystemsNode==null) {
                _filesystemsNode = new FilesystemsNode(this, aoServer);
                _filesystemsNode.start();
                serversNode.rootNode.nodeAdded();
            }
        }
    }

    synchronized private void verifyLoadAverage() throws IOException, SQLException {
        AOServer aoServer = _server.getAOServer();
        if(aoServer==null) {
            // No load monitoring
            if(_loadAverageNode!=null) {
                _loadAverageNode.stop();
                serversNode.rootNode.nodeRemoved();
                _loadAverageNode = null;
            }
        } else {
            // Has load monitoring
            if(_loadAverageNode==null) {
                _loadAverageNode = new LoadAverageNode(this, aoServer);
                _loadAverageNode.start();
                serversNode.rootNode.nodeAdded();
            }
        }
    }

    synchronized private void verifyMemory() throws IOException, SQLException {
        AOServer aoServer = _server.getAOServer();
        if(aoServer==null) {
            // No memory monitoring
            if(_memoryNode!=null) {
                _memoryNode.stop();
                serversNode.rootNode.nodeRemoved();
                _memoryNode = null;
            }
        } else {
            // Has memory monitoring
            if(_memoryNode==null) {
                _memoryNode = new MemoryNode(this, aoServer);
                _memoryNode.start();
                serversNode.rootNode.nodeAdded();
            }
        }
    }

    synchronized private void verifyTime() throws IOException, SQLException {
        AOServer aoServer = _server.getAOServer();
        if(aoServer == null) {
            // No time monitoring
            if(_timeNode!=null) {
                _timeNode.stop();
                serversNode.rootNode.nodeRemoved();
                _timeNode = null;
            }
        } else {
            // Has time monitoring
            if(_timeNode==null) {
                _timeNode = new TimeNode(this, aoServer);
                _timeNode.start();
                serversNode.rootNode.nodeAdded();
            }
        }
    }

    File getPersistenceDirectory() throws IOException {
        File packDir = new File(serversNode.getPersistenceDirectory(), Integer.toString(_pack));
        if(!packDir.exists()) {
            if(!packDir.mkdir()) {
                throw new IOException(
                    accessor.getMessage(
                        //serversNode.rootNode.locale,
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
                    accessor.getMessage(
                        //serversNode.rootNode.locale,
                        "error.mkdirFailed",
                        serverDir.getCanonicalPath()
                    )
                );
            }
        }
        return serverDir;
    }
}
