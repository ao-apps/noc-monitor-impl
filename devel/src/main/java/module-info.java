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
module com.aoindustries.noc.monitor.devel {
  exports com.aoindustries.noc.monitor.i18n;
  exports com.aoindustries.noc.monitor.backup.i18n;
  exports com.aoindustries.noc.monitor.cluster.i18n;
  exports com.aoindustries.noc.monitor.dns.i18n;
  exports com.aoindustries.noc.monitor.email.i18n;
  exports com.aoindustries.noc.monitor.infrastructure.i18n;
  exports com.aoindustries.noc.monitor.linux.i18n;
  exports com.aoindustries.noc.monitor.mysql.i18n;
  exports com.aoindustries.noc.monitor.net.i18n;
  exports com.aoindustries.noc.monitor.pki.i18n;
  exports com.aoindustries.noc.monitor.signup.i18n;
  exports com.aoindustries.noc.monitor.web.i18n;
  // Direct
  requires com.aoapps.hodgepodge; // <groupId>com.aoapps</groupId><artifactId>ao-hodgepodge</artifactId>
  requires static jsr305; // <groupId>com.google.code.findbugs</groupId><artifactId>jsr305</artifactId>
  // Java SE
  requires java.logging;
}
