/*
 * Copyright 2008-2009 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.aoserv.client.AOServer;
import com.aoindustries.aoserv.client.BackupPartition;
import com.aoindustries.aoserv.client.FailoverFileReplication;
import com.aoindustries.aoserv.client.FailoverFileSchedule;
import com.aoindustries.aoserv.client.Server;
import com.aoindustries.aoserv.client.ServerFarm;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.TableResult;
import com.aoindustries.noc.monitor.common.TableResultListener;
import com.aoindustries.noc.monitor.common.TableResultNode;
import com.aoindustries.table.Table;
import com.aoindustries.table.TableListener;
import com.aoindustries.util.WrappedException;
import java.io.File;
import java.io.IOException;
import java.rmi.RemoteException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The node per server.
 *
 * @author  AO Industries, Inc.
 */
public class BackupsNode extends NodeImpl implements TableResultNode, TableResultListener {

    private static final Logger logger = Logger.getLogger(BackupsNode.class.getName());

    /**
     * All AO-boxes should be backed-up to this server farm (AO admin HQ).
     */
    private static final String AO_SERVER_REQUIRED_BACKUP_FARM = "mob";

    final ServerNode serverNode;
    private final List<BackupNode> backupNodes = new ArrayList<BackupNode>();

    private AlertLevel alertLevel = AlertLevel.UNKNOWN;
    private TableResult lastResult;

    final private List<TableResultListener> tableResultListeners = new ArrayList<TableResultListener>();

    BackupsNode(ServerNode serverNode) {
        this.serverNode = serverNode;
    }

    @Override
    public ServerNode getParent() {
        return serverNode;
    }
    
    @Override
    public boolean getAllowsChildren() {
        return true;
    }

    /**
     * For thread safety and encapsulation, returns an unmodifiable copy of the array.
     */
    @Override
    public List<BackupNode> getChildren() {
        synchronized(backupNodes) {
            return Collections.unmodifiableList(new ArrayList<BackupNode>(backupNodes));
        }
    }

    /**
     * The alert level is equal to the highest alert level of its children.
     */
    @Override
    public AlertLevel getAlertLevel() {
        synchronized(backupNodes) {
            AlertLevel level = this.alertLevel;
            for(NodeImpl backupNode : backupNodes) {
                AlertLevel backupNodeLevel = backupNode.getAlertLevel();
                if(backupNodeLevel.compareTo(level)>0) level = backupNodeLevel;
            }
            return level;
        }
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
        return "backups";
    }

    @Override
    public String getLabel() {
        return accessor.getMessage(/*serverNode.serversNode.rootNode.locale,*/ "BackupsNode.label");
    }
    
    private TableListener tableListener = new TableListener() {
        @Override
        public void tableUpdated(Table<?> table) {
            try {
                verifyBackups();
            } catch(IOException err) {
                throw new WrappedException(err);
            } catch(SQLException err) {
                throw new WrappedException(err);
            }
        }
    };

    void start() throws IOException, SQLException {
        synchronized(backupNodes) {
            serverNode.serversNode.rootNode.conn.getFailoverFileReplications().addTableListener(tableListener, 100);
            serverNode.serversNode.rootNode.conn.getFailoverFileSchedules().addTableListener(tableListener, 100);
            verifyBackups();
        }
    }
    
    void stop() {
        synchronized(backupNodes) {
            serverNode.serversNode.rootNode.conn.getFailoverFileSchedules().removeTableListener(tableListener);
            serverNode.serversNode.rootNode.conn.getFailoverFileReplications().removeTableListener(tableListener);
            for(BackupNode backupNode : backupNodes) {
                backupNode.removeTableResultListener(this);
                backupNode.stop();
                serverNode.serversNode.rootNode.nodeRemoved();
            }
            backupNodes.clear();
        }
    }

    /**
     * Listens for updates in its children nodes and recreates its own internal state off the most recently available line of each child.
     */
    @Override
    public void tableResultUpdated(TableResult tableResult) {
        try {
            verifyBackups();
        } catch(IOException err) {
            logger.log(Level.SEVERE, null, err);
        } catch(SQLException err) {
            logger.log(Level.SEVERE, null, err);
        }
    }

    private void verifyBackups() throws IOException, SQLException {
        final long startTime = System.currentTimeMillis();
        final long startNanos = System.nanoTime();

        List<FailoverFileReplication> failoverFileReplications = serverNode.getServer().getFailoverFileReplications();
        Server server = serverNode.getServer();
        AOServer aoServer = server.getAOServer();

        // Determine if an aoServer is either in the "mob" server farm or has at least one active backup in that location
        boolean missingMobBackup;
        if(aoServer==null) {
            // mobile backup not required for non-AO boxes
            missingMobBackup = false;
        } else {
            ServerFarm sf = server.getServerFarm();
            if(sf.getName().equals(AO_SERVER_REQUIRED_BACKUP_FARM)) {
                // Server itself is in "mob" server farm
                missingMobBackup = false;
            } else {
                missingMobBackup = true;
                for(FailoverFileReplication ffr : failoverFileReplications) {
                    if(ffr.getEnabled()) {
                        BackupPartition bp = ffr.getBackupPartition();
                        if(bp==null) {
                            missingMobBackup = false;
                            break;
                        } else {
                            if(bp.getAOServer().getServer().getServerFarm().getName().equals(AO_SERVER_REQUIRED_BACKUP_FARM)) {
                                // Has at least one enabled replication in Mobile
                                missingMobBackup = false;
                                break;
                            }
                        }
                    }
                }
            }
        }

        // Handle local alert level for missing backups
        AlertLevel newAlertLevel =
            failoverFileReplications.isEmpty() && aoServer!=null // Only AOServers require backups
            ? AlertLevel.MEDIUM
            : missingMobBackup
                ? AlertLevel.LOW
                : AlertLevel.NONE
        ;
        AlertLevel oldAlertLevel = alertLevel;
        String newAlertLevelMessage =
            failoverFileReplications.isEmpty()
            ? accessor.getMessage(/*serverNode.serversNode.rootNode.locale,*/ "BackupsNode.noBackupsConfigured")
            : missingMobBackup
                ? accessor.getMessage(/*serverNode.serversNode.rootNode.locale,*/ "BackupsNode.missingMobBackup")
                : accessor.getMessage(/*serverNode.serversNode.rootNode.locale,*/ "BackupsNode.backupsConfigured")
        ;
        if(oldAlertLevel!=newAlertLevel) {
            alertLevel = newAlertLevel;
            serverNode.serversNode.rootNode.nodeAlertLevelChanged(
                this,
                oldAlertLevel,
                newAlertLevel,
                newAlertLevelMessage
            );
        }

        // Control individual backup nodes
        TableResult newResult;
        synchronized(backupNodes) {
            // Remove old ones
            Iterator<BackupNode> backupNodeIter = backupNodes.iterator();
            while(backupNodeIter.hasNext()) {
                BackupNode backupNode = backupNodeIter.next();
                FailoverFileReplication failoverFileReplication = backupNode.getFailoverFileReplication();
                if(!failoverFileReplications.contains(failoverFileReplication)) {
                    backupNode.removeTableResultListener(this);
                    backupNode.stop();
                    backupNodeIter.remove();
                    serverNode.serversNode.rootNode.nodeRemoved();
                }
            }
            // Add new ones
            for(int c=0;c<failoverFileReplications.size();c++) {
                FailoverFileReplication failoverFileReplication = failoverFileReplications.get(c);
                if(c>=backupNodes.size() || !failoverFileReplication.equals(backupNodes.get(c).getFailoverFileReplication())) {
                    // Insert into proper index
                    BackupNode backupNode = new BackupNode(this, failoverFileReplication);
                    backupNodes.add(c, backupNode);
                    backupNode.start();
                    serverNode.serversNode.rootNode.nodeAdded();
                    backupNode.addTableResultListener(this);
                }
            }

            // Update lastResult: failoverFileReplications and backupNodes are completely aligned currently
            long latency = System.nanoTime() - startNanos;
            if(failoverFileReplications.isEmpty()) {
                newResult = new TableResult(
                    serverNode.serversNode.rootNode.monitoringPoint,
                    startTime,
                    latency,
                    true,
                    1,
                    1,
                    Collections.singletonList(accessor.getMessage(/*serverNode.serversNode.rootNode.locale,*/ "BackupsNode.columnHeaders.configurationError")),
                    Collections.singletonList(accessor.getMessage(/*serverNode.serversNode.rootNode.locale,*/ "BackupsNode.noBackupsConfigured")),
                    Collections.singletonList(aoServer==null ? AlertLevel.NONE : AlertLevel.MEDIUM)
                );
            } else if(missingMobBackup) {
                newResult = new TableResult(
                    serverNode.serversNode.rootNode.monitoringPoint,
                    startTime,
                    latency,
                    true,
                    1,
                    1,
                    Collections.singletonList(accessor.getMessage(/*serverNode.serversNode.rootNode.locale,*/ "BackupsNode.columnHeaders.configurationError")),
                    Collections.singletonList(accessor.getMessage(/*serverNode.serversNode.rootNode.locale,*/ "BackupsNode.missingMobBackup")),
                    Collections.singletonList(AlertLevel.LOW)
                );
            } else {
                List<String> columnHeaders = new ArrayList<String>(7);
                columnHeaders.add(accessor.getMessage(/*serverNode.serversNode.rootNode.locale,*/ "BackupsNode.columnHeaders.to"));
                columnHeaders.add(accessor.getMessage(/*serverNode.serversNode.rootNode.locale,*/ "BackupsNode.columnHeaders.path"));
                columnHeaders.add(accessor.getMessage(/*serverNode.serversNode.rootNode.locale,*/ "BackupsNode.columnHeaders.scheduledTimes"));
                columnHeaders.add(accessor.getMessage(/*serverNode.serversNode.rootNode.locale,*/ "BackupsNode.columnHeaders.maxBitRate"));
                columnHeaders.add(accessor.getMessage(/*serverNode.serversNode.rootNode.locale,*/ "BackupsNode.columnHeaders.useCompression"));
                columnHeaders.add(accessor.getMessage(/*serverNode.serversNode.rootNode.locale,*/ "BackupsNode.columnHeaders.retention"));
                columnHeaders.add(accessor.getMessage(/*serverNode.serversNode.rootNode.locale,*/ "BackupsNode.columnHeaders.status"));
                List<Object> tableData = new ArrayList<Object>(failoverFileReplications.size()*7);
                List<AlertLevel> alertLevels = new ArrayList<AlertLevel>(failoverFileReplications.size());
                for(int c=0;c<failoverFileReplications.size();c++) {
                    FailoverFileReplication failoverFileReplication = failoverFileReplications.get(c);
                    BackupPartition backupPartition = failoverFileReplication.getBackupPartition();
                    tableData.add(backupPartition==null ? "null" : backupPartition.getAOServer().getHostname());
                    tableData.add(backupPartition==null ? "null" : backupPartition.getPath());
                    StringBuilder times = new StringBuilder();
                    for(FailoverFileSchedule ffs : failoverFileReplication.getFailoverFileSchedules()) {
                        if(ffs.isEnabled()) {
                            if(times.length()>0) times.append(", ");
                            times.append(ffs.getHour()).append(':');
                            int minute = ffs.getMinute();
                            if(minute<10) times.append('0');
                            times.append(minute);
                        }
                    }
                    tableData.add(times.toString());
                    Long bitRate = failoverFileReplication.getBitRate();
                    if(bitRate==null) {
                        tableData.add(accessor.getMessage(/*serverNode.serversNode.rootNode.locale,*/ "BackupsNode.bitRate.unlimited"));
                    } else {
                        tableData.add(accessor.getMessage(/*serverNode.serversNode.rootNode.locale,*/ "BackupsNode.bitRate", bitRate));
                    }
                    if(failoverFileReplication.getUseCompression()) {
                        tableData.add(accessor.getMessage(/*serverNode.serversNode.rootNode.locale,*/ "BackupsNode.useCompression.true"));
                    } else {
                        tableData.add(accessor.getMessage(/*serverNode.serversNode.rootNode.locale,*/ "BackupsNode.useCompression.false"));
                    }
                    tableData.add(failoverFileReplication.getRetention().getDisplay());
                    BackupNode backupNode = backupNodes.get(c);
                    TableResult backupNodeResult = backupNode.getLastResult();
                    if(backupNodeResult==null) {
                        tableData.add("");
                        alertLevels.add(AlertLevel.UNKNOWN);
                    } else {
                        AlertLevelAndMessage alertLevelAndMessage = backupNode.getAlertLevelAndMessage(serverNode.serversNode.rootNode.locale, backupNodeResult);
                        tableData.add(alertLevelAndMessage.getAlertMessage());
                        alertLevels.add(alertLevelAndMessage.getAlertLevel());
                    }
                }
                newResult = new TableResult(
                    serverNode.serversNode.rootNode.monitoringPoint,
                    startTime,
                    latency,
                    false,
                    7,
                    failoverFileReplications.size(),
                    columnHeaders,
                    tableData,
                    alertLevels
                );
            }
            lastResult = newResult;
        }
        notifyTableResultUpdated(newResult);
    }

    File getPersistenceDirectory() throws IOException {
        File dir = new File(serverNode.getPersistenceDirectory(), "failover_file_replications");
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

    @Override
    final public void addTableResultListener(TableResultListener tableResultListener) {
        synchronized(tableResultListeners) {
            tableResultListeners.add(tableResultListener);
        }
    }

    // TODO: Remove only once, in case add and remove come in out of order with quick GUI changes?
    @Override
    final public void removeTableResultListener(TableResultListener tableResultListener) {
        int foundCount = 0;
        synchronized(tableResultListeners) {
            for(int c=tableResultListeners.size()-1;c>=0;c--) {
                if(tableResultListeners.get(c).equals(tableResultListener)) {
                    tableResultListeners.remove(c);
                    foundCount++;
                }
            }
        }
        if(foundCount!=1) logger.log(Level.WARNING, null, new AssertionError("Expected foundCount==1, got foundCount="+foundCount));
    }

    /**
     * Notifies all of the listeners.
     */
    private void notifyTableResultUpdated(TableResult tableResult) {
        synchronized(tableResultListeners) {
            Iterator<TableResultListener> I = tableResultListeners.iterator();
            while(I.hasNext()) {
                TableResultListener tableResultListener = I.next();
                try {
                    tableResultListener.tableResultUpdated(tableResult);
                } catch(RemoteException err) {
                    I.remove();
                    logger.log(Level.SEVERE, null, err);
                }
            }
        }
    }

    @Override
    public TableResult getLastResult() {
        return lastResult;
    }
}
