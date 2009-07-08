/*
 * Copyright 2008-2009 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.aoserv.client.BackupPartition;
import com.aoindustries.aoserv.client.FailoverFileReplication;
import com.aoindustries.noc.common.TableResult;
import java.io.File;
import java.io.IOException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.sql.SQLException;
import java.util.Locale;

/**
 * The node for the backup monitoring.
 *
 * @author  AO Industries, Inc.
 */
public class BackupNode extends TableResultNodeImpl {

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
        this.label = ApplicationResourcesAccessor.getMessage(
            rootNode.locale,
            "BackupNode.label",
            backupPartition.getAOServer().getHostname(),
            backupPartition.getPath()
        );
    }

    FailoverFileReplication getFailoverFileReplication() {
        return failoverFileReplication;
    }

    @Override
    public String getLabel() {
        return label;
    }
    
    AlertLevelAndMessage getAlertLevelAndMessage(Locale locale, TableResult result) {
        return worker.getAlertLevelAndMessage(locale, result);
    }
}
