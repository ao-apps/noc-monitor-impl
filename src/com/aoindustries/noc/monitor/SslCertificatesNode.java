/*
 * Copyright 2018 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.aoserv.client.AOServer;
import com.aoindustries.aoserv.client.SslCertificate;
import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.noc.monitor.common.AlertLevel;
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
public class SslCertificatesNode extends NodeImpl {

	private static final long serialVersionUID = 1L;

	final ServerNode serverNode;
	private final AOServer aoServer;
	private final List<SslCertificateNode> sslCertificateNodes = new ArrayList<>();

	SslCertificatesNode(ServerNode serverNode, AOServer aoServer, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws RemoteException {
		super(port, csf, ssf);
		this.serverNode = serverNode;
		this.aoServer = aoServer;
	}

	@Override
	public ServerNode getParent() {
		return serverNode;
	}

	public AOServer getAOServer() {
		return aoServer;
	}

	@Override
	public boolean getAllowsChildren() {
		return true;
	}

	@Override
	public List<SslCertificateNode> getChildren() {
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
		return accessor.getMessage(serverNode.serversNode.rootNode.locale, "SslCertificatesNode.label");
	}

	private final TableListener tableListener = (Table<?> table) -> {
		try {
			verifySslCertificates();
		} catch(IOException | SQLException err) {
			throw new WrappedException(err);
		}
	};

	void start() throws IOException, SQLException {
		synchronized(sslCertificateNodes) {
			serverNode.serversNode.rootNode.conn.getSslCertificates().addTableListener(tableListener, 100);
			serverNode.serversNode.rootNode.conn.getSslCertificateNames().addTableListener(tableListener, 100);
			verifySslCertificates();
		}
	}

	void stop() {
		synchronized(sslCertificateNodes) {
			serverNode.serversNode.rootNode.conn.getSslCertificateNames().removeTableListener(tableListener);
			serverNode.serversNode.rootNode.conn.getSslCertificates().removeTableListener(tableListener);
			for(SslCertificateNode sslCertificateNode : sslCertificateNodes) {
				sslCertificateNode.stop();
				serverNode.serversNode.rootNode.nodeRemoved();
			}
			sslCertificateNodes.clear();
		}
	}

	private void verifySslCertificates() throws IOException, SQLException {
		assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

		List<SslCertificate> sslCertificates = aoServer.getSslCertificates();
		synchronized(sslCertificateNodes) {
			// Remove old ones
			Iterator<SslCertificateNode> sslCertificateNodeIter = sslCertificateNodes.iterator();
			while(sslCertificateNodeIter.hasNext()) {
				SslCertificateNode sslCertificateNode = sslCertificateNodeIter.next();
				SslCertificate sslCertificate = sslCertificateNode.getSslCertificate();
				// Find matching new state
				SslCertificate newCert = null;
				for(SslCertificate cert : sslCertificates) {
					if(cert.equals(sslCertificate)) {
						newCert = cert;
						break;
					}
				}
				if(
					// Does not exist
					newCert == null
					// or label changed
					|| !SslCertificateNode.getLabel(newCert).equals(sslCertificateNode.getLabel())
				) {
					sslCertificateNode.stop();
					sslCertificateNodeIter.remove();
					serverNode.serversNode.rootNode.nodeRemoved();
				}
			}
			// Add new ones
			for(int c = 0; c < sslCertificates.size(); c++) {
				SslCertificate sslCertificate = sslCertificates.get(c);
				if(c >= sslCertificateNodes.size() || !sslCertificate.equals(sslCertificateNodes.get(c).getSslCertificate())) {
					// Insert into proper index
					SslCertificateNode sslCertificateNode = new SslCertificateNode(this, sslCertificate, port, csf, ssf);
					sslCertificateNodes.add(c, sslCertificateNode);
					sslCertificateNode.start();
					serverNode.serversNode.rootNode.nodeAdded();
				}
			}
			// Prune any extra nodes that can happen when they are reordered
			while(sslCertificateNodes.size() > sslCertificates.size()) {
				SslCertificateNode sslCertificateNode = sslCertificateNodes.remove(sslCertificateNodes.size() - 1);
				sslCertificateNode.stop();
				serverNode.serversNode.rootNode.nodeRemoved();
			}
		}
	}

	File getPersistenceDirectory() throws IOException {
		File dir = new File(serverNode.getPersistenceDirectory(), "ssl_certificates");
		if(!dir.exists()) {
			if(!dir.mkdir()) {
				throw new IOException(
					accessor.getMessage(
						serverNode.serversNode.rootNode.locale,
						"error.mkdirFailed",
						dir.getCanonicalPath()
					)
				);
			}
		}
		return dir;
	}
}
