/*
 * Copyright 2008 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.aoserv.client.AOServer;
import com.aoindustries.aoserv.client.OperatingSystemVersion;
import com.aoindustries.aoserv.client.Server;
import com.aoindustries.noc.common.AlertLevel;
import com.aoindustries.noc.common.Node;
import com.aoindustries.table.Table;
import com.aoindustries.table.TableListener;
import com.aoindustries.util.WrappedException;
import java.io.File;
import java.io.IOException;
import java.rmi.RemoteException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.SwingUtilities;

/**
 * The node per server.
 *
 * @author  AO Industries, Inc.
 */
public class ServerNode extends NodeImpl {

    final ServersNode serversNode;
    private final Server _server;
    private final String _pack;
    private final String _name;
    private final String _label;

    volatile private BackupsNode _backupsNode;
    volatile private NetDevicesNode _netDevicesNode;
    volatile private HardDrivesNode _hardDrivesNode;
    volatile private RaidNode _raidNode;
    volatile private FilesystemsNode _filesystemsNode;
    volatile private LoadAverageNode _loadAverageNode;
    volatile private MemoryNode _memoryNode;
    volatile private TimeNode _timeNode;

    ServerNode(ServersNode serversNode, Server server, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws RemoteException {
        super(port, csf, ssf);
        this.serversNode = serversNode;
        this._server = server;
        this._pack = server.getPackage().getName();
        this._name = server.getName();
        this._label = server.toString();
    }

    @Override
    public Node getParent() {
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
        List<NodeImpl> children = new ArrayList<NodeImpl>(8);
        BackupsNode backupsNode = this._backupsNode;
        if(backupsNode!=null) children.add(backupsNode);
        NetDevicesNode netDevicesNode = this._netDevicesNode;
        if(netDevicesNode!=null) children.add(netDevicesNode);
        HardDrivesNode hardDrivesNode = this._hardDrivesNode;
        if(hardDrivesNode!=null) children.add(hardDrivesNode);
        RaidNode raidNode = this._raidNode;
        if(raidNode!=null) children.add(raidNode);
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

    @Override
    public String getLabel() {
        return _label;
    }

    private TableListener tableListener = new TableListener() {
        @Override
        public void tableUpdated(Table table) {
            try {
                verifyNetDevices();
                verifyHardDrives();
                verifyRaid();
                verifyFilesystems();
                verifyLoadAverage();
                verifyMemory();
                verifyTime();
            } catch(IOException err) {
                throw new WrappedException(err);
            }
        }
    };

    /**
     * Starts this node after it is added to the parent.
     */
    synchronized void start() throws IOException, SQLException {
        serversNode.rootNode.conn.aoServers.addTableListener(tableListener, 100);
        serversNode.rootNode.conn.netDevices.addTableListener(tableListener, 100);
        serversNode.rootNode.conn.servers.addTableListener(tableListener, 100);
        if(_backupsNode==null) {
            _backupsNode = new BackupsNode(this, port, csf, ssf);
            _backupsNode.start();
            serversNode.rootNode.nodeAdded();
        }
        verifyNetDevices();
        verifyHardDrives();
        verifyRaid();
        verifyFilesystems();
        verifyLoadAverage();
        verifyMemory();
        verifyTime();
    }

    /**
     * Stops this node before it is removed from the parent.
     */
    synchronized void stop() {
        serversNode.rootNode.conn.aoServers.removeTableListener(tableListener);
        serversNode.rootNode.conn.netDevices.removeTableListener(tableListener);
        serversNode.rootNode.conn.servers.removeTableListener(tableListener);
        TimeNode timeNode = this._timeNode;
        if(timeNode!=null) {
            timeNode.stop();
            this._timeNode = null;
            serversNode.rootNode.nodeRemoved();
        }
        MemoryNode memoryNode = this._memoryNode;
        if(memoryNode!=null) {
            memoryNode.stop();
            this._memoryNode = null;
            serversNode.rootNode.nodeRemoved();
        }
        LoadAverageNode loadAverageNode = this._loadAverageNode;
        if(loadAverageNode!=null) {
            loadAverageNode.stop();
            this._loadAverageNode = null;
            serversNode.rootNode.nodeRemoved();
        }
        FilesystemsNode filesystemsNode = this._filesystemsNode;
        if(filesystemsNode!=null) {
            filesystemsNode.stop();
            this._filesystemsNode = null;
            serversNode.rootNode.nodeRemoved();
        }
        RaidNode raidNode = this._raidNode;
        if(raidNode!=null) {
            raidNode.stop();
            this._raidNode = null;
            serversNode.rootNode.nodeRemoved();
        }
        HardDrivesNode hardDrivesNode = this._hardDrivesNode;
        if(hardDrivesNode!=null) {
            hardDrivesNode.stop();
            this._hardDrivesNode = null;
            serversNode.rootNode.nodeRemoved();
        }
        NetDevicesNode netDevicesNode = this._netDevicesNode;
        if(netDevicesNode!=null) {
            netDevicesNode.stop();
            this._netDevicesNode = null;
            serversNode.rootNode.nodeRemoved();
        }
        BackupsNode backupsNode = this._backupsNode;
        if(backupsNode!=null) {
            backupsNode.stop();
            this._backupsNode = null;
            serversNode.rootNode.nodeRemoved();
        }
    }

    synchronized private void verifyNetDevices() throws IOException {
        assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

        //AOServer aoServer = server.getAOServer();
        /*if(aoServer==null) {
            // No net devices
            NetDevicesNode localNetDevicesNode = this.netDevicesNode;
            if(localNetDevicesNode!=null) {
                localNetDevicesNode.stop();
                this.netDevicesNode = null;
                serversNode.rootNode.nodeRemoved();
            }
        } else {*/
            // Has net devices
            if(_netDevicesNode==null) {
                _netDevicesNode = new NetDevicesNode(this, _server, port, csf, ssf);
                _netDevicesNode.start();
                serversNode.rootNode.nodeAdded();
            }
        //}
    }

    synchronized private void verifyHardDrives() throws IOException {
        assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

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
                _hardDrivesNode = new HardDrivesNode(this, aoServer, port, csf, ssf);
                _hardDrivesNode.start();
                serversNode.rootNode.nodeAdded();
            }
        } else {
            // No hddtemp monitoring
            HardDrivesNode hardDrivesNode = this._hardDrivesNode;
            if(hardDrivesNode!=null) {
                hardDrivesNode.stop();
                this._hardDrivesNode = null;
                serversNode.rootNode.nodeRemoved();
            }
        }
    }

    synchronized private void verifyRaid() throws IOException {
        assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

        AOServer aoServer = _server.getAOServer();
        if(aoServer==null) {
            // No raid monitoring
            RaidNode localRaidNode = this._raidNode;
            if(localRaidNode!=null) {
                localRaidNode.stop();
                this._raidNode = null;
                serversNode.rootNode.nodeRemoved();
            }
        } else {
            // Has raid monitoring
            if(_raidNode==null) {
                _raidNode = new RaidNode(this, aoServer, port, csf, ssf);
                _raidNode.start();
                serversNode.rootNode.nodeAdded();
            }
        }
    }

    synchronized private void verifyFilesystems() throws IOException {
        assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

        AOServer aoServer = _server.getAOServer();
        if(aoServer==null) {
            // No filesystem monitoring
            FilesystemsNode filesystemsNode = this._filesystemsNode;
            if(filesystemsNode!=null) {
                filesystemsNode.stop();
                this._filesystemsNode = null;
                serversNode.rootNode.nodeRemoved();
            }
        } else {
            // Has filesystem monitoring
            if(_filesystemsNode==null) {
                _filesystemsNode = new FilesystemsNode(this, aoServer, port, csf, ssf);
                _filesystemsNode.start();
                serversNode.rootNode.nodeAdded();
            }
        }
    }

    synchronized private void verifyLoadAverage() throws IOException {
        assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

        AOServer aoServer = _server.getAOServer();
        if(aoServer==null) {
            // No load monitoring
            LoadAverageNode loadAverageNode = this._loadAverageNode;
            if(loadAverageNode!=null) {
                loadAverageNode.stop();
                this._loadAverageNode = null;
                serversNode.rootNode.nodeRemoved();
            }
        } else {
            // Has load monitoring
            if(_loadAverageNode==null) {
                _loadAverageNode = new LoadAverageNode(this, aoServer, port, csf, ssf);
                _loadAverageNode.start();
                serversNode.rootNode.nodeAdded();
            }
        }
    }

    synchronized private void verifyMemory() throws IOException {
        assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

        AOServer aoServer = _server.getAOServer();
        if(aoServer==null) {
            // No memory monitoring
            MemoryNode memoryNode = this._memoryNode;
            if(memoryNode!=null) {
                memoryNode.stop();
                this._memoryNode = null;
                serversNode.rootNode.nodeRemoved();
            }
        } else {
            // Has memory monitoring
            if(_memoryNode==null) {
                _memoryNode = new MemoryNode(this, aoServer, port, csf, ssf);
                _memoryNode.start();
                serversNode.rootNode.nodeAdded();
            }
        }
    }

    synchronized private void verifyTime() throws IOException {
        assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

        AOServer aoServer = _server.getAOServer();
        if(
            aoServer == null
            // TODO: Remove this os exclusion once new HttpdManager installed to Mandriva 2006.0
            && aoServer.getServer().getOperatingSystemVersion().getPkey() != OperatingSystemVersion.MANDRIVA_2006_0_I586
        ) {
            // No time monitoring
            TimeNode timeNode = this._timeNode;
            if(timeNode!=null) {
                timeNode.stop();
                this._timeNode = null;
                serversNode.rootNode.nodeRemoved();
            }
        } else {
            // Has time monitoring
            if(_timeNode==null) {
                _timeNode = new TimeNode(this, aoServer, port, csf, ssf);
                _timeNode.start();
                serversNode.rootNode.nodeAdded();
            }
        }
    }

    File getPersistenceDirectory() throws IOException {
        File packDir = new File(serversNode.getPersistenceDirectory(), _pack);
        if(!packDir.exists()) {
            if(!packDir.mkdir()) {
                throw new IOException(
                    ApplicationResourcesAccessor.getMessage(
                        serversNode.rootNode.locale,
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
                    ApplicationResourcesAccessor.getMessage(
                        serversNode.rootNode.locale,
                        "error.mkdirFailed",
                        serverDir.getCanonicalPath()
                    )
                );
            }
        }
        return serverDir;
    }
}
