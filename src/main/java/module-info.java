/*
 * noc-monitor-impl - Implementation of Network Operations Center Monitoring.
 * Copyright (C) 2021, 2022  AO Industries, Inc.
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
module com.aoindustries.noc.monitor.impl {
  exports com.aoindustries.noc.monitor;
  exports com.aoindustries.noc.monitor.backup;
  exports com.aoindustries.noc.monitor.cluster;
  exports com.aoindustries.noc.monitor.dns;
  exports com.aoindustries.noc.monitor.email;
  exports com.aoindustries.noc.monitor.infrastructure;
  exports com.aoindustries.noc.monitor.linux;
  exports com.aoindustries.noc.monitor.mysql;
  exports com.aoindustries.noc.monitor.net;
  exports com.aoindustries.noc.monitor.pki;
  exports com.aoindustries.noc.monitor.signup;
  exports com.aoindustries.noc.monitor.web;
  // Direct
  requires com.aoapps.collections; // <groupId>com.aoapps</groupId><artifactId>ao-collections</artifactId>
  requires com.aoapps.concurrent; // <groupId>com.aoapps</groupId><artifactId>ao-concurrent</artifactId>
  requires com.aoapps.dbc; // <groupId>com.aoapps</groupId><artifactId>ao-dbc</artifactId>
  requires com.aoapps.hodgepodge; // <groupId>com.aoapps</groupId><artifactId>ao-hodgepodge</artifactId>
  requires com.aoapps.lang; // <groupId>com.aoapps</groupId><artifactId>ao-lang</artifactId>
  requires com.aoapps.net.types; // <groupId>com.aoapps</groupId><artifactId>ao-net-types</artifactId>
  requires com.aoapps.persistence; // <groupId>com.aoapps</groupId><artifactId>ao-persistence</artifactId>
  requires com.aoapps.sql; // <groupId>com.aoapps</groupId><artifactId>ao-sql</artifactId>
  requires com.aoindustries.aoserv.client; // <groupId>com.aoindustries</groupId><artifactId>aoserv-client</artifactId>
  requires com.aoindustries.aoserv.cluster; // <groupId>com.aoindustries</groupId><artifactId>aoserv-cluster</artifactId>
  requires org.dnsjava; // <groupId>dnsjava</groupId><artifactId>dnsjava</artifactId>
  requires com.aoindustries.noc.monitor.api; // <groupId>com.aoindustries</groupId><artifactId>noc-monitor-api</artifactId>
  requires com.aoindustries.noc.monitor.portmon; // <groupId>com.aoindustries</groupId><artifactId>noc-monitor-portmon</artifactId>
  // Java SE
  requires java.desktop;
  requires java.logging;
  requires java.rmi;
  requires java.sql;
}
