/*
 * Copyright 2018 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor.pki;

import com.aoindustries.aoserv.client.linux.Server;
import com.aoindustries.aoserv.client.pki.Certificate;
import com.aoindustries.noc.monitor.AlertLevelUtils;
import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.noc.monitor.NodeImpl;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.net.HostNode;
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
import java.util.Iterator;
import java.util.List;
import javax.swing.SwingUtilities;

/**
 * @author  AO Industries, Inc.
 */
public class CertificatesNode extends NodeImpl {

	private static final long serialVersionUID = 1L;

	final HostNode serverNode;
	private final Server aoServer;
	private final List<CertificateNode> sslCertificateNodes = new ArrayList<>();
	private boolean started;

	public CertificatesNode(HostNode serverNode, Server aoServer, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws RemoteException {
		super(port, csf, ssf);
		this.serverNode = serverNode;
		this.aoServer = aoServer;
	}

	@Override
	public HostNode getParent() {
		return serverNode;
	}

	public Server getAOServer() {
		return aoServer;
	}

	@Override
	public boolean getAllowsChildren() {
		return true;
	}

	@Override
	public List<CertificateNode> getChildren() {
		synchronized(sslCertificateNodes) {
			return getSnapshot(sslCertificateNodes);
		}
	}

	/**
	 * The alert level is equal to the highest alert level of its children.
	 */
	@Override
	public AlertLevel getAlertLevel() {
		AlertLevel level;
		synchronized(sslCertificateNodes) {
			level = AlertLevelUtils.getMaxAlertLevel(sslCertificateNodes);
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
		return accessor.getMessage(serverNode.hostsNode.rootNode.locale, "SslCertificatesNode.label");
	}

	private final TableListener tableListener = (Table<?> table) -> {
		try {
			verifySslCertificates();
		} catch(IOException | SQLException err) {
			throw new WrappedException(err);
		}
	};

	public void start() throws IOException, SQLException {
		synchronized(sslCertificateNodes) {
			if(started) throw new IllegalStateException();
			started = true;
			serverNode.hostsNode.rootNode.conn.getPki().getCertificate().addTableListener(tableListener, 100);
			serverNode.hostsNode.rootNode.conn.getPki().getCertificateName().addTableListener(tableListener, 100);
		}
		verifySslCertificates();
	}

	public void stop() {
		synchronized(sslCertificateNodes) {
			started = false;
			serverNode.hostsNode.rootNode.conn.getPki().getCertificateName().removeTableListener(tableListener);
			serverNode.hostsNode.rootNode.conn.getPki().getCertificate().removeTableListener(tableListener);
			for(CertificateNode sslCertificateNode : sslCertificateNodes) {
				sslCertificateNode.stop();
				serverNode.hostsNode.rootNode.nodeRemoved();
			}
			sslCertificateNodes.clear();
		}
	}

	private void verifySslCertificates() throws IOException, SQLException {
		assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

		synchronized(sslCertificateNodes) {
			if(!started) return;
		}

		List<Certificate> sslCertificates = aoServer.getSslCertificates();
		synchronized(sslCertificateNodes) {
			if(started) {
				// Remove old ones
				Iterator<CertificateNode> sslCertificateNodeIter = sslCertificateNodes.iterator();
				while(sslCertificateNodeIter.hasNext()) {
					CertificateNode sslCertificateNode = sslCertificateNodeIter.next();
					Certificate sslCertificate = sslCertificateNode.getSslCertificate();
					// Find matching new state
					Certificate newCert = null;
					for(Certificate cert : sslCertificates) {
						if(cert.equals(sslCertificate)) {
							newCert = cert;
							break;
						}
					}
					if(
						// Does not exist
						newCert == null
						// or label changed
						|| !CertificateNode.getLabel(newCert).equals(sslCertificateNode.getLabel())
					) {
						sslCertificateNode.stop();
						sslCertificateNodeIter.remove();
						serverNode.hostsNode.rootNode.nodeRemoved();
					}
				}
				// Add new ones
				for(int c = 0; c < sslCertificates.size(); c++) {
					Certificate sslCertificate = sslCertificates.get(c);
					if(c >= sslCertificateNodes.size() || !sslCertificate.equals(sslCertificateNodes.get(c).getSslCertificate())) {
						// Insert into proper index
						CertificateNode sslCertificateNode = new CertificateNode(this, sslCertificate, port, csf, ssf);
						sslCertificateNodes.add(c, sslCertificateNode);
						sslCertificateNode.start();
						serverNode.hostsNode.rootNode.nodeAdded();
					}
				}
				// Prune any extra nodes that can happen when they are reordered
				while(sslCertificateNodes.size() > sslCertificates.size()) {
					CertificateNode sslCertificateNode = sslCertificateNodes.remove(sslCertificateNodes.size() - 1);
					sslCertificateNode.stop();
					serverNode.hostsNode.rootNode.nodeRemoved();
				}
			}
		}
	}

	File getPersistenceDirectory() throws IOException {
		File dir = new File(serverNode.getPersistenceDirectory(), "ssl_certificates");
		if(!dir.exists()) {
			if(!dir.mkdir()) {
				throw new IOException(
					accessor.getMessage(
						serverNode.hostsNode.rootNode.locale,
						"error.mkdirFailed",
						dir.getCanonicalPath()
					)
				);
			}
		}
		return dir;
	}
}
