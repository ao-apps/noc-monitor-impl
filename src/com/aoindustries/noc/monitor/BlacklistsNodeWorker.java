/*
 * Copyright 2009-2013, 2014 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.aoserv.client.AOServer;
import com.aoindustries.aoserv.client.IPAddress;
import com.aoindustries.aoserv.client.validator.DomainName;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.TableResult;
import com.aoindustries.noc.monitor.common.TimeWithTimeZone;
import com.aoindustries.sql.NanoInterval;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.xbill.DNS.ARecord;
import org.xbill.DNS.Cache;
import org.xbill.DNS.DClass;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.MXRecord;
import org.xbill.DNS.Name;
import org.xbill.DNS.Record;
import org.xbill.DNS.Resolver;
import org.xbill.DNS.Type;

/**
 * The workers for blacklist monitoring.
 *
 * TODO: There are several whitelists tested at multirbl.valli.org
 * TODO: There are also informational lists at multirbl.valli.org
 * TODO: Also at multirbl.valli.org there are hostname-based blacklists...
 * TODO: yahoo, hotmail, gmail, aol?
 * TODO: How to check when rejected by domain name on sender address like done for NMW on att domains?
 *
 * @author  AO Industries, Inc.
 */
class BlacklistsNodeWorker extends TableResultNodeWorker<List<BlacklistsNodeWorker.BlacklistQueryResult>,Object> {

	private static final Logger logger = Logger.getLogger(BlacklistsNodeWorker.class.getName());

	/**
	 * The query timeout in milliseconds, allows for time in the queue waiting for resolver.
	 */
	private static final long TIMEOUT = 60L*1000L; // Was 15 minutes

	/**
	 * The resolver timeout in seconds.
	 */
	private static final int RESOLVER_TIMEOUT_SECONDS = 5;

	/**
	 * The number of milliseconds to wait before trying a timed-out lookup.
	 * This does not apply to a queue time-out, only an actual loookup timeout.
	 */
	private static final long UNKNOWN_RETRY = 60L*60L*1000L; // Was 15 minutes

	/**
	 * The number of milliseconds to wait before looking up a previously good result.
	 */
	private static final long GOOD_RETRY = 24L*60L*60L*1000L;

	/**
	 * The number of milliseconds to wait before looking up a previously bad result.
	 */
	private static final long BAD_RETRY = 60L*60L*1000L;

	/**
	 * The maximum number of threads.
	 */
	private static final int NUM_THREADS = 32; // Was 8; // Was 16; // Was 32;

	/**
	 * The delay between submitting each task in milliseconds.
	 */
	private static final int TASK_DELAY = 100;

	static class BlacklistQueryResult {

		final String basename;
		final long queryTime;
		final long latency;
		final String query;
		final String result;
		final AlertLevel alertLevel;

		BlacklistQueryResult(String basename, long queryTime, long latency, String query, String result, AlertLevel alertLevel) {
			this.basename = basename;
			this.queryTime = queryTime;
			this.latency = latency;
			this.query = query;
			this.result = result;
			this.alertLevel = alertLevel;
		}
	}

	/**
	 * This cache is used for all DNS lookups.
	 */
	static {
		Cache inCache = Lookup.getDefaultCache(DClass.IN);
		inCache.setMaxEntries(-1);
		inCache.setMaxCache(300);
		inCache.setMaxNCache(300);
		Resolver resolver = Lookup.getDefaultResolver();
		resolver.setTimeout(RESOLVER_TIMEOUT_SECONDS);
		if(logger.isLoggable(Level.FINE)) {
			logger.log(Level.FINE, "maxCache={0}", inCache.getMaxCache());
			logger.log(Level.FINE, "maxEntries={0}", inCache.getMaxEntries());
			logger.log(Level.FINE, "maxNCache={0}", inCache.getMaxNCache());
		}
	}

	abstract class BlacklistLookup implements Comparable<BlacklistLookup>, Callable<BlacklistQueryResult> {

		@Override
		final public int compareTo(BlacklistLookup o) {
			return DomainName.compareLabels(getBaseName(), o.getBaseName());
		}

		abstract String getBaseName();
		abstract String getQuery();
		abstract AlertLevel getMaxAlertLevel();
	}

	class RblBlacklist extends BlacklistLookup {

		final String basename;
		final AlertLevel maxAlertLevel;
		final String query;

		RblBlacklist(String basename) {
			this(basename, AlertLevel.LOW);
		}

		RblBlacklist(String basename, AlertLevel maxAlertLevel) {
			this.basename = basename;
			this.maxAlertLevel = maxAlertLevel;
			com.aoindustries.aoserv.client.validator.InetAddress ip = ipAddress.getExternalIpAddress();
			if(ip==null) ip = ipAddress.getInetAddress();
			if(ip.isIPv6()) throw new UnsupportedOperationException("IPv6 not yet implemented");
			int bits = IPAddress.getIntForIPAddress(ip.toString());
			this.query =
				new StringBuilder(16+basename.length())
				.append(bits&255)
				.append('.')
				.append((bits>>>8)&255)
				.append('.')
				.append((bits>>>16)&255)
				.append('.')
				.append((bits>>>24)&255)
				.append('.')
				.append(basename)
				.append('.')
				.toString()
			;
		}

		@Override
		public BlacklistQueryResult call() throws Exception {
			long startTime = System.currentTimeMillis();
			long startNanos = System.nanoTime();
			String result;
			AlertLevel alertLevel;
			// Lookup the IP addresses
			Lookup aLookup = new Lookup(query, Type.A);
			aLookup.run();
			if(aLookup.getResult()==Lookup.HOST_NOT_FOUND) {
				// Not blacklisted
				result = "Host not found";
				alertLevel = AlertLevel.NONE;
			} else if(aLookup.getResult()==Lookup.TYPE_NOT_FOUND) {
				// Not blacklisted
				result = "Type not found";
				alertLevel = AlertLevel.NONE;
			} else if(aLookup.getResult()!=Lookup.SUCCESSFUL) {
				String errorString = aLookup.getErrorString();
				switch (errorString) {
					case "SERVFAIL":
						// Not blacklisted
						result = "SERVFAIL";
						alertLevel = AlertLevel.NONE;
						break;
					case "timed out":
						result = "Timeout";
						alertLevel = AlertLevel.UNKNOWN;
						break;
					default:
						result = "A lookup failed: "+errorString;
						alertLevel = maxAlertLevel;
						break;
				}
			} else {
				Record[] aRecords = aLookup.getAnswers();
				// Pick a random A
				if(aRecords.length==0) {
					result = "No A records found";
					alertLevel = maxAlertLevel;
				} else {
					ARecord a;
					if(aRecords.length==1) a = (ARecord)aRecords[0];
					else a = (ARecord)aRecords[RootNodeImpl.random.nextInt(aRecords.length)];
					String ip = a.getAddress().getHostAddress();
					// Try TXT record
					Lookup txtLookup = new Lookup(query, Type.TXT);
					txtLookup.run();
					if(txtLookup.getResult()==Lookup.SUCCESSFUL) {
						Record[] answers = txtLookup.getAnswers();
						if(answers.length>0) {
							StringBuilder SB = new StringBuilder(ip);
							for(Record record : answers) SB.append(" - ").append(record.rdataToString());
							result = SB.toString();
						} else {
							result = ip.intern();
						}
					} else {
						result = ip.intern();
					}
					alertLevel = maxAlertLevel;
				}
			}
			return new BlacklistQueryResult(basename, startTime, System.nanoTime() - startNanos, query, result, alertLevel);
		}

		@Override
		String getBaseName() {
			return basename;
		}

		@Override
		AlertLevel getMaxAlertLevel() {
			return maxAlertLevel;
		}

		@Override
		String getQuery() {
			return query;
		}
	}

	class SmtpBlacklist extends BlacklistLookup {

		private final String domain;
		private final AlertLevel maxAlertLevel;
		private final String unknownQuery;

		SmtpBlacklist(String domain) {
			this(domain, AlertLevel.LOW);
		}

		SmtpBlacklist(String domain, AlertLevel maxAlertLevel) {
			this.domain = domain;
			this.maxAlertLevel = maxAlertLevel;
			unknownQuery = "N/A";
		}

		@Override
		String getBaseName() {
			return domain;
		}

		@Override
		AlertLevel getMaxAlertLevel() {
			return maxAlertLevel;
		}

		@Override
		public BlacklistQueryResult call() throws Exception {
			long startTime = System.currentTimeMillis();
			long startNanos = System.nanoTime();
			// Lookup the MX for the domain
			Lookup mxLookup = new Lookup(domain+'.', Type.MX);
			mxLookup.run();
			if(mxLookup.getResult()!=Lookup.SUCCESSFUL) throw new IOException(domain+": MX lookup failed: "+mxLookup.getErrorString());
			Record[] mxRecords = mxLookup.getAnswers();
			// Pick a random MX
			if(mxRecords.length==0) throw new IOException(domain+": No MX records found");
			MXRecord mx;
			if(mxRecords.length==1) mx = (MXRecord)mxRecords[0];
			else mx = (MXRecord)mxRecords[RootNodeImpl.random.nextInt(mxRecords.length)];
			// Lookup the IP addresses
			Name target = mx.getTarget();
			Lookup aLookup = new Lookup(target, Type.A);
			aLookup.run();
			if(aLookup.getResult()!=Lookup.SUCCESSFUL) throw new IOException(domain+": A lookup failed: "+aLookup.getErrorString());
			Record[] aRecords = aLookup.getAnswers();
			// Pick a random A
			if(aRecords.length==0) throw new IOException(domain+": No A records found");
			ARecord a;
			if(aRecords.length==1) a = (ARecord)aRecords[0];
			else a = (ARecord)aRecords[RootNodeImpl.random.nextInt(aRecords.length)];
			InetAddress address = a.getAddress();
			// Make call from the daemon from privileged port
			AOServer aoServer = ipAddress.getNetDevice().getServer().getAOServer();
			if(aoServer==null) throw new SQLException(ipAddress+": AOServer not found");
			String addressIp = address.getHostAddress();
			String statusLine = aoServer.checkSmtpBlacklist(ipAddress.getInetAddress(), addressIp);
			// Return results
			long endNanos = System.nanoTime();
			AlertLevel alertLevel;
			if(statusLine.startsWith("220 ")) alertLevel = AlertLevel.NONE;
			else alertLevel = maxAlertLevel;
			return new BlacklistQueryResult(domain, startTime, endNanos-startNanos, addressIp, statusLine, alertLevel);
		}

		@Override
		String getQuery() {
			return unknownQuery;
		}
	}

	/**
	 * One unique worker is made per persistence file (and should match the ipAddress exactly)
	 */
	private static final Map<String, BlacklistsNodeWorker> workerCache = new HashMap<>();
	static BlacklistsNodeWorker getWorker(File persistenceFile, IPAddress ipAddress) throws IOException, SQLException {
		String path = persistenceFile.getCanonicalPath();
		synchronized(workerCache) {
			BlacklistsNodeWorker worker = workerCache.get(path);
			if(worker==null) {
				worker = new BlacklistsNodeWorker(persistenceFile, ipAddress);
				workerCache.put(path, worker);
			} else {
				if(!worker.ipAddress.equals(ipAddress)) throw new AssertionError("worker.ipAddress!=ipAddress: "+worker.ipAddress+"!="+ipAddress);
			}
			return worker;
		}
	}

	// Will use whichever connector first created this worker, even if other accounts connect later.
	final private IPAddress ipAddress;
	final private List<BlacklistLookup> lookups;

	private static RblBlacklist[] addUnique(RblBlacklist ... blacklists) {
		Map<String,RblBlacklist> unique = new HashMap<>(blacklists.length*4/3+1);
		for(RblBlacklist blacklist : blacklists) {
			String basename = blacklist.getBaseName();
			RblBlacklist existing = unique.get(basename);
			if(existing == null) {
				unique.put(basename, blacklist);
			} else {
				// When two exist, must have the same max alert level
				if(blacklist.getMaxAlertLevel() != existing.getMaxAlertLevel()) {
					throw new AssertionError("Blacklist max alert level mismatch: " + basename);
				}
			}
		}
		return unique.values().toArray(new RblBlacklist[unique.size()]);
	}

	BlacklistsNodeWorker(File persistenceFile, IPAddress ipAddress) throws IOException, SQLException {
		super(persistenceFile);
		this.ipAddress = ipAddress;
		// Build the list of lookups
		RblBlacklist[] rblBlacklists = addUnique(
			// <editor-fold desc="cqcounter.com" defaultstate="collapsed">
			// From http://cqcounter.com/rbl_check/ on 2014-02-09
			new RblBlacklist("access.redhawk.org"),
			new RblBlacklist("assholes.madscience.nl"),
			new RblBlacklist("badconf.rhsbl.sorbs.net"),
			new RblBlacklist("bl.deadbeef.com"),
			new RblBlacklist("bl.spamcannibal.org"),
			new RblBlacklist("bl.technovision.dk"),
			new RblBlacklist("blackholes.five-ten-sg.com"),
			new RblBlacklist("blackholes.intersil.net"),
			new RblBlacklist("blackholes.mail-abuse.org"),
			new RblBlacklist("blackholes.sandes.dk"),
			new RblBlacklist("blacklist.sci.kun.nl"),
			new RblBlacklist("blacklist.spambag.org", AlertLevel.NONE),
			new RblBlacklist("block.dnsbl.sorbs.net"),
			new RblBlacklist("blocked.hilli.dk"),
			new RblBlacklist("cart00ney.surriel.com"),
			new RblBlacklist("cbl.abuseat.org"),
			new RblBlacklist("dev.null.dk"),
			new RblBlacklist("dialup.blacklist.jippg.org"),
			new RblBlacklist("dialups.mail-abuse.org"),
			new RblBlacklist("dialups.visi.com"),
			new RblBlacklist("dnsbl-1.uceprotect.net"),
			new RblBlacklist("dnsbl-2.uceprotect.net"),
			new RblBlacklist("dnsbl-3.uceprotect.net"),
			new RblBlacklist("dnsbl.ahbl.org"),
			new RblBlacklist("dnsbl.antispam.or.id"),
			new RblBlacklist("dnsbl.cyberlogic.net"),
			new RblBlacklist("dnsbl.inps.de"),
			new RblBlacklist("uribl.swinog.ch"),
			new RblBlacklist("dnsbl.kempt.net"),
			new RblBlacklist("dnsbl.njabl.org"),
			new RblBlacklist("dnsbl.sorbs.net"),
			new RblBlacklist("list.dsbl.org"),
			new RblBlacklist("multihop.dsbl.org"),
			new RblBlacklist("unconfirmed.dsbl.org"),
			new RblBlacklist("dsbl.dnsbl.net.au"),
			new RblBlacklist("duinv.aupads.org"),
			new RblBlacklist("dul.dnsbl.sorbs.net"),
			new RblBlacklist("dul.maps.vix.com"),
			new RblBlacklist("dul.orca.bc.ca"),
			new RblBlacklist("dul.ru"),
			new RblBlacklist("dun.dnsrbl.net"),
			new RblBlacklist("fl.chickenboner.biz"),
			new RblBlacklist("forbidden.icm.edu.pl"),
			new RblBlacklist("hil.habeas.com"),
			new RblBlacklist("http.dnsbl.sorbs.net"),
			new RblBlacklist("images.rbl.msrbl.net"),
			new RblBlacklist("intruders.docs.uu.se"),
			new RblBlacklist("ix.dnsbl.manitu.net"),
			new RblBlacklist("korea.services.net"),
			new RblBlacklist("l1.spews.dnsbl.sorbs.net"),
			new RblBlacklist("l2.spews.dnsbl.sorbs.net"),
			new RblBlacklist("mail-abuse.blacklist.jippg.org"),
			new RblBlacklist("map.spam-rbl.com"),
			new RblBlacklist("misc.dnsbl.sorbs.net"),
			new RblBlacklist("msgid.bl.gweep.ca"),
			new RblBlacklist("combined.rbl.msrbl.net"),
			new RblBlacklist("no-more-funn.moensted.dk"),
			new RblBlacklist("nomail.rhsbl.sorbs.net"),
			new RblBlacklist("ohps.dnsbl.net.au"),
			// Disabled 2012-02-07: new RblBlacklist("okrelays.nthelp.com"),
			new RblBlacklist("omrs.dnsbl.net.au"),
			new RblBlacklist("orid.dnsbl.net.au"),
			new RblBlacklist("orvedb.aupads.org"),
			new RblBlacklist("osps.dnsbl.net.au"),
			new RblBlacklist("osrs.dnsbl.net.au"),
			new RblBlacklist("owfs.dnsbl.net.au"),
			new RblBlacklist("owps.dnsbl.net.au"),
			new RblBlacklist("pdl.dnsbl.net.au"),
			new RblBlacklist("phishing.rbl.msrbl.net"),
			new RblBlacklist("probes.dnsbl.net.au"),
			new RblBlacklist("proxy.bl.gweep.ca"),
			new RblBlacklist("psbl.surriel.com"),
			new RblBlacklist("pss.spambusters.org.ar"),
			new RblBlacklist("rbl-plus.mail-abuse.org"),
			// Shutdown on 2009-11-11: new RblBlacklist("rbl.cluecentral.net"),
			new RblBlacklist("rbl.efnetrbl.org"),
			new RblBlacklist("rbl.jp"),
			new RblBlacklist("rbl.maps.vix.com"),
			new RblBlacklist("rbl.schulte.org"),
			new RblBlacklist("rbl.snark.net"),
			new RblBlacklist("rbl.triumf.ca"),
			new RblBlacklist("rdts.dnsbl.net.au"),
			new RblBlacklist("relays.bl.gweep.ca"),
			new RblBlacklist("relays.bl.kundenserver.de"),
			new RblBlacklist("relays.mail-abuse.org"),
			new RblBlacklist("relays.nether.net"),
			// Disabled 2012-02-07: new RblBlacklist("relays.nthelp.com"),
			new RblBlacklist("rhsbl.sorbs.net"),
			new RblBlacklist("ricn.dnsbl.net.au"),
			new RblBlacklist("rmst.dnsbl.net.au"),
			new RblBlacklist("rsbl.aupads.org"),
			// Shutdown on 2009-11-11: new RblBlacklist("satos.rbl.cluecentral.net"),
			new RblBlacklist("sbl-xbl.spamhaus.org"),
			new RblBlacklist("sbl.spamhaus.org"),
			new RblBlacklist("smtp.dnsbl.sorbs.net"),
			new RblBlacklist("socks.dnsbl.sorbs.net"),
			new RblBlacklist("sorbs.dnsbl.net.au"),
			new RblBlacklist("spam.dnsbl.sorbs.net"),
			new RblBlacklist("spam.dnsrbl.net"),
			new RblBlacklist("spam.olsentech.net"),
			new RblBlacklist("spam.wytnij.to"),
			new RblBlacklist("bl.spamcop.net"),
			new RblBlacklist("spamguard.leadmon.net"),
			new RblBlacklist("spamsites.dnsbl.net.au"),
			// Shutdown 2012-02-07: new RblBlacklist("spamsources.dnsbl.info"),
			new RblBlacklist("spamsources.fabel.dk"),
			new RblBlacklist("spews.dnsbl.net.au"),
			new RblBlacklist("t1.dnsbl.net.au"),
			new RblBlacklist("tor.dan.me.uk"),
			new RblBlacklist("torexit.dan.me.uk"),
			new RblBlacklist("ucepn.dnsbl.net.au"),
			new RblBlacklist("virbl.dnsbl.bit.nl"),
			new RblBlacklist("virus.rbl.msrbl.net"),
			new RblBlacklist("virus.rbl.jp"),
			new RblBlacklist("web.dnsbl.sorbs.net"),
			new RblBlacklist("whois.rfc-ignorant.org"),
			// Removed 2014-06-26: new RblBlacklist("will-spam-for-food.eu.org"),
			new RblBlacklist("xbl.spamhaus.org"),
			new RblBlacklist("zombie.dnsbl.sorbs.net"),
			// </editor-fold>

			// <editor-fold desc="robtex.com" defaultstate="collapsed">
			// From https://www.robtex.com/ip/66.160.183.2.html#blacklists on 2014-02-09
			// Not including "timeout", "servfail", "not whitelisted" sections.
			// Only including "green" section
			// Could add whitelists here
			new RblBlacklist("access.redhawk.org"),
			new RblBlacklist("all.spamrats.com"),
			new RblBlacklist("assholes.madscience.nl"),
			new RblBlacklist("b.barracudacentral.org"),
			// Removed 2014-02-09: new RblBlacklist("bl.csma.biz"),
			new RblBlacklist("bl.spamcannibal.org"),
			new RblBlacklist("bl.technovision.dk"),
			// Removed 2014-02-09: new RblBlacklist("black.uribl.com"),
			new RblBlacklist("blackholes.five-ten-sg.com"),
			new RblBlacklist("blackholes.intersil.net"),
			new RblBlacklist("blackholes.mail-abuse.org"),
			new RblBlacklist("blackholes.sandes.dk"),
			new RblBlacklist("blacklist.sci.kun.nl"),
			new RblBlacklist("block.dnsbl.sorbs.net"),
			new RblBlacklist("blocked.hilli.dk"),
			// Removed 2014-02-09: new RblBlacklist("blocklist.squawk.com"),
			// Removed 2014-02-09: new RblBlacklist("blocklist2.squawk.com"),
			new RblBlacklist("cart00ney.surriel.com"),
			new RblBlacklist("cbl.abuseat.org"),
			new RblBlacklist("cblless.anti-spam.org.cn"),
			new RblBlacklist("cblplus.anti-spam.org.cn"),
			new RblBlacklist("cdl.anti-spam.org.cn"),
			new RblBlacklist("combined.abuse.ch"),
			new RblBlacklist("dev.null.dk"),
			new RblBlacklist("dialup.blacklist.jippg.org"),
			new RblBlacklist("dialups.mail-abuse.org"),
			new RblBlacklist("dialups.visi.com"),
			new RblBlacklist("dnsbl-1.uceprotect.net"),
			new RblBlacklist("dnsbl-2.uceprotect.net"),
			new RblBlacklist("dnsbl-3.uceprotect.net"),
			new RblBlacklist("dnsbl.abuse.ch"),
			new RblBlacklist("dnsbl.ahbl.org"),
			new RblBlacklist("dnsbl.antispam.or.id"),
			new RblBlacklist("dnsbl.dronebl.org"),
			new RblBlacklist("dnsbl.inps.de"),
			new RblBlacklist("dnsbl.kempt.net"),
			new RblBlacklist("dnsbl.njabl.org"),
			new RblBlacklist("dnsbl.mags.net"),
			new RblBlacklist("dnsbl.sorbs.net"),
			new RblBlacklist("drone.abuse.ch"),
			new RblBlacklist("dsbl.dnsbl.net.au"),
			new RblBlacklist("duinv.aupads.org"),
			new RblBlacklist("dul.dnsbl.sorbs.net"),
			new RblBlacklist("dul.orca.bc.ca"),
			new RblBlacklist("dul.ru"),
			new RblBlacklist("dynablock.njabl.org"),
			// Removed 2014-02-09: new RblBlacklist("fl.chickenboner.biz"),
			new RblBlacklist("forbidden.icm.edu.pl"),
			// Removed 2014-02-09: new RblBlacklist("grey.uribl.com"),
			new RblBlacklist("hil.habeas.com"),
			new RblBlacklist("http.dnsbl.sorbs.net"),
			// Not a useful basename: new RblBlacklist("images-msrbls"),
			new RblBlacklist("intruders.docs.uu.se"),
			new RblBlacklist("ips.backscatterer.org"),
			new RblBlacklist("ix.dnsbl.manitu.net"),
			new RblBlacklist("korea.services.net"),
			new RblBlacklist("l1.spews.dnsbl.sorbs.net"),
			new RblBlacklist("l2.spews.dnsbl.sorbs.net"),
			new RblBlacklist("list.quorum.to"),
			new RblBlacklist("mail-abuse.blacklist.jippg.org"),
			// Removed 2014-02-09: new RblBlacklist("map.spam-rbl.com"),
			new RblBlacklist("misc.dnsbl.sorbs.net"),
			new RblBlacklist("msgid.bl.gweep.ca"),
			// Not a useful basename: new RblBlacklist("msrbl"),
			new RblBlacklist("multi.surbl.org"),
			// Removed 2014-02-09: new RblBlacklist("multi.uribl.com"),
			new RblBlacklist("netblock.pedantic.org"),
			new RblBlacklist("no-more-funn.moensted.dk"),
			new RblBlacklist("noptr.spamrats.com"),
			new RblBlacklist("ohps.dnsbl.net.au"),
			// Disabled 2012-02-07: new RblBlacklist("okrelays.nthelp.com"),
			new RblBlacklist("omrs.dnsbl.net.au"),
			new RblBlacklist("opm.blitzed.org"),
			new RblBlacklist("opm.tornevall.org"),
			new RblBlacklist("orid.dnsbl.net.au"),
			new RblBlacklist("orvedb.aupads.org"),
			new RblBlacklist("osps.dnsbl.net.au"),
			new RblBlacklist("osrs.dnsbl.net.au"),
			new RblBlacklist("owfs.dnsbl.net.au"),
			new RblBlacklist("owps.dnsbl.net.au"),
			new RblBlacklist("pbl.spamhaus.org"),
			new RblBlacklist("pdl.dnsbl.net.au"),
			// Not a useful basename: new RblBlacklist("phishing-msrbl"),
			new RblBlacklist("probes.dnsbl.net.au"),
			new RblBlacklist("problems.dnsbl.sorbs.net"),
			// Not a useful basename: new RblBlacklist("Project Honeypot"),
			new RblBlacklist("proxy.bl.gweep.ca"),
			new RblBlacklist("psbl.surriel.com"),
			new RblBlacklist("pss.spambusters.org.ar"),
			// Shutdown on 2009-11-11: new RblBlacklist("rbl.cluecentral.net"),
			new RblBlacklist("rbl.efnetrbl.org"),
			new RblBlacklist("rbl.jp"),
			// Removed 2014-02-09: new RblBlacklist("rbl.maps.vix.com"),
			new RblBlacklist("rbl.schulte.org"),
			new RblBlacklist("rbl.snark.net"),
			// Removed 2014-02-09: new RblBlacklist("rbl.triumf.ca"),
			new RblBlacklist("rbl-plus.mail-abuse.org"),
			new RblBlacklist("rdts.dnsbl.net.au"),
			// Removed 2014-02-09: new RblBlacklist("red.uribl.com"),
			new RblBlacklist("relays.bl.gweep.ca"),
			new RblBlacklist("relays.bl.kundenserver.de"),
			new RblBlacklist("relays.mail-abuse.org"),
			new RblBlacklist("relays.nether.net"),
			// Disabled 2012-02-07: new RblBlacklist("relays.nthelp.com"),
			new RblBlacklist("rhsbl.sorbs.net"),
			new RblBlacklist("ricn.dnsbl.net.au"),
			new RblBlacklist("rmst.dnsbl.net.au"),
			new RblBlacklist("rsbl.aupads.org"),
			new RblBlacklist("safe.dnsbl.sorbs.net"),
			// Shutdown on 2009-11-11: new RblBlacklist("satos.rbl.cluecentral.net"),
			new RblBlacklist("sbl-xbl.spamhaus.org"),
			// Removed 2014-02-09: new RblBlacklist("sbl.csma.biz"),
			new RblBlacklist("sbl.spamhaus.org"),
			new RblBlacklist("smtp.dnsbl.sorbs.net"),
			new RblBlacklist("socks.dnsbl.sorbs.net"),
			new RblBlacklist("sorbs.dnsbl.net.au"),
			new RblBlacklist("spam.abuse.ch"),
			new RblBlacklist("spam.dnsbl.sorbs.net"),
			new RblBlacklist("spam.olsentech.net"),
			new RblBlacklist("spam.pedantic.org"),
			// Removed 2014-02-09: new RblBlacklist("spam.wytnij.to"),
			// Not a useful basename: new RblBlacklist("spamcop"),
			new RblBlacklist("spamguard.leadmon.net"),
			new RblBlacklist("spamsites.dnsbl.net.au"),
			new RblBlacklist("spamsources.fabel.dk"),
			new RblBlacklist("spews.dnsbl.net.au"),
			new RblBlacklist("t1.dnsbl.net.au"),
			new RblBlacklist("tor.dan.me.uk"),
			new RblBlacklist("torexit.dan.me.uk"),
			new RblBlacklist("ucepn.dnsbl.net.au"),
			new RblBlacklist("uribl.swinog.ch"),
			// Not a useful basename: new RblBlacklist("virbl"),
			// Not a useful basename: new RblBlacklist("virus-msrbl"),
			new RblBlacklist("virus.rbl.jp"),
			new RblBlacklist("web.dnsbl.sorbs.net"),
			// Removed 2014-02-09: new RblBlacklist("will-spam-for-food.eu.org"),
			new RblBlacklist("xbl.spamhaus.org"),
			new RblBlacklist("zen.spamhaus.org"),
			new RblBlacklist("zombie.dnsbl.sorbs.net"),
			// </editor-fold>

			// <editor-fold desc="anti-abuse.org" defaultstate="collapsed">
			// From www.anti-abuse.org on 2009-11-06
			new RblBlacklist("bl.spamcop.net"),
			new RblBlacklist("cbl.abuseat.org"),
			new RblBlacklist("b.barracudacentral.org"),
			new RblBlacklist("dnsbl.sorbs.net"),
			new RblBlacklist("http.dnsbl.sorbs.net"),
			new RblBlacklist("dul.dnsbl.sorbs.net"),
			new RblBlacklist("misc.dnsbl.sorbs.net"),
			new RblBlacklist("smtp.dnsbl.sorbs.net"),
			new RblBlacklist("socks.dnsbl.sorbs.net"),
			new RblBlacklist("spam.dnsbl.sorbs.net"),
			new RblBlacklist("web.dnsbl.sorbs.net"),
			new RblBlacklist("zombie.dnsbl.sorbs.net"),
			new RblBlacklist("dnsbl-1.uceprotect.net"),
			new RblBlacklist("dnsbl-2.uceprotect.net"),
			new RblBlacklist("dnsbl-3.uceprotect.net"),
			new RblBlacklist("pbl.spamhaus.org"),
			new RblBlacklist("sbl.spamhaus.org"),
			new RblBlacklist("xbl.spamhaus.org"),
			new RblBlacklist("zen.spamhaus.org"),
			// Removed 2014-02-09: new RblBlacklist("images.rbl.msrbl.net"),
			// Removed 2014-02-09: new RblBlacklist("phishing.rbl.msrbl.net"),
			// Removed 2014-02-09: new RblBlacklist("combined.rbl.msrbl.net"),
			// Removed 2014-02-09: new RblBlacklist("spam.rbl.msrbl.net"),
			// Removed 2014-02-09: new RblBlacklist("virus.rbl.msrbl.net"),
			new RblBlacklist("bl.spamcannibal.org"),
			new RblBlacklist("psbl.surriel.com"),
			new RblBlacklist("ubl.unsubscore.com"),
			new RblBlacklist("dnsbl.njabl.org"),
			new RblBlacklist("combined.njabl.org"),
			new RblBlacklist("rbl.spamlab.com"),
			// Removed 2014-02-09: new RblBlacklist("bl.deadbeef.com"),
			// Removed 2014-02-09: new RblBlacklist("dnsbl.ahbl.org"),
			// Removed 2014-02-09: new RblBlacklist("tor.ahbl.org"),
			new RblBlacklist("ircbl.ahbl.org"),
			new RblBlacklist("dyna.spamrats.com"),
			new RblBlacklist("noptr.spamrats.com"),
			new RblBlacklist("spam.spamrats.com"),
			// Removed 2014-02-09: new RblBlacklist("blackholes.five-ten-sg.com"),
			// Removed 2014-02-09: new RblBlacklist("bl.emailbasura.org"),
			new RblBlacklist("cbl.anti-spam.org.cn"),
			new RblBlacklist("cdl.anti-spam.org.cn"),
			// Removed 2014-02-09: new RblBlacklist("dnsbl.cyberlogic.net"),
			new RblBlacklist("dnsbl.inps.de"),
			new RblBlacklist("drone.abuse.ch"),
			// Removed 2014-02-09: new RblBlacklist("spam.abuse.ch"),
			new RblBlacklist("httpbl.abuse.ch"),
			new RblBlacklist("dul.ru"),
			new RblBlacklist("korea.services.net"),
			new RblBlacklist("short.rbl.jp"),
			new RblBlacklist("virus.rbl.jp"),
			new RblBlacklist("spamrbl.imp.ch"),
			new RblBlacklist("wormrbl.imp.ch"),
			new RblBlacklist("virbl.bit.nl"),
			new RblBlacklist("rbl.suresupport.com"),
			new RblBlacklist("dsn.rfc-ignorant.org"),
			new RblBlacklist("ips.backscatterer.org"),
			new RblBlacklist("spamguard.leadmon.net"),
			new RblBlacklist("opm.tornevall.org"),
			new RblBlacklist("netblock.pedantic.org"),
			new RblBlacklist("multi.surbl.org"),
			new RblBlacklist("ix.dnsbl.manitu.net"),
			new RblBlacklist("tor.dan.me.uk"),
			new RblBlacklist("rbl.efnetrbl.org"),
			new RblBlacklist("relays.mail-abuse.org"),
			new RblBlacklist("blackholes.mail-abuse.org"),
			new RblBlacklist("rbl-plus.mail-abuse.org"),
			new RblBlacklist("dnsbl.dronebl.org"),
			new RblBlacklist("access.redhawk.org"),
			new RblBlacklist("db.wpbl.info"),
			new RblBlacklist("rbl.interserver.net"),
			new RblBlacklist("query.senderbase.org"),
			new RblBlacklist("bogons.cymru.com"),
			new RblBlacklist("csi.cloudmark.com"),
			// </editor-fold>

			// <editor-fold desc="checker.msrbl.com" defaultstate="collapsed">
			// From checker.msrbl.com on 2009-11-06
			// Seems offline on 2014-02-09
			// Offline 2014-02-09: new RblBlacklist("dnsbl.ahbl.org"),
			// Offline 2014-02-09: new RblBlacklist("bl.technovision.dk"),
			// Offline 2014-02-09: new RblBlacklist("blackholes.five-ten-sg.com"),
			// Offline 2014-02-09: new RblBlacklist("bl.csma.biz"),
			// Offline 2014-02-09: new RblBlacklist("bl.deadbeef.com"),
			// Offline 2014-02-09: new RblBlacklist("list.dsbl.org"),
			// Offline 2014-02-09: new RblBlacklist("multihop.dsbl.org"),
			// Offline 2014-02-09: new RblBlacklist("unconfirmed.dsbl.org"),
			// Offline 2014-02-09: new RblBlacklist("dul.ru"),
			// Offline 2014-02-09: new RblBlacklist("rbl.efnet.org"),
			// Offline 2014-02-09: new RblBlacklist("korea.services.net"),
			// Offline 2014-02-09: new RblBlacklist("combined.rbl.msrbl.net"),
			// Offline 2014-02-09: new RblBlacklist("phishing.rbl.msrbl.net"),
			// Offline 2014-02-09: new RblBlacklist("virus.rbl.msrbl.net"),
			// Offline 2014-02-09: new RblBlacklist("images.rbl.msrbl.net"),
			// Offline 2014-02-09: new RblBlacklist("spam.rbl.msrbl.net"),
			// Offline 2014-02-09: new RblBlacklist("web.rbl.msrbl.net"),
			// Offline 2014-02-09: new RblBlacklist("no-more-funn.moensted.dk"),
			// Disabled 2012-02-07: new RblBlacklist("okrelays.nthelp.com"),
			// Disabled 2012-02-07: new RblBlacklist("relays.nthelp.com"),
			// Offline 2014-02-09: new RblBlacklist("psbl.surriel.com"),
			// Offline 2014-02-09: new RblBlacklist("rbl.schulte.org"),
			// Offline 2014-02-09: new RblBlacklist("bl.spamcop.net"),
			// Offline 2014-02-09: new RblBlacklist("sbl-xbl.spamhaus.org"),
			// Offline 2014-02-09: new RblBlacklist("xbl.spamhaus.org"),
			// Offline 2014-02-09: new RblBlacklist("dnsbl.sorbs.net"),
			// Offline 2014-02-09: new RblBlacklist("http.dnsbl.sorbs.net"),
			// Offline 2014-02-09: new RblBlacklist("socks.dnsbl.sorbs.net"),
			// Offline 2014-02-09: new RblBlacklist("misc.dnsbl.sorbs.net"),
			// Offline 2014-02-09: new RblBlacklist("smtp.dnsbl.sorbs.net"),
			// Offline 2014-02-09: new RblBlacklist("web.dnsbl.sorbs.net"),
			// Offline 2014-02-09: new RblBlacklist("new.spam.dnsbl.sorbs.net"),
			// Offline 2014-02-09: new RblBlacklist("recent.spam.dnsbl.sorbs.net"),
			// Offline 2014-02-09: new RblBlacklist("old.spam.dnsbl.sorbs.net"),
			// Offline 2014-02-09: new RblBlacklist("spam.dnsbl.sorbs.net"),
			// Offline 2014-02-09: new RblBlacklist("escalations.dnsbl.sorbs.net"),
			// Offline 2014-02-09: new RblBlacklist("block.dnsbl.sorbs.net"),
			// Offline 2014-02-09: new RblBlacklist("zombie.dnsbl.sorbs.net"),
			// Offline 2014-02-09: new RblBlacklist("dul.dnsbl.sorbs.net"),
			// Offline 2014-02-09: new RblBlacklist("cbl.abuseat.org"),
			// Offline 2014-02-09: new RblBlacklist("dnsbl-1.uceprotect.net"),
			// Offline 2014-02-09: new RblBlacklist("dnsbl-2.uceprotect.net"),
			// Offline 2014-02-09: new RblBlacklist("dnsbl-3.uceprotect.net"),
			// Offline 2014-02-09: new RblBlacklist("dnsbl.njabl.org"),
			// Offline 2014-02-09: new RblBlacklist("dynablock.njabl.org"),
			// Offline 2014-02-09: new RblBlacklist("combined.njabl.org"),
			// Offline 2014-02-09: new RblBlacklist("bhnc.njabl.org"),
			// </editor-fold>

			// <editor-fold desc="multirbl.valli.org" defaultstate="collapsed">
			// From http://multirbl.valli.org/ on 2014-02-09
			new RblBlacklist("0spam.fusionzero.com"),
			new RblBlacklist("0spam-killlist.fusionzero.com"),
			// Removed 2014-02-09: new RblBlacklist("blackholes.five-ten-sg.com"),
			new RblBlacklist("combined.abuse.ch"),
			// Removed 2014-02-09: new RblBlacklist("dnsbl.abuse.ch"),
			new RblBlacklist("drone.abuse.ch"),
			new RblBlacklist("spam.abuse.ch"),
			new RblBlacklist("httpbl.abuse.ch"),
			// Forward lookup: uribl.zeustracker.abuse.ch
			new RblBlacklist("ipbl.zeustracker.abuse.ch"),
			new RblBlacklist("rbl.abuse.ro"),
			// Forward lookup: uribl.abuse.ro
			new RblBlacklist("dnsbl.ahbl.org"),
			new RblBlacklist("ircbl.ahbl.org"),
			// Forward lookup: rhsbl.ahbl.org
			// Removed 2014-02-09: new RblBlacklist("orvedb.aupads.org"),
			// Removed 2014-02-09: new RblBlacklist("rsbl.aupads.org"),
			new RblBlacklist("spam.dnsbl.anonmails.de"),
			new RblBlacklist("dnsbl.anticaptcha.net"),
			new RblBlacklist("orvedb.aupads.org"),
			new RblBlacklist("rsbl.aupads.org"),
			// Forward lookup: l1.apews.org
			new RblBlacklist("l2.apews.org"),
			// Removed 2014-02-09: new RblBlacklist("fresh.dict.rbl.arix.com"),
			// Removed 2014-02-09: new RblBlacklist("stale.dict.rbl.arix.com"),
			// Removed 2014-02-09: new RblBlacklist("fresh.sa_slip.rbl.arix.com"),
			// Removed 2014-02-09: new RblBlacklist("stale.sa_slip.arix.com"),
			new RblBlacklist("aspews.ext.sorbs.net"),
			new RblBlacklist("dnsbl.aspnet.hu"),
			// Forward lookup: dnsbl.aspnet.hu
			// Removed 2014-02-09: new RblBlacklist("access.atlbl.net"),
			// Removed 2014-02-09: new RblBlacklist("rbl.atlbl.net"),
			new RblBlacklist("ips.backscatterer.org"),
			new RblBlacklist("b.barracudacentral.org"),
			new RblBlacklist("bb.barracudacentral.org"),
			new RblBlacklist("list.bbfh.org"),
			new RblBlacklist("l1.bbfh.ext.sorbs.net"),
			new RblBlacklist("l2.bbfh.ext.sorbs.net"),
			new RblBlacklist("l3.bbfh.ext.sorbs.net"),
			new RblBlacklist("l4.bbfh.ext.sorbs.net"),
			new RblBlacklist("bbm.2ch.net"),
			new RblBlacklist("niku.2ch.net"),
			new RblBlacklist("bbx.2ch.net"),
			// Removed 2014-02-09: new RblBlacklist("bl.deadbeef.com"),
			// Removed 2014-02-09: new RblBlacklist("rbl.blakjak.net"),
			new RblBlacklist("netscan.rbl.blockedservers.com"),
			new RblBlacklist("rbl.blockedservers.com"),
			new RblBlacklist("spam.rbl.blockedservers.com"),
			new RblBlacklist("list.blogspambl.com"),
			new RblBlacklist("bsb.empty.us"),
			// Forward lookup: bsb.empty.us
			new RblBlacklist("bsb.spamlookup.net"),
			// Forward lookup: bsb.spamlookup.net
			new RblBlacklist("dnsbl.burnt-tech.com"),
			new RblBlacklist("blacklist.sci.kun.nl"),
			new RblBlacklist("cbl.anti-spam.org.cn"),
			new RblBlacklist("cblplus.anti-spam.org.cn"),
			new RblBlacklist("cblless.anti-spam.org.cn"),
			new RblBlacklist("cdl.anti-spam.org.cn"),
			new RblBlacklist("cbl.abuseat.org"),
			new RblBlacklist("rbl.choon.net"),
			// Removed 2014-02-09: new RblBlacklist("dnsbl.cyberlogic.net"),
			new RblBlacklist("bogons.cymru.com"),
			new RblBlacklist("v4.fullbogons.cymru.com"),
			new RblBlacklist("tor.dan.me.uk"),
			new RblBlacklist("torexit.dan.me.uk"),
			// Forward lookup: ex.dnsbl.org
			// Forward lookup: in.dnsbl.org
			new RblBlacklist("rbl.dns-servicios.com"),
			new RblBlacklist("dnsbl.ipocalypse.net"),
			new RblBlacklist("dnsbl.mags.net"),
			// Forward lookup: dnsbl.othello.ch
			new RblBlacklist("dnsbl.rv-soft.info"),
			new RblBlacklist("dnsblchile.org"),
			new RblBlacklist("vote.drbl.caravan.ru"),
			new RblBlacklist("vote.drbldf.dsbl.ru"),
			new RblBlacklist("vote.drbl.gremlin.ru"),
			new RblBlacklist("work.drbl.caravan.ru"),
			new RblBlacklist("work.drbldf.dsbl.ru"),
			new RblBlacklist("work.drbl.gremlin.ru"),
			// Removed 2014-02-09: new RblBlacklist("vote.drbl.drand.net"),
			// Removed 2014-02-09: new RblBlacklist("spamprobe.drbl.drand.net"),
			// Removed 2014-02-09: new RblBlacklist("spamtrap.drbl.drand.net"),
			// Removed 2014-02-09: new RblBlacklist("work.drbl.drand.net"),
			new RblBlacklist("bl.drmx.org"),
			new RblBlacklist("dnsbl.dronebl.org"),
			// Removed 2014-02-09: new RblBlacklist("rbl.efnethelp.net"),
			new RblBlacklist("rbl.efnet.org"),
			new RblBlacklist("rbl.efnetrbl.org"),
			new RblBlacklist("tor.efnet.org"),
			new RblBlacklist("bl.emailbasura.org"),
			new RblBlacklist("rbl.fasthosts.co.uk"),
			new RblBlacklist("fnrbl.fast.net"),
			new RblBlacklist("forbidden.icm.edu.pl"),
			new RblBlacklist("hil.habeas.com"),
			new RblBlacklist("lookup.dnsbl.iip.lu"),
			new RblBlacklist("spamrbl.imp.ch"),
			new RblBlacklist("wormrbl.imp.ch"),
			new RblBlacklist("dnsbl.inps.de"),
			new RblBlacklist("intercept.datapacket.net"),
			new RblBlacklist("rbl.interserver.net"),
			new RblBlacklist("any.dnsl.ipquery.org"),
			new RblBlacklist("backscat.dnsl.ipquery.org"),
			new RblBlacklist("netblock.dnsl.ipquery.org"),
			new RblBlacklist("relay.dnsl.ipquery.org"),
			new RblBlacklist("single.dnsl.ipquery.org"),
			new RblBlacklist("mail-abuse.blacklist.jippg.org"),
			// Removed 2014-02-09: new RblBlacklist("karmasphere.email-sender.dnsbl.karmasphere.com"),
			new RblBlacklist("dnsbl.justspam.org"),
			new RblBlacklist("dnsbl.kempt.net"),
			new RblBlacklist("spamlist.or.kr"),
			new RblBlacklist("bl.konstant.no"),
			new RblBlacklist("relays.bl.kundenserver.de"),
			new RblBlacklist("spamguard.leadmon.net"),
			// Removed 2014-02-09: new RblBlacklist("fraud.rhs.mailpolice.com"),
			// Removed 2014-02-09: new RblBlacklist("sbl.csma.biz"),
			// Removed 2014-02-09: new RblBlacklist("bl.csma.biz"),
			new RblBlacklist("dnsbl.madavi.de"),
			new RblBlacklist("ipbl.mailhosts.org"),
			// Forward lookup: rhsbl.mailhosts.org
			new RblBlacklist("shortlist.mailhosts.org"),
			new RblBlacklist("bl.mailspike.net"),
			new RblBlacklist("z.mailspike.net"),
			new RblBlacklist("bl.mav.com.br"),
			new RblBlacklist("cidr.bl.mcafee.com"),
			new RblBlacklist("rbl.megarbl.net"),
			new RblBlacklist("combined.rbl.msrbl.net"),
			new RblBlacklist("images.rbl.msrbl.net"),
			new RblBlacklist("phishing.rbl.msrbl.net"),
			new RblBlacklist("spam.rbl.msrbl.net"),
			new RblBlacklist("virus.rbl.msrbl.net"),
			new RblBlacklist("web.rbl.msrbl.net"),
			new RblBlacklist("relays.nether.net"),
			new RblBlacklist("unsure.nether.net"),
			new RblBlacklist("ix.dnsbl.manitu.net"),
			// Removed 2014-02-09: new RblBlacklist("dnsbl.njabl.org"),
			// Removed 2014-02-09: new RblBlacklist("bhnc.njabl.org"),
			// Removed 2014-02-09: new RblBlacklist("combined.njabl.org"),
			new RblBlacklist("no-more-funn.moensted.dk"),
			new RblBlacklist("nospam.ant.pl"),
			new RblBlacklist("dyn.nszones.com"),
			new RblBlacklist("sbl.nszones.com"),
			new RblBlacklist("bl.nszones.com"),
			// Forward lookup: ubl.nszones.com
			new RblBlacklist("dnsbl.openresolvers.org"),
			new RblBlacklist("rbl.orbitrbl.com", AlertLevel.NONE),
			new RblBlacklist("netblock.pedantic.org"),
			new RblBlacklist("spam.pedantic.org"),
			new RblBlacklist("pofon.foobar.hu"),
			new RblBlacklist("rbl.polarcomm.net"),
			new RblBlacklist("dnsbl.proxybl.org"),
			new RblBlacklist("psbl.surriel.com"),
			new RblBlacklist("all.rbl.jp"),
			// Forward lookup: dyndns.rbl.jp
			new RblBlacklist("short.rbl.jp"),
			// Forward lookup: url.rbl.jp
			new RblBlacklist("virus.rbl.jp"),
			new RblBlacklist("rbl.schulte.org"),
			new RblBlacklist("rbl.talkactive.net"),
			new RblBlacklist("access.redhawk.org"),
			new RblBlacklist("dnsbl.rizon.net"),
			new RblBlacklist("dynip.rothen.com"),
			new RblBlacklist("dul.ru"),
			new RblBlacklist("dnsbl.rymsho.ru"),
			// Forward lookup: rhsbl.rymsho.ru
			new RblBlacklist("all.s5h.net"),
			new RblBlacklist("tor.dnsbl.sectoor.de"),
			new RblBlacklist("exitnodes.tor.dnsbl.sectoor.de"),
			new RblBlacklist("bl.score.senderscore.com"),
			new RblBlacklist("bl.shlink.org"),
			new RblBlacklist("dyn.shlink.org"),
			// Forward lookup: rhsbl.shlink.org
			// Removed 2014-02-09: new RblBlacklist("dnsbl.solid.net"),
			new RblBlacklist("dnsbl.sorbs.net"),
			new RblBlacklist("problems.dnsbl.sorbs.net"),
			new RblBlacklist("proxies.dnsbl.sorbs.net"),
			new RblBlacklist("relays.dnsbl.sorbs.net"),
			new RblBlacklist("safe.dnsbl.sorbs.net"),
			// Forward lookup: nomail.rhsbl.sorbs.net
			// Forward lookup: badconf.rhsbl.sorbs.net
			new RblBlacklist("dul.dnsbl.sorbs.net"),
			new RblBlacklist("zombie.dnsbl.sorbs.net"),
			new RblBlacklist("block.dnsbl.sorbs.net"),
			new RblBlacklist("escalations.dnsbl.sorbs.net"),
			new RblBlacklist("http.dnsbl.sorbs.net"),
			new RblBlacklist("misc.dnsbl.sorbs.net"),
			new RblBlacklist("smtp.dnsbl.sorbs.net"),
			new RblBlacklist("socks.dnsbl.sorbs.net"),
			// Forward lookup: rhsbl.sorbs.net
			new RblBlacklist("spam.dnsbl.sorbs.net"),
			new RblBlacklist("recent.spam.dnsbl.sorbs.net"),
			new RblBlacklist("new.spam.dnsbl.sorbs.net"),
			new RblBlacklist("old.spam.dnsbl.sorbs.net"),
			new RblBlacklist("web.dnsbl.sorbs.net"),
			new RblBlacklist("korea.services.net"),
			new RblBlacklist("backscatter.spameatingmonkey.net"),
			new RblBlacklist("badnets.spameatingmonkey.net"),
			new RblBlacklist("bl.spameatingmonkey.net"),
			// Forward lookup: fresh.spameatingmonkey.net
			// Forward lookup: fresh10.spameatingmonkey.net
			// Forward lookup: fresh15.spameatingmonkey.net
			new RblBlacklist("netbl.spameatingmonkey.net"),
			// Forward lookup: uribl.spameatingmonkey.net
			// Forward lookup: urired.spameatingmonkey.net
			// Removed 2014-02-09: new RblBlacklist("map.spam-rbl.com"),
			new RblBlacklist("singlebl.spamgrouper.com"),
			new RblBlacklist("netblockbl.spamgrouper.com"),
			new RblBlacklist("all.spam-rbl.fr"),
			new RblBlacklist("bl.spamcannibal.org"),
			new RblBlacklist("dnsbl.spam-champuru.livedoor.com"),
			new RblBlacklist("bl.spamcop.net"),
			// Forward lookup: dbl.spamhaus.org
			new RblBlacklist("pbl.spamhaus.org"),
			new RblBlacklist("sbl.spamhaus.org"),
			new RblBlacklist("sbl-xbl.spamhaus.org"),
			new RblBlacklist("xbl.spamhaus.org"),
			new RblBlacklist("zen.spamhaus.org"),
			new RblBlacklist("feb.spamlab.com"),
			new RblBlacklist("rbl.spamlab.com"),
			new RblBlacklist("all.spamrats.com"),
			new RblBlacklist("dyna.spamrats.com"),
			new RblBlacklist("noptr.spamrats.com"),
			new RblBlacklist("spam.spamrats.com"),
			new RblBlacklist("spamsources.fabel.dk"),
			new RblBlacklist("bl.spamstinks.com"),
			new RblBlacklist("badhost.stopspam.org"),
			new RblBlacklist("block.stopspam.org"),
			new RblBlacklist("dnsbl.stopspam.org"),
			new RblBlacklist("dul.pacifier.net"),
			// Removed 2014-02-09: new RblBlacklist("ab.surbl.org"),
			// Removed 2014-02-09: new RblBlacklist("jp.surbl.org"),
			new RblBlacklist("multi.surbl.org"),
			// Forward lookup: multi.surbl.org
			// Removed 2014-02-09: new RblBlacklist("ob.surbl.org"),
			// Removed 2014-02-09: new RblBlacklist("ph.surbl.org"),
			// Removed 2014-02-09: new RblBlacklist("sc.surbl.org"),
			// Removed 2014-02-09: new RblBlacklist("ws.surbl.org"),
			new RblBlacklist("xs.surbl.org"),
			// Forward lookup: xs.surbl.org
			// Disabled 2012-02-07: new RblBlacklist("dnsbl.swiftbl.net"),
			new RblBlacklist("dnsbl.swiftbl.org"),
			new RblBlacklist("dnsrbl.swinog.ch"),
			// Forward lookup: uribl.swinog.ch
			new RblBlacklist("bl.technovision.dk"),
			new RblBlacklist("st.technovision.dk"),
			// Forward lookup: dob.sibl.support-intelligence.net
			new RblBlacklist("opm.tornevall.org"),
			new RblBlacklist("rbl2.triumf.ca"),
			new RblBlacklist("truncate.gbudb.net"),
			new RblBlacklist("dnsbl-0.uceprotect.net"),
			new RblBlacklist("dnsbl-1.uceprotect.net"),
			new RblBlacklist("dnsbl-2.uceprotect.net"),
			new RblBlacklist("dnsbl-3.uceprotect.net"),
			new RblBlacklist("ubl.unsubscore.com"),
			// Forward lookup: black.uribl.com
			// Forward lookup: grey.uribl.com
			// Forward lookup: multi.uribl.com
			// Forward lookup: red.uribl.com
			// Removed 2014-02-09: new RblBlacklist("ubl.lashback.com"),
			new RblBlacklist("free.v4bl.org"),
			new RblBlacklist("ip.v4bl.org"),
			new RblBlacklist("virbl.dnsbl.bit.nl"),
			new RblBlacklist("dnsbl.webequipped.com"),
			new RblBlacklist("blacklist.woody.ch"),
			// Forward lookup: uri.blacklist.woody.ch
			new RblBlacklist("db.wpbl.info"),
			new RblBlacklist("bl.blocklist.de"),
			new RblBlacklist("dnsbl.zapbl.net"),
			// Forward lookup: rhsbl.zapbl.net
			// Forward lookup: zebl.zoneedit.com
			// Forward lookup: ban.zebl.zoneedit.com
			// Removed 2014-02-09: new RblBlacklist("dnsbl.zetabl.org"),
			new RblBlacklist("hostkarma.junkemailfilter.com"),
			// Forward lookup: hostkarma.junkemailfilter.com
			new RblBlacklist("rep.mailspike.net"),
			new RblBlacklist("list.quorum.to"),
			new RblBlacklist("srn.surgate.net")
			// </editor-fold>
		);
		//InetAddress ip = ipAddress.getInetAddress();
		boolean checkSmtpBlacklist =
			//!"64.62.174.125".equals(ip)
			//&& !"64.62.174.189".equals(ip)
			//&& !"64.62.174.253".equals(ip)
			//&& !"64.71.144.125".equals(ip)
			//&& !"66.160.183.125".equals(ip)
			//&& !"66.160.183.189".equals(ip)
			//&& !"66.160.183.253".equals(ip)
			ipAddress.getCheckBlacklistsOverSmtp()
			&& ipAddress.getNetDevice().getServer().getAOServer()!=null
		;
		lookups = new ArrayList<>(checkSmtpBlacklist ? (rblBlacklists.length + 5) : rblBlacklists.length);
		for(BlacklistLookup rblBlacklist : rblBlacklists) {
			lookups.add(rblBlacklist);
		}
		if(checkSmtpBlacklist) {
			lookups.add(new SmtpBlacklist("att.net",       AlertLevel.MEDIUM));
			lookups.add(new SmtpBlacklist("bellsouth.net", AlertLevel.MEDIUM));
			lookups.add(new SmtpBlacklist("comcast.net",   AlertLevel.MEDIUM));
			lookups.add(new SmtpBlacklist("pacbell.net",   AlertLevel.MEDIUM));
			lookups.add(new SmtpBlacklist("sbcglobal.net", AlertLevel.MEDIUM));
		}
		Collections.sort(lookups);
	}

	@Override
	protected int getColumns() {
		return 5;
	}

	@Override
	protected List<String> getColumnHeaders(Locale locale) {
		List<String> columnHeaders = new ArrayList<>(5);
		columnHeaders.add(accessor.getMessage(/*locale,*/ "BlacklistsNodeWorker.columnHeader.basename"));
		columnHeaders.add(accessor.getMessage(/*locale,*/ "BlacklistsNodeWorker.columnHeader.queryTime"));
		columnHeaders.add(accessor.getMessage(/*locale,*/ "BlacklistsNodeWorker.columnHeader.latency"));
		columnHeaders.add(accessor.getMessage(/*locale,*/ "BlacklistsNodeWorker.columnHeader.query"));
		columnHeaders.add(accessor.getMessage(/*locale,*/ "BlacklistsNodeWorker.columnHeader.result"));
		return columnHeaders;
	}

	private static final ExecutorService executorService = Executors.newFixedThreadPool(
		NUM_THREADS,
		new ThreadFactory() {
			@Override
			public Thread newThread(Runnable r) {
				return new Thread(r, BlacklistsNodeWorker.class.getName()+".executorService");
			}
		}
	);

	private final Map<String,BlacklistQueryResult> queryResultCache = new HashMap<>();

	@Override
	protected List<BlacklistQueryResult> getQueryResult(Locale locale) throws Exception {
		// Run each query in parallel
		List<Long> startTimes = new ArrayList<>(lookups.size());
		List<Long> startNanos = new ArrayList<>(lookups.size());
		List<Future<BlacklistQueryResult>> futures = new ArrayList<>(lookups.size());
		for(final BlacklistLookup lookup : lookups) {
			final String baseName = lookup.getBaseName();
			BlacklistQueryResult oldResult;
			synchronized(queryResultCache) {
				oldResult = queryResultCache.get(baseName);
			}
			final long currentTime = System.currentTimeMillis();
			boolean needNewQuery;
			if(oldResult==null) {
				needNewQuery = true;
			} else {
				long timeSince = currentTime - oldResult.queryTime;
				if(timeSince<0) timeSince = -timeSince; // Handle system time reset
				switch(oldResult.alertLevel) {
					case UNKNOWN:
						// Retry for those unknown
						needNewQuery = timeSince >= UNKNOWN_RETRY;
						break;
					case NONE:
						// Retry when no problem
						needNewQuery = timeSince >= GOOD_RETRY;
						break;
					default:
						// All others, retry
						needNewQuery = timeSince >= BAD_RETRY;
				}
			}
			if(needNewQuery) {
				startTimes.add(currentTime);
				startNanos.add(System.nanoTime());
				futures.add(
					executorService.submit(
						new Callable<BlacklistQueryResult>() {
							@Override
							public BlacklistQueryResult call() throws Exception {
								BlacklistQueryResult result = lookup.call();
								// Remember result even if timed-out on queue, this is to try to not lose any progress.
								// Time-outs are only cached here, never from a queue timeout
								synchronized(queryResultCache) {
									queryResultCache.put(baseName, result);
								}
								return result;
							}
						}
					)
				);
				Thread.sleep(TASK_DELAY);
			} else {
				startTimes.add(null);
				startNanos.add(null);
				futures.add(null);
			}
		}

		// Get the results
		List<BlacklistQueryResult> results = new ArrayList<>(lookups.size());
		for(int c=0;c<lookups.size();c++) {
			BlacklistLookup lookup = lookups.get(c);
			String baseName = lookup.getBaseName();
			BlacklistQueryResult result;
			Future<BlacklistQueryResult> future = futures.get(c);
			if(future==null) {
				// Use previously cached value
				synchronized(queryResultCache) {
					result = queryResultCache.get(baseName);
				}
				if(result==null) throw new AssertionError("result==null");
			} else {
				long startTime = startTimes.get(c);
				long startNano = startNanos.get(c);
				long timeoutRemainingNanos = startNano + TIMEOUT * 1000000L - System.nanoTime();
				if(timeoutRemainingNanos<0) timeoutRemainingNanos = 0L;
				boolean cacheResult;
				try {
					result = future.get(timeoutRemainingNanos, TimeUnit.NANOSECONDS);
					cacheResult = true;
				} catch(TimeoutException to) {
					future.cancel(false);
					result = new BlacklistQueryResult(baseName, startTime, System.nanoTime() - startNano, lookup.getQuery(), "Timeout in queue, timeoutRemaining = " + new NanoInterval(timeoutRemainingNanos), AlertLevel.UNKNOWN);
					// Queue timeouts are not cached
					cacheResult = false;
				} catch(ThreadDeath TD) {
					throw TD;
				} catch(InterruptedException | ExecutionException | RuntimeException e) {
					future.cancel(false);
					result = new BlacklistQueryResult(baseName, startTime, System.nanoTime() - startNano, lookup.getQuery(), e.getMessage(), lookup.getMaxAlertLevel());
					cacheResult = true;
				}
				if(cacheResult) {
					synchronized(queryResultCache) {
						queryResultCache.put(baseName, result);
					}
				}
			}
			results.add(result);
		}
		return results;
	}

	@Override
	protected List<Object> getTableData(List<BlacklistQueryResult> queryResult, Locale locale) throws Exception {
		List<Object> tableData = new ArrayList<>(queryResult.size()*5);
		for(BlacklistQueryResult result : queryResult) {
			tableData.add(result.basename);
			tableData.add(new TimeWithTimeZone(result.queryTime));
			tableData.add(new NanoInterval(result.latency));
			tableData.add(result.query);
			tableData.add(result.result);
		}
		return tableData;
	}

	@Override
	protected List<AlertLevel> getAlertLevels(List<BlacklistQueryResult> queryResult) {
		List<AlertLevel> alertLevels = new ArrayList<>(queryResult.size());
		for(BlacklistQueryResult result : queryResult) alertLevels.add(result.alertLevel);
		return alertLevels;
	}

	@Override
	protected AlertLevelAndMessage getAlertLevelAndMessage(Locale locale, TableResult result) {
		AlertLevel highestAlertLevel = AlertLevel.NONE;
		String highestAlertMessage = "";
		List<?> tableData = result.getTableData();
		if(result.isError()) {
			highestAlertLevel = result.getAlertLevels().get(0);
			highestAlertMessage = tableData.get(0).toString();
		} else {
			for(int index=0,len=tableData.size();index<len;index+=5) {
				AlertLevel alertLevel = result.getAlertLevels().get(index/5);
				// Too many queries time-out: if alert level is "Unknown", treat as "Low"
				if(alertLevel == AlertLevel.UNKNOWN) alertLevel = AlertLevel.LOW;
				if(alertLevel.compareTo(highestAlertLevel)>0) {
					highestAlertLevel = alertLevel;
					Object resultValue = tableData.get(index+4);
					highestAlertMessage = tableData.get(index)+": "+(resultValue==null ? "null" : resultValue.toString());
				}
			}
		}
		// Do not allow higher than MEDIUM, even if individual rows are higher
		// if(highestAlertLevel.compareTo(AlertLevel.MEDIUM)>0) highestAlertLevel=AlertLevel.MEDIUM;
		return new AlertLevelAndMessage(highestAlertLevel, highestAlertMessage);
	}

	/**
	 * The sleep delay is always 15 minutes.  The query results are cached and will
	 * be reused until individual timeouts.
	 */
	@Override
	protected long getSleepDelay(boolean lastSuccessful, AlertLevel alertLevel) {
		return 15L * 60L * 1000L;
	}

	@Override
	protected long getTimeout() {
		return TIMEOUT + 5L*60L*1000L; // Allow extra five minutes to try to capture timeouts on a row-by-row basis.
	}

	@Override
	protected TimeUnit getTimeoutUnit() {
		return TimeUnit.MILLISECONDS;
	}

	/**
	 * The startup delay is within fifteen minutes.
	 */
	@Override
	protected int getNextStartupDelay() {
		return RootNodeImpl.getNextStartupDelayFifteenMinutes();
	}
}
