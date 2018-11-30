/*
 * Copyright 2008, 2009, 2016, 2018 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.aoserv.client.backup.BackupPartition;
import com.aoindustries.aoserv.client.backup.FailoverFileReplication;
import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.TableResult;
import java.io.File;
import java.io.IOException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.sql.SQLException;

/**
 * The node for the backup monitoring.
 *
 * @author  AO Industries, Inc.
 */
public class BackupNode extends TableResultNodeImpl {

	private static final long serialVersionUID = 1L;

	final private FailoverFileReplication failoverFileReplication;
	final private String label;

	BackupNode(BackupsNode backupsNode, FailoverFileReplication failoverFileReplication, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws IOException, SQLException {
		super(
			backupsNode.serverNode.serversNode.rootNode,
			backupsNode,
			BackupNodeWorker.getWorker(
				new File(backupsNode.getPersistenceDirectory(), Integer.toString(failoverFileReplication.getPkey())),
				failoverFileReplication
			),
			port,
			csf,
			ssf
		);
		this.failoverFileReplication = failoverFileReplication;
		BackupPartition backupPartition = failoverFileReplication.getBackupPartition();
		this.label = accessor.getMessage(
			rootNode.locale,
			"BackupNode.label",
			backupPartition==null ? "null" : backupPartition.getAOServer().getHostname(),
			backupPartition==null ? "null" : backupPartition.getPath()
		);
	}

	FailoverFileReplication getFailoverFileReplication() {
		return failoverFileReplication;
	}

	@Override
	public String getLabel() {
		return label;
	}

	AlertLevelAndMessage getAlertLevelAndMessage(TableResult result) {
		AlertLevel curAlertLevel = worker.getAlertLevel();
		if(curAlertLevel == null) curAlertLevel = AlertLevel.NONE;
		return worker.getAlertLevelAndMessage(
			curAlertLevel,
			result
		);
	}
}
