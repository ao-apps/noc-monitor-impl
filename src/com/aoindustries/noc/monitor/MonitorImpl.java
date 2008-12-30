/*
 * Copyright 2008 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.aoserv.client.AOServConnector;
import com.aoindustries.noc.common.Monitor;
import com.aoindustries.noc.common.RootNode;
import com.aoindustries.util.ErrorHandler;
import java.io.IOException;
import java.rmi.RemoteException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.UnicastRemoteObject;
import java.util.Locale;

/**
 * The main starting point for the monitor.
 *
 * TODO:
 *     Add warning about total power allocation per rack if max power known
 *
 * TODO:
 *     Group servers by cluster and then by rack, can add power warning on rack node itself
 *        /cluster/
 *                /rack1
 *                /rack2
 *                /virtual
 *
 * TODO:
 * watchdog
 * handle failed RMI
 * 3ware, verify last battery test interval
 * Monitor all syslog stuff for errors, split to separate logs for error, warning, info.  errors and higher to one file to monitor.
 *     watch for SMART status (May 20 04:42:31 xen907-5 smartd[3454]: Device: /dev/hde, 1 Offline uncorrectable sectors) in /var/log/messages
 *         smartd -q onecheck?
 *         smartctl?
 *     watch for procmail/spamc failures in: /var/log/mail/info
 *         Dec  8 19:25:10 www3 sendmail[1795]: mB88rS9R029955: to=\\jl, delay=16:31:40, xdelay=00:00:04, mailer=local, pri=6962581, dsn=4.0.0, stat=Deferred: local mailer (/usr/bin/procmail) exited with EX_TEMPFAIL
 * mrtg auto-check and graphs
 * /proc/version against template
 * Dell RAID
 *      http://support.dell.com/support/downloads/driverslist.aspx?os=RHEL5&osl=EN&catid=-1&impid=-1&servicetag=&SystemID=PWE_PNT_P3C_2850&hidos=WNET&hidlang=en
 *      OpenManage Server Administrator linux
 * Other hardware (temps, fans, ...)
 *    reboot detections using uptime? (or last command)
 * DiskIO
 * port monitoring
 * AOServ data integrity?
 * AOServ Daemon errors and warnings
 * DNS, forward and reverse (non-AO name servers)
 *     Also query each nameserver for all expected values slowly over time
 * kernel parameters (/proc/sys/...)
 *     shared memory segments
 * other stuff that used to be in server reports
 * smart monitoring?
 *     kernel?
 *     3ware?
 *     Dell?
 * backups
 *   make sure backups not going to the same primary or secondary physical machine
 *   low priority if successful but scanned 0 (like no backups configured)
 * mysql
 *     replications
 *     mysql myisam corruption (check table ... quick fast)
 *     credit card scanner from aoserv-daemon
 * distro scans
 * software updates (could we auto-search for them)?
 * snapshot-backups space
 * signups
 * mail folders close to 2 GB - this may not matter with cyrus
 * jilter state
 * RBLs
 * netstat
 * aoserv daemon/master/website errors (and anything else that used to get emailed to aoserv@aoindustries.com address)
 * sendmail queues
 *     watch for files older than 7 days to help keep things clean - eventually delete outright?
 * nmap - other tools that Mark Akins uses
 * domain registration expiration :)
 * postgresql, mysql, apache (make sure no empty apache logs after rotate !)
 *
 * max latency setting on a per-server basis (or per netdevice) - or a hierachy server_farms, server, net_device, ip_address
 *
 * TODO: Log history and persist over time?  Simple files to disk?  Locking to prevent multiple JVM's corrupting?
 *       server_reports-style logging?
 *
 * CPU
 *      configurable limits per alert level
 *      based on 5-minute averages, sampled every minute, will take up to 9 minutes to alert
 * 
 * LVM:
 *   snapshot space
 *   vgck
 * 
 * Monitor syslog for ECC errors (at least for i5000 module).  See wiki page for xen917-5.fc.aoindustries.com
 * 
 * Monitor all SSL certificates, ours and customers, could have on a single SSL
 * Certificates node per server, perhaps?  Read from filesystem or connect to port?
 *     HTTPS
 *     IMAPS/IMAP+TLS
 *     POP3S/POP3+TLS
 *     MySQL
 *     SMTPS/SMTP+TLS
 *     PostgreSQL
 * Monitor accessibility to all NTP_SERVERS (in route scripts) to pool.ntp.org
 *     At least look for secondary effect of clock skew
 *
 * @author  AO Industries, Inc.
 */
public class MonitorImpl extends UnicastRemoteObject implements Monitor {

    final private ErrorHandler errorHandler;
    final private int port;
    final private RMIClientSocketFactory csf;
    final private RMIServerSocketFactory ssf;

    public MonitorImpl(ErrorHandler errorHandler, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws RemoteException {
        super(port, csf, ssf);
        this.errorHandler = errorHandler;
        this.port = port;
        this.csf = csf;
        this.ssf = ssf;
    }

    @Override
    public RootNode login(Locale locale, String username, String password) throws IOException {
        AOServConnector connector=AOServConnector.getConnector(username, password, errorHandler);
        connector.testConnect();
        return RootNodeImpl.getRootNode(locale, connector, port, csf, ssf);
    }
}
