/*
 * noc-monitor-impl - Implementation of Network Operations Center Monitoring.
 * Copyright (C) 2008, 2009, 2016, 2018, 2020, 2021, 2022  AO Industries, Inc.
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

import com.aoapps.lang.EnumUtils;
import com.aoapps.lang.function.SerializableFunction;
import com.aoapps.lang.i18n.ThreadLocale;
import static com.aoindustries.noc.monitor.Resources.PACKAGE_RESOURCES;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.TableResult;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;

/**
 * The workers for table results node.
 *
 * TODO: Add persistence of the last report
 *
 * @author  AO Industries, Inc.
 */
public abstract class TableResultNodeWorker<QR, TD> implements Runnable {

  private static final Logger logger = Logger.getLogger(TableResultNodeWorker.class.getName());

  /**
   * The most recent timer task
   */
  private final Object timerTaskLock = new Object();
  private Future<?> timerTask;

  private volatile TableResult lastResult;
  private volatile AlertLevel alertLevel = null;
  private volatile Function<Locale, String> alertMessage = null;

  private final List<TableResultNodeImpl> tableResultNodeImpls = new ArrayList<>();

  protected final File persistenceFile;

  protected TableResultNodeWorker(File persistenceFile) {
    this.persistenceFile = persistenceFile;
  }

  final TableResult getLastResult() {
    return lastResult;
  }

  public final AlertLevel getAlertLevel() {
    return alertLevel;
  }

  final Function<Locale, String> getAlertMessage() {
    return alertMessage;
  }

  /**
   * The default startup delay is within five minutes.
   */
  protected int getNextStartupDelay() {
    return RootNodeImpl.getNextStartupDelayFiveMinutes();
  }

  private void start() {
    synchronized (timerTaskLock) {
      assert timerTask == null : "thread already started";
      timerTask = RootNodeImpl.schedule(this, getNextStartupDelay());
    }
  }

  private void stop() {
    synchronized (timerTaskLock) {
      if (timerTask != null) {
        timerTask.cancel(true);
        timerTask = null;
      }
    }
  }

  private QR getQueryResultWithTimeout() throws InterruptedException, TimeoutException, Exception {
    Future<QR> future = RootNodeImpl.executors.getUnbounded().submit(this::getQueryResult);
    try {
      return future.get(getTimeout(), getTimeoutUnit());
    } catch (InterruptedException | TimeoutException err) {
      cancel(future);
      throw err;
    }
  }

  /**
   * Enables incremental alert level ramp-up, where the node's alert level
   * is only incremented one step at a time per monitoring pass.  This makes
   * the resource more tolerant of intermittent problems, at the cost of
   * slower reaction time.
   * <p>
   * <b>Implementation Note:</b><br>
   * Enabled by default
   * </p>
   *
   * @see  SingleResultNodeWorker#isIncrementalRampUp(boolean)
   * @see  TableMultiResultNodeWorker#isIncrementalRampUp(boolean)
   */
  protected boolean isIncrementalRampUp(boolean isError) {
    return true;
  }

  @Override
  @SuppressWarnings({"UseSpecificCatch", "TooBroadCatch"})
  public final void run() {
    assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

    boolean lastSuccessful = false;
    synchronized (timerTaskLock) {
      if (timerTask == null) {
        return;
      }
    }
    AlertLevel maxAlertLevel = alertLevel;
    try {
      long startMillis = System.currentTimeMillis();
      long startNanos = System.nanoTime();

      AlertLevel curAlertLevel = alertLevel;
      if (curAlertLevel == null) {
        curAlertLevel = AlertLevel.NONE;
      }

      int columns;
      int rows;
      SerializableFunction<Locale, ? extends List<String>> columnHeaders;
      SerializableFunction<Locale, ? extends List<?>> tableData;
      List<AlertLevel> alertLevels;
      boolean isError;
      try {
        QR queryResult = getQueryResultWithTimeout();
        SerializableFunction<Locale, ? extends List<? extends TD>> successfulTableData = getTableData(queryResult);
        columns = getColumns();
        rows = successfulTableData.apply(Locale.getDefault()).size() / columns; // TODO: Is possible to delay getting number of rows until locale known?
        columnHeaders = getColumnHeaders();
        alertLevels = Collections.unmodifiableList(getAlertLevels(queryResult));
        isError = false;
        lastSuccessful = true;
        tableData = successfulTableData;
      } catch (Exception err) {
        columns = 1;
        rows = 1;
        columnHeaders = locale -> Collections.singletonList(
          PACKAGE_RESOURCES.getMessage(locale, "TableResultNodeWorker.columnHeaders.error")
        );
        tableData = locale -> ThreadLocale.call(locale,
          () -> {
            String msg = err.getLocalizedMessage();
            if (msg == null || msg.isEmpty()) {
              msg = err.toString();
            }
            return Collections.singletonList(
              PACKAGE_RESOURCES.getMessage(locale, "TableResultNodeWorker.tableData.error", msg)
            );
          }
        );
        alertLevels = Collections.singletonList(
          // Don't downgrade UNKNOWN to CRITICAL on error
          EnumUtils.max(AlertLevel.CRITICAL, curAlertLevel)
        );
        isError = true;
        lastSuccessful = false;
      }
      long pingNanos = System.nanoTime() - startNanos;

      synchronized (timerTaskLock) {
        if (timerTask == null) {
          return;
        }
      }

      TableResult result = new TableResult(
        startMillis,
        pingNanos,
        isError,
        columns,
        rows,
        columnHeaders,
        tableData,
        alertLevels
      );
      lastResult = result;

      AlertLevelAndMessage alertLevelAndMessage = getAlertLevelAndMessage(curAlertLevel, result);
      maxAlertLevel = alertLevelAndMessage.getAlertLevel();
      AlertLevel newAlertLevel;
      // TODO: Immediate jump to UNKNOWN like TableMultiResultNodeWorker?
      if (maxAlertLevel.compareTo(curAlertLevel) < 0) {
        // If maxAlertLevel < current, drop current to be the max
        newAlertLevel = maxAlertLevel;
      } else if (isIncrementalRampUp(isError) && curAlertLevel.compareTo(maxAlertLevel) < 0) {
        // If current < maxAlertLevel, increment by one
        newAlertLevel = AlertLevel.fromOrdinal(curAlertLevel.ordinal() + 1);
      } else {
        newAlertLevel = maxAlertLevel;
      }

      AlertLevel oldAlertLevel = alertLevel;
      if (oldAlertLevel == null) {
        oldAlertLevel = AlertLevel.UNKNOWN;
      }
      alertLevel = newAlertLevel;
      alertMessage = alertLevelAndMessage.getAlertMessage();
      tableResultUpdated(result);
      if (oldAlertLevel != newAlertLevel) {
        synchronized (tableResultNodeImpls) {
          for (TableResultNodeImpl tableResultNodeImpl : tableResultNodeImpls) {
            tableResultNodeImpl.nodeAlertLevelChanged(
              oldAlertLevel,
              newAlertLevel,
              alertMessage
            );
          }
        }
      }
    } catch (ThreadDeath td) {
      lastSuccessful = false;
      throw td;
    } catch (Throwable t) {
      logger.log(Level.SEVERE, null, t);
      lastSuccessful = false;
    } finally {
      // Reschedule next timer task if still running
      synchronized (timerTaskLock) {
        if (timerTask != null) {
          timerTask = RootNodeImpl.schedule(
            this,
            getSleepDelay(lastSuccessful, maxAlertLevel)
          );
        }
      }
    }
  }

  /**
   * Gets the timeout value.  Defaults to <code>5</code>.
   */
  protected long getTimeout() {
    return 5;
  }

  /**
   * Gets the timeout time unit.  Defaults to <code>TimeUnit.MINUTES</code>.
   */
  protected TimeUnit getTimeoutUnit() {
    return TimeUnit.MINUTES;
  }

  final void addTableResultNodeImpl(TableResultNodeImpl tableResultNodeImpl) {
    synchronized (tableResultNodeImpls) {
      boolean needsStart = tableResultNodeImpls.isEmpty();
      assert !CollectionUtils.containsByIdentity(tableResultNodeImpls, tableResultNodeImpl);
      tableResultNodeImpls.add(tableResultNodeImpl);
      if (needsStart) {
        start();
      }
    }
  }

  final void removeTableResultNodeImpl(TableResultNodeImpl tableResultNodeImpl) {
    synchronized (tableResultNodeImpls) {
      if (tableResultNodeImpls.isEmpty()) {
        throw new AssertionError("tableResultNodeImpls is empty");
      }
      boolean found = false;
      for (int c=tableResultNodeImpls.size()-1;c >= 0;c--) {
        if (tableResultNodeImpls.get(c) == tableResultNodeImpl) {
          tableResultNodeImpls.remove(c);
          found = true;
          break;
        }
      }
      if (!found && logger.isLoggable(Level.WARNING)) {
        logger.log(Level.WARNING, "tableResultNodeImpl not found in tableResultNodeImpls: " + tableResultNodeImpl);
      }
      assert !CollectionUtils.containsByIdentity(tableResultNodeImpls, tableResultNodeImpl);
      if (tableResultNodeImpls.isEmpty()) {
        stop();
      }
    }
  }

  /**
   * Notifies all of the listeners.
   */
  private void tableResultUpdated(TableResult tableResult) {
    assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

    synchronized (tableResultNodeImpls) {
      for (TableResultNodeImpl tableResultNodeImpl : tableResultNodeImpls) {
        tableResultNodeImpl.tableResultUpdated(tableResult);
      }
    }
  }

  /**
   * The default sleep delay is five minutes when successful or
   * one minute when unsuccessful.
   *
   * @param  alertLevel  When {@code null}, treated as {@link AlertLevel#UNKNOWN}
   */
  protected long getSleepDelay(boolean lastSuccessful, AlertLevel alertLevel) {
    return (lastSuccessful && alertLevel == AlertLevel.NONE) ? (5L * 60 * 1000) : (60L * 1000);
  }

  /**
   * Determines the alert level and message for the provided result.  This result may also represent the error state.
   * The error state will always have columns=1, rows=1, and tableData.size() == 1
   */
  public abstract AlertLevelAndMessage getAlertLevelAndMessage(AlertLevel curAlertLevel, TableResult result);

  /**
   * Gets the number of columns in the table data.
   */
  protected abstract int getColumns();

  /**
   * Gets the column headers.
   */
  protected abstract SerializableFunction<Locale, ? extends List<String>> getColumnHeaders();

  /**
   * Gets the current table data for this worker.
   */
  protected abstract QR getQueryResult() throws InterruptedException, Exception;

  /**
   * Gets the table data for the query result.  This must be processed quickly.
   */
  protected abstract SerializableFunction<Locale, ? extends List<? extends TD>> getTableData(QR queryResult) throws Exception;

  /**
   * Cancels the current getTableData call on a best-effort basis.
   * Implementations of this method <b>must not block</b>.
   * This default implementation calls <code>future.cancel(true)</code>.
   */
  protected void cancel(Future<QR> future) {
    future.cancel(true);
  }

  /**
   * Gets the alert levels for the provided data.
   */
  protected abstract List<AlertLevel> getAlertLevels(QR queryResult);
}
