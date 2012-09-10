/*
 * Copyright 2008-2012 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import static com.aoindustries.noc.monitor.ApplicationResources.accessor;
import com.aoindustries.aoserv.client.AOServConnector;
import com.aoindustries.aoserv.client.IPAddress;
import com.aoindustries.aoserv.client.NetBind;
import com.aoindustries.aoserv.client.NetDevice;
import com.aoindustries.aoserv.client.Server;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.Node;
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
import java.util.Iterator;
import java.util.List;
import javax.swing.SwingUtilities;

/**
 * The node per NetBind.
 *
 * TODO: Add output of netstat -ln here to detect extra ports.
 *
 * @author  AO Industries, Inc.
 */
public class NetBindsNode extends NodeImpl {

    private static final long serialVersionUID = 1L;

    final IPAddressNode ipAddressNode;
    private final List<NetBindNode> netBindNodes = new ArrayList<NetBindNode>();

    NetBindsNode(IPAddressNode ipAddressNode, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws RemoteException {
        super(port, csf, ssf);

        this.ipAddressNode = ipAddressNode;
    }

    @Override
    public Node getParent() {
        return ipAddressNode;
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
        synchronized(netBindNodes) {
            return Collections.unmodifiableList(new ArrayList<NetBindNode>(netBindNodes));
        }
    }

    /**
     * The alert level is equal to the highest alert level of its children.
     */
    @Override
    public AlertLevel getAlertLevel() {
        AlertLevel level = AlertLevel.NONE;
        synchronized(netBindNodes) {
            for(NodeImpl netBindNode : netBindNodes) {
                AlertLevel netBindNodeLevel = netBindNode.getAlertLevel();
                if(netBindNodeLevel.compareTo(level)>0) level = netBindNodeLevel;
            }
        }
        return level;
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
        return "net_binds";
    }

    @Override
    public String getLabel() {
        return accessor.getMessage(/*ipAddressNode.ipAddressesNode.netDeviceNode._networkDevicesNode.serverNode.serversNode.rootNode.locale,*/ "NetBindsNode.label");
    }

    private TableListener tableListener = new TableListener() {
        @Override
        public void tableUpdated(Table<?> table) {
            try {
                verifyNetBinds();
            } catch(IOException err) {
                throw new WrappedException(err);
            } catch(SQLException err) {
                throw new WrappedException(err);
            }
        }
    };

    void start() throws IOException, SQLException {
        AOServConnector conn = ipAddressNode.ipAddressesNode.netDeviceNode._networkDevicesNode.serverNode.serversNode.rootNode.conn;
        synchronized(netBindNodes) {
            conn.getIpAddresses().addTableListener(tableListener, 100);
            conn.getNetBinds().addTableListener(tableListener, 100);
            conn.getNetDevices().addTableListener(tableListener, 100);
            verifyNetBinds();
        }
    }

    void stop() {
        RootNodeImpl rootNode = ipAddressNode.ipAddressesNode.netDeviceNode._networkDevicesNode.serverNode.serversNode.rootNode;
        AOServConnector conn = rootNode.conn;
        synchronized(netBindNodes) {
            conn.getIpAddresses().removeTableListener(tableListener);
            conn.getNetBinds().removeTableListener(tableListener);
            conn.getNetDevices().removeTableListener(tableListener);
            for(NetBindNode netBindNode : netBindNodes) {
                netBindNode.stop();
                rootNode.nodeRemoved();
            }
            netBindNodes.clear();
        }
    }

    static class NetMonitorSetting implements Comparable<NetMonitorSetting> {

        private final Server server;
        private final NetBind netBind;
        private final String ipAddress;
        private final int port;
        private final String netProtocol;

        private NetMonitorSetting(Server server, NetBind netBind, String ipAddress, int port, String netProtocol) {
            this.server = server;
            this.netBind = netBind;
            this.ipAddress = ipAddress;
            this.port = port;
            this.netProtocol = netProtocol;
        }

        @Override
        public int compareTo(NetMonitorSetting o) {
            // Server
            int diff = server.compareTo(o.server);
            if(diff!=0) return diff;
            // IP
            diff = IPAddress.getIntForIPAddress(ipAddress) - IPAddress.getIntForIPAddress(o.ipAddress);
            if(diff!=0) return diff;
            // port
            if(port<o.port) return -1;
            if(port>o.port) return 1;
            // net protocol
            return netProtocol.compareTo(o.netProtocol);
        }

        @Override
        public boolean equals(Object O) {
            if(O==null) return false;
            if(!(O instanceof NetMonitorSetting)) return false;
            NetMonitorSetting other = (NetMonitorSetting)O;
            return
                port==other.port
                && server.equals(other.server)
                && netBind.equals(other.netBind)
                && ipAddress.equals(other.ipAddress)
                && netProtocol.equals(other.netProtocol)
            ;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 11 * hash + server.hashCode();
            hash = 11 * hash + netBind.hashCode();
            hash = 11 * hash + ipAddress.hashCode();
            hash = 11 * hash + port;
            hash = 11 * hash + netProtocol.hashCode();
            return hash;
        }

        /**
         * Gets the Server for this port.
         */
        Server getServer() {
            return server;
        }

        NetBind getNetBind() {
            return netBind;
        }

        /**
         * @return the ipAddress
         */
        String getIpAddress() {
            return ipAddress;
        }

        /**
         * @return the port
         */
        int getPort() {
            return port;
        }

        /**
         * @return the netProtocol
         */
        String getNetProtocol() {
            return netProtocol;
        }
    }

    private void verifyNetBinds() throws RemoteException, IOException, SQLException {
        assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

        final RootNodeImpl rootNode = ipAddressNode.ipAddressesNode.netDeviceNode._networkDevicesNode.serverNode.serversNode.rootNode;

        // The list of net binds is:
        //     The binds directly on the IP address plus the wildcard binds
        IPAddress ipAddress = ipAddressNode.getIPAddress();
        NetDevice netDevice = ipAddress.getNetDevice();
        List<NetBind> directNetBinds = ipAddress.getNetBinds();

        // Find the wildcard IP address, if available
        Server server = netDevice.getServer();
        IPAddress wildcard = null;
        for(IPAddress ia : server.getIPAddresses()) {
            if(ia.isWildcard()) {
                wildcard = ia;
                break;
            }
        }
        List<NetBind> wildcardNetBinds;
        if(wildcard==null) wildcardNetBinds = Collections.emptyList();
        else wildcardNetBinds = server.getNetBinds(wildcard);

        String ipAddressString = ipAddress.getIPAddress();
        List<NetMonitorSetting> netMonitorSettings = new ArrayList<NetMonitorSetting>(directNetBinds.size() + wildcardNetBinds.size());
        for(NetBind netBind : directNetBinds) {
            if(netBind.isMonitoringEnabled()) {
                netMonitorSettings.add(
                    new NetMonitorSetting(
                        server,
                        netBind,
                        ipAddressString,
                        netBind.getPort().getPort(),
                        netBind.getNetProtocol().getProtocol()
                    )
                );
            }
        }
        for(NetBind netBind : wildcardNetBinds) {
            if(netBind.isMonitoringEnabled()) {
                netMonitorSettings.add(
                    new NetMonitorSetting(
                        server,
                        netBind,
                        ipAddressString,
                        netBind.getPort().getPort(),
                        netBind.getNetProtocol().getProtocol()
                    )
                );
            }
        }
        Collections.sort(netMonitorSettings);

        synchronized(netBindNodes) {
            // Remove old ones
            Iterator<NetBindNode> netBindNodeIter = netBindNodes.iterator();
            while(netBindNodeIter.hasNext()) {
                NetBindNode netBindNode = netBindNodeIter.next();
                NetMonitorSetting netMonitorSetting = netBindNode.getNetMonitorSetting();
                if(!netMonitorSettings.contains(netMonitorSetting)) {
                    netBindNode.stop();
                    netBindNodeIter.remove();
                    rootNode.nodeRemoved();
                }
            }
            // Add new ones
            for(int c=0;c<netMonitorSettings.size();c++) {
                NetMonitorSetting netMonitorSetting = netMonitorSettings.get(c);
                if(c>=netBindNodes.size() || !netMonitorSetting.equals(netBindNodes.get(c).getNetMonitorSetting())) {
                    // Insert into proper index
                    NetBindNode netBindNode = new NetBindNode(this, netMonitorSetting, port, csf, ssf);
                    netBindNodes.add(c, netBindNode);
                    netBindNode.start();
                    rootNode.nodeAdded();
                }
            }
        }
    }

    File getPersistenceDirectory() throws IOException {
        File dir = new File(ipAddressNode.getPersistenceDirectory(), "net_binds");
        if(!dir.exists()) {
            if(!dir.mkdir()) {
                throw new IOException(
                    accessor.getMessage(
                        /*ipAddressNode.ipAddressesNode.netDeviceNode._networkDevicesNode.serverNode.serversNode.rootNode.locale,*/
                        "error.mkdirFailed",
                        dir.getCanonicalPath()
                    )
                );
            }
        }
        return dir;
    }
}
