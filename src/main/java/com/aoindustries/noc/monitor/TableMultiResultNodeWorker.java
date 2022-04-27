/*
 * noc-monitor-impl - Implementation of Network Operations Center Monitoring.
 * Copyright (C) 2008-2012, 2014, 2016, 2018, 2020, 2021, 2022  AO Industries, Inc.
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
import com.aoapps.lang.i18n.ThreadLocale;
import com.aoapps.persistence.PersistentCollections;
import com.aoapps.persistence.PersistentLinkedList;
import com.aoapps.persistence.ProtectionLevel;
import com.aoapps.persistence.Serializer;
import static com.aoindustries.noc.monitor.Resources.PACKAGE_RESOURCES;
import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.TableMultiResult;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;

/**
 * The workers for table multi-results node.
 *
 * TODO: Instead of a fixed history size, aggregate data into larger time ranges and keep
 * track of mean, min, max, and standard deviation (or perhaps 5th/95th percentile?).  Keep
 * the following time ranges:
 * <pre>
 * 1 minute for 2 days = 2880 samples
 * 5 minutes for 5 days = 1440 samples
 * 15 minutes for 7 days = 672 samples
 * 30 minutes for 14 days = 672 samples
 * 1 hour for 28 days = 672 samples
 * 2 hours for 56 days = 672 samples
 * 4 hours for 112 days = 672 samples
 * 1 day forever beyond this
 * ==================================
 * total: 7680 samples + one per day beyond 224 days
 * </pre>
 * Update in a single background thread across all workers, and handle recovery from unexpected
 * shutdown gracefully by inserting aggregate before removing samples, and detect on next aggregation.
 * Also, the linked list should always be sorted by time descending, confirm this on aggregation pass.
 *
 * @author  AO Industries, Inc.
 */
public abstract class TableMultiResultNodeWorker<S, R extends TableMultiResult> implements Runnable {

  private static final Logger logger = Logger.getLogger(TableMultiResultNodeWorker.class.getName());

  /**
   * The most recent timer task
   */
  private final Object timerTaskLock = new Object();
  private Future<?> timerTask;

  private final PersistentLinkedList<R> results;

  private volatile AlertLevel alertLevel = null;
  private volatile Function<Locale, String> alertMessage;

  private final List<TableMultiResultNodeImpl<R>> tableMultiResultNodeImpls = new ArrayList<>();

  protected TableMultiResultNodeWorker(File persistenceFile, Serializer<R> serializer) throws IOException {
    this.results = new PersistentLinkedList<>(
        PersistentCollections.getPersistentBuffer(new RandomAccessFile(persistenceFile, "rw"), ProtectionLevel.BARRIER, Long.MAX_VALUE),
        //new RandomAccessFileBuffer(new RandomAccessFile(persistenceFile, "rw"), ProtectionLevel.NONE),
        /*
        new TwoCopyBarrierBuffer(
          persistenceFile,
          ProtectionLevel.BARRIER,
          4096, // Matches the block size of the underlying ext2 filesystem - hopefully matches the flash page size??? Can't find specs.
          60L*1000L, // TODO: Flash: 60L*60L*1000L, // Only commit once per 60 minutes in the single asynchronous writer thread
          5L*60L*1000L // TODO: Flash: 24L*60L*60L*1000L  // Only commit synchronously (concurrently) once per 24 hours to save flash writes
        ),
         */
        serializer
    );
  }

  /**
   * Gets an unmodifiable copy of the results.
   */
  final List<R> getResults() {
    //System.out.println("DEBUG: getResults");
    //try {
      synchronized (results) {
      return Collections.unmodifiableList(new ArrayList<>(results));
    }
    //} catch (ThreadDeath td) {
    //    throw td;
    //} catch (Throwable t) {
    //    ErrorPrinter.printStackTraces(t);
    //    throw t;
    //}
  }

  final AlertLevel getAlertLevel() {
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

  @SuppressWarnings("unchecked")
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

  private S getSampleWithTimeout() throws Exception {
    Future<S> future = RootNodeImpl.executors.getUnbounded().submit(this::getSample);
    try {
      return future.get(getFutureTimeout(), getFutureTimeoutUnit());
    } catch (InterruptedException | TimeoutException err) {
      cancel(future);
      throw err;
    } catch (ExecutionException err) {
      // Unwrap exception here
      Throwable cause = err.getCause();
      throw (cause instanceof Exception) ? (Exception) cause : err;
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
   * @see  TableResultNodeWorker#isIncrementalRampUp(boolean)
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
    try {
      long startMillis = System.currentTimeMillis();
      long startNanos = System.nanoTime();

      AlertLevel curAlertLevel = alertLevel;
      if (curAlertLevel == null) {
        curAlertLevel = AlertLevel.NONE;
      }

      String error;
      S sample;
      AlertLevelAndMessage alertLevelAndMessage;
      try {
        error = null;
        if (useFutureTimeout()) {
          sample = getSampleWithTimeout();
        } else {
          sample = getSample();
        }
        synchronized (results) {
          alertLevelAndMessage = getAlertLevelAndMessage(sample, results);
        }
        lastSuccessful = true;
      } catch (Exception err) {
        // Get error in default locale because it is persisted by serializer
        error = err.getLocalizedMessage();
        if (error == null || error.isEmpty()) {
          error = err.toString();
        }
        sample = null;
        alertLevelAndMessage = new AlertLevelAndMessage(
            // Don't downgrade UNKNOWN to CRITICAL on error
            EnumUtils.max(AlertLevel.CRITICAL, curAlertLevel),
            locale -> ThreadLocale.call(locale,
                () -> {
                  String msg = err.getLocalizedMessage();
                  if (msg == null || msg.isEmpty()) {
                    msg = err.toString();
                  }
                  return PACKAGE_RESOURCES.getMessage(locale, "TableMultiResultNodeWorker.tableData.error", msg);
                }
            )
        );
        lastSuccessful = false;
      }
      long pingNanos = System.nanoTime() - startNanos;

      synchronized (timerTaskLock) {
        if (timerTask == null) {
          return;
        }
      }

      if (error == null && sample == null) {
        throw new IllegalArgumentException("error and sample may not both be null");
      }
      if (error != null && sample != null) {
        throw new IllegalArgumentException("error and sample may not both be non-null");
      }

      R added;
      if (error != null) {
        added = newErrorResult(
            startMillis,
            pingNanos,
            alertLevelAndMessage.getAlertLevel(),
            error
        );
      } else {
        added = newSampleResult(
            startMillis,
            pingNanos,
            alertLevelAndMessage.getAlertLevel(),
            sample
        );
      }

      // Update the results
      R removed = null;
      synchronized (results) {
        results.addFirst(added);
        if (results.size() > getHistorySize()) {
          removed = results.removeLast();
        }
      }

      tableMultiResultAdded(added);
      if (removed != null) {
        tableMultiResultRemoved(removed);
      }

      AlertLevel maxAlertLevel = alertLevelAndMessage.getAlertLevel();
      AlertLevel newAlertLevel;
      if (maxAlertLevel == AlertLevel.UNKNOWN) {
        newAlertLevel = AlertLevel.UNKNOWN;
      } else if (maxAlertLevel.compareTo(curAlertLevel) < 0) {
        // If maxAlertLevel < current, drop current to be the max
        newAlertLevel = maxAlertLevel;
      } else if (isIncrementalRampUp(error != null) && curAlertLevel.compareTo(maxAlertLevel) < 0) {
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

      if (oldAlertLevel != newAlertLevel) {
        synchronized (tableMultiResultNodeImpls) {
          for (TableMultiResultNodeImpl<R> tableMultiResultNodeImpl : tableMultiResultNodeImpls) {
            tableMultiResultNodeImpl.nodeAlertLevelChanged(
                oldAlertLevel,
                newAlertLevel,
                alertLevelAndMessage.getAlertMessage()
            );
          }
        }
      }
    } catch (ThreadDeath td) {
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
              getSleepDelay(lastSuccessful, alertLevel)
          );
        }
      }
    }
  }

  final void addTableMultiResultNodeImpl(TableMultiResultNodeImpl<R> tableMultiResultNodeImpl) {
    synchronized (tableMultiResultNodeImpls) {
      boolean needsStart = tableMultiResultNodeImpls.isEmpty();
      assert !CollectionUtils.containsByIdentity(tableMultiResultNodeImpls, tableMultiResultNodeImpl);
      tableMultiResultNodeImpls.add(tableMultiResultNodeImpl);
      if (needsStart) {
        start();
      }
    }
  }

  final void removeTableMultiResultNodeImpl(TableMultiResultNodeImpl<R> tableMultiResultNodeImpl) {
    synchronized (tableMultiResultNodeImpls) {
      if (tableMultiResultNodeImpls.isEmpty()) {
        throw new AssertionError("tableMultiResultNodeImpls is empty");
      }
      boolean found = false;
      for (int c = tableMultiResultNodeImpls.size() - 1; c >= 0; c--) {
        if (tableMultiResultNodeImpls.get(c) == tableMultiResultNodeImpl) {
          tableMultiResultNodeImpls.remove(c);
          found = true;
          break;
        }
      }
      if (!found && logger.isLoggable(Level.WARNING)) {
        logger.log(Level.WARNING, "tableMultiResultNodeImpl not found in tableMultiResultNodeImpls: " + tableMultiResultNodeImpl);
      }
      assert !CollectionUtils.containsByIdentity(tableMultiResultNodeImpls, tableMultiResultNodeImpl);
      if (tableMultiResultNodeImpls.isEmpty()) {
        stop();
      }
    }
  }

  /**
   * Notifies all of the listeners.
   */
  private void tableMultiResultAdded(R result) {
    assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

    synchronized (tableMultiResultNodeImpls) {
      for (TableMultiResultNodeImpl<R> tableMultiResultNodeImpl : tableMultiResultNodeImpls) {
        tableMultiResultNodeImpl.tableMultiResultAdded(result);
      }
    }
  }

  /**
   * Notifies all of the listeners.
   */
  private void tableMultiResultRemoved(R result) {
    assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

    synchronized (tableMultiResultNodeImpls) {
      for (TableMultiResultNodeImpl<R> tableMultiResultNodeImpl : tableMultiResultNodeImpls) {
        tableMultiResultNodeImpl.tableMultiResultRemoved(result);
      }
    }
  }

  /**
   * The default sleep delay is five minutes when successful
   * or one minute when unsuccessful.
   *
   * @param  alertLevel  When {@code null}, treated as {@link AlertLevel#UNKNOWN}
   */
  protected long getSleepDelay(boolean lastSuccessful, AlertLevel alertLevel) {
    return (lastSuccessful && alertLevel == AlertLevel.NONE) ? (5L * 60 * 1000) : (60L * 1000);
  }

  /**
   * The number of history items to store.
   */
  protected abstract int getHistorySize();

  /**
   * This is the main monitor routine.
   * Gets the current sample for this worker, any error should result in an exception.
   * The sample may be any object that encapsulates the state of the resource in order
   * to determine its alert level, alert message, and overall result.
   */
  protected abstract S getSample() throws Exception;

  /**
   * Creates a new result container object for error condition.
   */
  protected abstract R newErrorResult(long time, long latency, AlertLevel alertLevel, String error);

  /**
   * Creates a new result container object for success condition.
   */
  protected abstract R newSampleResult(long time, long latency, AlertLevel alertLevel, S sample);

  /**
   * Cancels the current getSample call on a best-effort basis.
   * Implementations of this method <b>must not block</b>.
   * This default implementation calls <code>future.cancel(true)</code>.
   */
  protected void cancel(Future<S> future) {
    future.cancel(true);
  }

  /**
   * Determines the alert level and message for the provided result.
   * If unable to parse, may throw an exception to report the error.  This
   * should not block or delay for any reason.
   */
  protected abstract AlertLevelAndMessage getAlertLevelAndMessage(S sample, Iterable<? extends R> previousResults) throws Exception;

  /**
   * By default, the call to <code>getSample</code> uses a <code>Future</code>
   * and times-out at 5 minutes.  If the monitoring check cannot block
   * indefinitely, it is more efficient to not use this decoupling.
   */
  protected boolean useFutureTimeout() {
    return true;
  }

  /**
   * The default future timeout is 5 minutes.
   */
  protected long getFutureTimeout() {
    return 5;
  }

  /**
   * The default future timeout unit is MINUTES.
   *
   * @see  TimeUnit#MINUTES
   */
  protected TimeUnit getFutureTimeoutUnit() {
    return TimeUnit.MINUTES;
  }
}
