/*
 * noc-monitor-impl - Implementation of Network Operations Center Monitoring.
 * Copyright (C) 2008-2013, 2014, 2016, 2017, 2018, 2019, 2020, 2021  AO Industries, Inc.
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
 * along with noc-monitor-impl.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.aoserv.client.AOServConnector;
import com.aoindustries.aoserv.client.account.User;
import com.aoindustries.noc.monitor.common.Monitor;
import com.aoindustries.noc.monitor.common.RootNode;
import java.io.IOException;
import java.rmi.RemoteException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.UnicastRemoteObject;
import java.sql.SQLException;
import java.util.Locale;

/**
 * The main starting point for the monitor.
 *
 * TODO: DNS lookups via dnsjava, cycle through all DNS entries slowly
 *       ns1.aoindustries.com failed today (2019-09-13), so this is bumped up the list
 *       During this failure, TCP connections were stalled 15 seconds, then successful.
 *           So monitoring for connections taking over 10 seconds might also catch this failure mode.
 *
 * TODO: bind/named: rndc status
 *
 * TODO: Monitor PDU load levels
 *
 * TODO: Fan speed, CPU voltages, system/CPU temps (BMC) (Hardware Sensors)
 *
 * TODO: ECC reports
 *
 * TODO: Managed switches
 *
 * TODO: Watch /var/log/audit/audit.log for any SELinux denials or messages that indicate intrusion.
 *
 * TODO:
 *     Group servers by cluster and then by rack, can add power warning on rack node itself
 *        /cluster/
 *                /rack1
 *                /rack2
 *                /virtual
 *
 * TODO:
 *
 * monitor available entropy
 * monitor master stats, including shared entropy pool
 *
 * watch route scripts on gw1/gw2 pairs making sure consistent with each other
 * watchdog to detect failure between RMI client and server
 *     handle failed RMI
 * 3ware, verify last battery test interval, test at least once a year
 *     same for LSI (PDList look for Media Error Count: non-zero)
 * Monitor all syslog stuff for errors, split to separate logs for error, warning, info.  errors and higher to one file to monitor.
 *     watch for SMART status (May 20 04:42:31 xen907-5 smartd[3454]: Device: /dev/hde, 1 Offline uncorrectable sectors) in /var/log/messages
 *         smartd -q onecheck?
 *         smartctl?
 *     watch for procmail/spamc failures in: /var/log/mail/info
 *         Dec  8 19:25:10 www3 sendmail[1795]: mB88rS9R029955: to=\\jl, delay=16:31:40, xdelay=00:00:04, mailer=local, pri=6962581, dsn=4.0.0, stat=Deferred: local mailer (/usr/bin/procmail) exited with EX_TEMPFAIL
 * Watch the count of files in "/var/spool/aoserv/spamassassin" - a large number of files (not directories) indicates training failing.  Also watch parsed times.
 * mrtg auto-check and graphs
 * /proc/version against template
 * Other hardware (temps, fans, ...)
 *    reboot detections using uptime? (or last command)
 * DiskIO
 * port monitoring
 *     also enable port monitoring on all ports (including 127.0.0.1 via aoserv-daemon)
 *     update all code and procedures that adds net_binds to start them all as monitored
 *     maximum alert level based on account level?
 *     monitor the 0.0.0.0 ports on all IP addresses (including 127.0.0.1), minimize use of wildcard to reduce monitoring rate - or monitor on separate node for 0.0.0.0?
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
 *     Dell/LSI?
 * backups
 *   make sure backups not going to the same primary or secondary physical machine
 *   low priority if successful but scanned 0 (like no backups configured)
 * mysql
 *     replications
 *     mysql myisam corruption (check table ... quick fast)
 *     credit card scanner from aoserv-daemon
 * distro scans
 * software updates (could we auto-search for them)?
 * snapshot-backups space and timing (adpserver)
 * jilter state
 * netstat
 * aoserv daemon/master/website errors (and anything else that used to get emailed to aoserv@aoindustries.com address)
 * sendmail queues
 *     watch for files older than 7 days to help keep things clean - eventually delete outright?
 *     watch for growing - this is a sign of a problem
 * nmap - other tools that Mark Akins uses - nessus
 * domain registration expiration :)
 * apache (make sure no empty apache logs after rotate !)
 *
 * max latency setting on a per-server basis (or per netdevice) - or a hierachy server_farms, server, net_device, ip_address
 *
 * TODO: Log history and persist over time?  Simple files to disk?  Locking to prevent multiple JVM's corrupting?
 *       server_reports-style logging?
 *
 * Need to monitor log rotation success states (big log files built-up on keepandshare and awstats broken as a result)
 *
 * CPU
 *      configurable limits per alert level
 *      based on 5-minute averages, sampled every minute, will take up to 9 minutes to alert
 *
 * LVM:
 *   snapshot space
 *   vgck
 *
 * Monitor syslog for ECC errors (at least for i5000 module with most recent 2.6.18-92.1.10+ kernels).  See wiki page for xen917-5.fc.aoindustries.com
 *
 * Monitor all SSL certificates, ours and customers, could have on a single SSL
 * Certificates node per server, perhaps?  Read from filesystem or connect to port?
 *     HTTPS
 *     IMAPS/IMAP+TLS
 *     POP3S/POP3+TLS
 *     MySQL
 *     SMTPS/SMTP+TLS
 *     PostgreSQL
 *     Make part of port monitoring
 *
 * 3ware/BIOS firmware version monitoring?
 * LSI monitoring (MegaCli LdInfo/PdList, battery monitoring, too)?
 *
 * UPS Monitor:
 *      battery calibration once a year or when load is increased
 *      Perhaps just as a procedure - how to schedule in NOC interface?
 *
 * Open Resolvers: http://openresolverproject.org/
 *
 * Process Monitoring
 *      Any process linked to libraries or binaries that do not match current on-disk: This might mean a process needs restarted to have latest security updates.
 *
 * PostgreSQL and MySQL concurrency monitoring, too, like Apache instances
 *      Certain application pool sizes, too, such as Tomcat and custom applications (aoserv-master)?
 *
 * Tomcat Manager monitoring
 *    JMX
 *
 * Tomcat Log file monitoring (watch tomcat log files for key things like OutOfMemoryError and "Too many open files" (and translations?)
 * Watch the jvm_crashes.log file?
 *
 * Integrate NOC with Amazon Cloud Watch
 *
 * Monitor for certificates issued in domains that are not expected.
 *     Jonathon Moldenhaur described how there are lists of certificates issued.
 *
 * @author  AO Industries, Inc.
 */
public class MonitorImpl extends UnicastRemoteObject implements Monitor {

	private static final long serialVersionUID = 1L;

	private final int port;
	private final RMIClientSocketFactory csf;
	private final RMIServerSocketFactory ssf;

	public MonitorImpl(int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws RemoteException {
		super(port, csf, ssf);
		this.port = port;
		this.csf = csf;
		this.ssf = ssf;
	}

	@Override
	public RootNode login(Locale locale, User.Name username, String password) throws IOException, SQLException {
		AOServConnector connector = AOServConnector.getConnector(username, password);
		connector.testConnect();
		return RootNodeImpl.getRootNode(locale, connector, port, csf, ssf);
	}
}
