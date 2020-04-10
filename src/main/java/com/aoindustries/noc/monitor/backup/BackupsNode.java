/*
 * Copyright 2008-2009, 2014, 2016, 2018, 2019, 2020 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor.backup;

import com.aoindustries.aoserv.client.backup.BackupPartition;
import com.aoindustries.aoserv.client.backup.FileReplication;
import com.aoindustries.aoserv.client.backup.FileReplicationSchedule;
import com.aoindustries.aoserv.client.infrastructure.ServerFarm;
import com.aoindustries.aoserv.client.linux.Server;
import com.aoindustries.aoserv.client.net.Host;
import com.aoindustries.exception.WrappedException;
import com.aoindustries.noc.monitor.AlertLevelAndMessage;
import com.aoindustries.noc.monitor.AlertLevelUtils;
import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.noc.monitor.NodeImpl;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.TableResult;
import com.aoindustries.noc.monitor.common.TableResultListener;
import com.aoindustries.noc.monitor.common.TableResultNode;
import com.aoindustries.noc.monitor.net.HostNode;
import com.aoindustries.table.Table;
import com.aoindustries.table.TableListener;
import java.io.File;
import java.io.IOException;
import java.rmi.RemoteException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;

/**
 * The node per server.
 *
 * @author  AO Industries, Inc.
 */
public class BackupsNode extends NodeImpl implements TableResultNode, TableResultListener {

	private static final long serialVersionUID = 1L;

	private static final Logger logger = Logger.getLogger(BackupsNode.class.getName());

	/**
	 * All AO-boxes should be backed-up to this server farm (AO admin HQ).
	 */
	private static final String AO_SERVER_REQUIRED_BACKUP_FARM = "mob";

	final HostNode hostNode;
	private final List<BackupNode> backupNodes = new ArrayList<>();
	private boolean started;

	private AlertLevel alertLevel;
	private TableResult lastResult;

	final private List<TableResultListener> tableResultListeners = new ArrayList<>();

	public BackupsNode(HostNode hostNode, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws RemoteException {
		super(port, csf, ssf);
		this.hostNode = hostNode;
	}

	@Override
	public HostNode getParent() {
		return hostNode;
	}

	@Override
	public boolean getAllowsChildren() {
		return true;
	}

	@Override
	public List<BackupNode> getChildren() {
		synchronized(backupNodes) {
			return getSnapshot(backupNodes);
		}
	}

	/**
	 * The alert level is equal to the highest alert level of its children.
	 */
	@Override
	public AlertLevel getAlertLevel() {
		AlertLevel level;
		synchronized(backupNodes) {
			level = AlertLevelUtils.getMaxAlertLevel(
				alertLevel==null ? AlertLevel.UNKNOWN : alertLevel,
				backupNodes
			);
		}
		return constrainAlertLevel(level);
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
		return accessor.getMessage(hostNode.hostsNode.rootNode.locale, "BackupsNode.label");
	}

	private final TableListener tableListener = (Table<?> table) -> {
		try {
			verifyBackups();
		} catch(IOException | SQLException err) {
			throw new WrappedException(err);
		}
	};

	public void start() throws IOException, SQLException {
		synchronized(backupNodes) {
			if(started) throw new IllegalStateException();
			started = true;
			hostNode.hostsNode.rootNode.conn.getBackup().getFileReplication().addTableListener(tableListener, 100);
			hostNode.hostsNode.rootNode.conn.getBackup().getFileReplicationSchedule().addTableListener(tableListener, 100);
		}
		verifyBackups();
	}

	public void stop() {
		synchronized(backupNodes) {
			started = false;
			hostNode.hostsNode.rootNode.conn.getBackup().getFileReplicationSchedule().removeTableListener(tableListener);
			hostNode.hostsNode.rootNode.conn.getBackup().getFileReplication().removeTableListener(tableListener);
			for(BackupNode backupNode : backupNodes) {
				backupNode.removeTableResultListener(this);
				backupNode.stop();
				hostNode.hostsNode.rootNode.nodeRemoved();
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
		} catch(IOException | SQLException err) {
			logger.log(Level.SEVERE, null, err);
		}
	}

	private void verifyBackups() throws IOException, SQLException {
		assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

		synchronized(backupNodes) {
			if(!started) return;
		}

		final long startTime = System.currentTimeMillis();
		final long startNanos = System.nanoTime();

		List<FileReplication> failoverFileReplications = hostNode.getHost().getFailoverFileReplications();
		Host host = hostNode.getHost();
		Server linuxServer = host.getLinuxServer();

		// Determine if a linuxServer is either in the "mob" server farm or has at least one active backup in that location
		boolean missingMobBackup;
		if(linuxServer==null) {
			// mobile backup not required for non-AO boxes
			missingMobBackup = false;
		} else {
			ServerFarm sf = host.getServerFarm();
			if(sf.getName().equals(AO_SERVER_REQUIRED_BACKUP_FARM)) {
				// Host itself is in "mob" server farm
				missingMobBackup = false;
			} else {
				missingMobBackup = true;
				for(FileReplication ffr : failoverFileReplications) {
					if(ffr.getEnabled()) {
						BackupPartition bp = ffr.getBackupPartition();
						if(bp==null) {
							missingMobBackup = false;
							break;
						} else {
							if(bp.getLinuxServer().getHost().getServerFarm().getName().equals(AO_SERVER_REQUIRED_BACKUP_FARM)) {
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
			failoverFileReplications.isEmpty() && linuxServer!=null // Only AOServers require backups
			? AlertLevel.MEDIUM
			: missingMobBackup
				? AlertLevel.LOW
				: AlertLevel.NONE
		;

		// Control individual backup nodes
		TableResult newResult;
		synchronized(backupNodes) {
			if(!started) return;

			AlertLevel oldAlertLevel = alertLevel;
			if(oldAlertLevel == null) oldAlertLevel = AlertLevel.UNKNOWN;
			String newAlertLevelMessage =
				failoverFileReplications.isEmpty()
				? accessor.getMessage(hostNode.hostsNode.rootNode.locale, "BackupsNode.noBackupsConfigured")
				: missingMobBackup
					? accessor.getMessage(hostNode.hostsNode.rootNode.locale, "BackupsNode.missingMobBackup")
					: accessor.getMessage(hostNode.hostsNode.rootNode.locale, "BackupsNode.backupsConfigured")
			;
			alertLevel = newAlertLevel;
			if(oldAlertLevel!=newAlertLevel) {
				hostNode.hostsNode.rootNode.nodeAlertLevelChanged(
					this,
					constrainAlertLevel(oldAlertLevel),
					constrainAlertLevel(newAlertLevel),
					newAlertLevelMessage
				);
			}

			// Remove old ones
			Iterator<BackupNode> backupNodeIter = backupNodes.iterator();
			while(backupNodeIter.hasNext()) {
				BackupNode backupNode = backupNodeIter.next();
				FileReplication failoverFileReplication = backupNode.getFailoverFileReplication();
				if(!failoverFileReplications.contains(failoverFileReplication)) {
					backupNode.removeTableResultListener(this);
					backupNode.stop();
					backupNodeIter.remove();
					hostNode.hostsNode.rootNode.nodeRemoved();
				}
			}
			// Add new ones
			for(int c=0;c<failoverFileReplications.size();c++) {
				FileReplication failoverFileReplication = failoverFileReplications.get(c);
				if(c>=backupNodes.size() || !failoverFileReplication.equals(backupNodes.get(c).getFailoverFileReplication())) {
					// Insert into proper index
					BackupNode backupNode = new BackupNode(this, failoverFileReplication, port, csf, ssf);
					backupNodes.add(c, backupNode);
					backupNode.start();
					hostNode.hostsNode.rootNode.nodeAdded();
					backupNode.addTableResultListener(this);
				}
			}

			// Update lastResult: failoverFileReplications and backupNodes are completely aligned currently
			final long latency = System.nanoTime() - startNanos;
			if(failoverFileReplications.isEmpty()) {
				newResult = new TableResult(
					startTime,
					latency,
					true,
					1,
					1,
					locale -> Collections.singletonList(accessor.getMessage(locale, "BackupsNode.columnHeaders.configurationError")),
					locale -> Collections.singletonList(accessor.getMessage(locale, "BackupsNode.noBackupsConfigured")),
					Collections.singletonList(linuxServer==null ? AlertLevel.NONE : AlertLevel.MEDIUM)
				);
			} else if(missingMobBackup) {
				newResult = new TableResult(
					startTime,
					latency,
					true,
					1,
					1,
					locale -> Collections.singletonList(accessor.getMessage(locale, "BackupsNode.columnHeaders.configurationError")),
					locale -> Collections.singletonList(accessor.getMessage(locale, "BackupsNode.missingMobBackup")),
					Collections.singletonList(AlertLevel.LOW)
				);
			} else {
				List<Object> tableData = new ArrayList<>(failoverFileReplications.size()*7);
				List<AlertLevel> alertLevels = new ArrayList<>(failoverFileReplications.size());
				for(int c=0;c<failoverFileReplications.size();c++) {
					FileReplication failoverFileReplication = failoverFileReplications.get(c);
					BackupPartition backupPartition = failoverFileReplication.getBackupPartition();
					tableData.add(backupPartition==null ? "null" : backupPartition.getLinuxServer().getHostname());
					tableData.add(backupPartition==null ? "null" : backupPartition.getPath());
					StringBuilder times = new StringBuilder();
					for(FileReplicationSchedule ffs : failoverFileReplication.getFailoverFileSchedules()) {
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
						tableData.add(accessor.getMessage(hostNode.hostsNode.rootNode.locale, "BackupsNode.bitRate.unlimited"));
					} else {
						tableData.add(accessor.getMessage(hostNode.hostsNode.rootNode.locale, "BackupsNode.bitRate", bitRate));
					}
					if(failoverFileReplication.getUseCompression()) {
						tableData.add(accessor.getMessage(hostNode.hostsNode.rootNode.locale, "BackupsNode.useCompression.true"));
					} else {
						tableData.add(accessor.getMessage(hostNode.hostsNode.rootNode.locale, "BackupsNode.useCompression.false"));
					}
					tableData.add(failoverFileReplication.getRetention().getDisplay());
					BackupNode backupNode = backupNodes.get(c);
					TableResult backupNodeResult = backupNode.getLastResult();
					if(backupNodeResult==null) {
						tableData.add("");
						alertLevels.add(AlertLevel.UNKNOWN);
					} else {
						AlertLevelAndMessage alertLevelAndMessage = backupNode.getAlertLevelAndMessage(backupNodeResult);
						Function<Locale,String> alertMessage = alertLevelAndMessage.getAlertMessage();
						tableData.add(alertMessage == null ? null : alertMessage.apply(hostNode.hostsNode.rootNode.locale));
						alertLevels.add(alertLevelAndMessage.getAlertLevel());
					}
				}
				newResult = new TableResult(
					startTime,
					latency,
					false,
					7,
					failoverFileReplications.size(),
					locale -> Arrays.asList(
						accessor.getMessage(locale, "BackupsNode.columnHeaders.to"),
						accessor.getMessage(locale, "BackupsNode.columnHeaders.path"),
						accessor.getMessage(locale, "BackupsNode.columnHeaders.scheduledTimes"),
						accessor.getMessage(locale, "BackupsNode.columnHeaders.maxBitRate"),
						accessor.getMessage(locale, "BackupsNode.columnHeaders.useCompression"),
						accessor.getMessage(locale, "BackupsNode.columnHeaders.retention"),
						accessor.getMessage(locale, "BackupsNode.columnHeaders.status")
					),
					locale -> tableData,
					alertLevels
				);
			}
			lastResult = newResult;
		}
		notifyTableResultUpdated(newResult);
	}

	File getPersistenceDirectory() throws IOException {
		File dir = new File(hostNode.getPersistenceDirectory(), "failover_file_replications");
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

	@Override
	final public void addTableResultListener(TableResultListener tableResultListener) {
		synchronized(tableResultListeners) {
			tableResultListeners.add(tableResultListener);
		}
	}

	@Override
	final public void removeTableResultListener(TableResultListener tableResultListener) {
		synchronized(tableResultListeners) {
			for(int c=tableResultListeners.size()-1;c>=0;c--) {
				if(tableResultListeners.get(c).equals(tableResultListener)) {
					tableResultListeners.remove(c);
					// Remove only once, in case add and remove come in out of order with quick GUI changes
					return;
				}
			}
		}
		logger.log(Level.WARNING, null, new AssertionError("Listener not found: " + tableResultListener));
	}

	/**
	 * Notifies all of the listeners.
	 */
	private void notifyTableResultUpdated(TableResult tableResult) {
		assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

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
