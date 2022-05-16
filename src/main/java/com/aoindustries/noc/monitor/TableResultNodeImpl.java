/*
 * noc-monitor-impl - Implementation of Network Operations Center Monitoring.
 * Copyright (C) 2008, 2009, 2014, 2018, 2020, 2021, 2022  AO Industries, Inc.
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

import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.TableResult;
import com.aoindustries.noc.monitor.common.TableResultListener;
import com.aoindustries.noc.monitor.common.TableResultNode;
import java.io.IOException;
import java.rmi.RemoteException;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;

/**
 * The node for table results.
 *
 * @author  AO Industries, Inc.
 */
public abstract class TableResultNodeImpl extends NodeImpl implements TableResultNode {

  private static final Logger logger = Logger.getLogger(TableResultNodeImpl.class.getName());

  private static final long serialVersionUID = 1L;

  public final RootNodeImpl rootNode;
  final NodeImpl parent;
  protected final TableResultWorker<?, ?> worker;

  private final List<TableResultListener> tableResultListeners = new ArrayList<>();

  protected TableResultNodeImpl(RootNodeImpl rootNode, NodeImpl parent, TableResultWorker<?, ?> worker, int port, RMIClientSocketFactory csf, RMIServerSocketFactory ssf) throws RemoteException {
    super(port, csf, ssf);
    this.rootNode = rootNode;
    this.parent = parent;
    this.worker = worker;
  }

  @Override
  public final NodeImpl getParent() {
    return parent;
  }

  @Override
  public boolean getAllowsChildren() {
    return false;
  }

  @Override
  public List<? extends NodeImpl> getChildren() {
    return Collections.emptyList();
  }

  @Override
  public AlertLevel getAlertLevel() {
    AlertLevel alertLevel = worker.getAlertLevel();
    return constrainAlertLevel(alertLevel == null ? AlertLevel.UNKNOWN : alertLevel);
  }

  @Override
  public final String getAlertMessage() {
    Function<Locale, String> alertMessage = worker.getAlertMessage();
    return alertMessage == null ? null : alertMessage.apply(rootNode.locale);
  }

  public void start() throws IOException {
    worker.addTableResultNodeImpl(this);
  }

  public void stop() {
    worker.removeTableResultNodeImpl(this);
  }

  @Override
  public final TableResult getLastResult() {
    return worker.getLastResult();
  }

  /**
   * Called by the worker when the alert level changes.
   */
  final void nodeAlertLevelChanged(AlertLevel oldAlertLevel, AlertLevel newAlertLevel, Function<Locale, String> alertMessage) throws RemoteException {
    assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

    rootNode.nodeAlertLevelChanged(
        this,
        constrainAlertLevel(oldAlertLevel),
        constrainAlertLevel(newAlertLevel),
        alertMessage == null ? null : alertMessage.apply(rootNode.locale)
    );
  }

  @Override
  public final void addTableResultListener(TableResultListener tableResultListener) {
    synchronized (tableResultListeners) {
      tableResultListeners.add(tableResultListener);
    }
  }

  @Override
  public final void removeTableResultListener(TableResultListener tableResultListener) {
    synchronized (tableResultListeners) {
      for (int c = tableResultListeners.size() - 1; c >= 0; c--) {
        if (tableResultListeners.get(c).equals(tableResultListener)) {
          tableResultListeners.remove(c);
          // Remove only once, in case add and remove come in out of order with quick GUI changes
          return;
        }
      }
    }
    logger.log(Level.WARNING, null, new AssertionError("Listener not found: " + tableResultListener));
  }

  /**
   * Notifies all of the listeners.
   */
  final void tableResultUpdated(TableResult tableResult) {
    assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

    synchronized (tableResultListeners) {
      Iterator<TableResultListener> iter = tableResultListeners.iterator();
      while (iter.hasNext()) {
        TableResultListener tableResultListener = iter.next();
        try {
          tableResultListener.tableResultUpdated(tableResult);
        } catch (RemoteException err) {
          iter.remove();
          logger.log(Level.SEVERE, null, err);
        }
      }
    }
  }
}
