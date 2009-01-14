/*
 * Copyright 2008-2009 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.noc.common.AlertLevel;
import com.aoindustries.noc.common.TableResult;
import com.aoindustries.util.ErrorHandler;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;

/**
 * The workers for table results node.
 *
 * TODO: Add persistence of the last report
 *
 * @author  AO Industries, Inc.
 */
abstract class TableResultNodeWorker implements Runnable {

    /**
     * The most recent timer task
     */
    private final Object timerTaskLock = new Object();
    private RootNodeImpl.RunnableTimerTask timerTask;

    volatile private TableResult lastResult;
    volatile private AlertLevel alertLevel = AlertLevel.UNKNOWN;
    volatile private String alertMessage = null;

    final private List<TableResultNodeImpl> tableResultNodeImpls = new ArrayList<TableResultNodeImpl>();

    final protected ErrorHandler errorHandler;
    final protected File persistenceFile;

    TableResultNodeWorker(ErrorHandler errorHandler, File persistenceFile) {
        this.errorHandler = errorHandler;
        this.persistenceFile = persistenceFile;
    }

    final TableResult getLastResult() {
        return lastResult;
    }

    final AlertLevel getAlertLevel() {
        return alertLevel;
    }

    final String getAlertMessage() {
        return alertMessage;
    }

    private static int lastStartupDelay;
    private static final Object lastStartupDelayLock = new Object();
    static int getNextStartupDelay() {
        synchronized(lastStartupDelayLock) {
            lastStartupDelay = (lastStartupDelay+5000)%60037;
            return lastStartupDelay;
        }
    }

    private void start() {
        synchronized(timerTaskLock) {
            assert timerTask==null : "thread already started";
            timerTask = RootNodeImpl.schedule(this, getNextStartupDelay());
        }
    }

    private void stop() {
        synchronized(timerTaskLock) {
            if(timerTask!=null) {
                timerTask.cancel();
                Future<?> future = timerTask.getFuture();
                if(future!=null) {
                    future.cancel(true);
                }
                timerTask = null;
            }
        }
    }

    @Override
    final public void run() {
        assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

        boolean lastSuccessful = false;
        synchronized(timerTaskLock) {if(timerTask==null) return;}
        try {
            long startMillis = System.currentTimeMillis();
            long startNanos = System.nanoTime();
            
            lastSuccessful = false;
            
            final Locale locale = Locale.getDefault();

            int columns;
            int rows;
            List<?> columnHeaders;
            List<?> tableData;
            List<AlertLevel> alertLevels;
            boolean isError;
            try {
                Future<List<?>> future = RootNodeImpl.executorService.submit(
                    new Callable<List<?>>() {
                        public List<?> call() throws Exception {
                            return getTableData(locale);
                        }
                    }
                );
                tableData = future.get(5, TimeUnit.MINUTES);
                columns = getColumns();
                rows = tableData.size()/columns;
                columnHeaders = getColumnHeaders(locale);
                alertLevels = getAlertLevels(tableData);
                isError = false;
                lastSuccessful = true;
            } catch(Exception err) {
                String error = err.getLocalizedMessage();
                columns = 1;
                rows = 1;
                columnHeaders = Collections.singletonList(
                    ApplicationResourcesAccessor.getMessage(locale, "TableResultNodeWorker.columnHeaders.error")
                );
                tableData = Collections.singletonList(
                    ApplicationResourcesAccessor.getMessage(locale, "TableResultNodeWorker.tableData.error", error)
                );
                alertLevels = Collections.singletonList(AlertLevel.CRITICAL);
                isError = true;
                lastSuccessful = false;
            }
            long pingNanos = System.nanoTime() - startNanos;

            synchronized(timerTaskLock) {if(timerTask==null) return;}

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

            AlertLevel curAlertLevel = alertLevel;
            if(curAlertLevel==AlertLevel.UNKNOWN) curAlertLevel = AlertLevel.NONE;
            AlertLevelAndMessage alertLevelAndMessage = getAlertLevelAndMessage(locale, result);
            AlertLevel maxAlertLevel = alertLevelAndMessage.getAlertLevel();
            AlertLevel newAlertLevel;
            if(maxAlertLevel.compareTo(curAlertLevel)<0) {
                // If maxAlertLevel < current, drop current to be the max
                newAlertLevel = maxAlertLevel;
            } else if(curAlertLevel.compareTo(maxAlertLevel)<0) {
                // If current < maxAlertLevel, increment by one
                newAlertLevel = AlertLevel.values()[curAlertLevel.ordinal()+1];
            } else {
                newAlertLevel = maxAlertLevel;
            }

            AlertLevel oldAlertLevel = alertLevel;
            alertLevel = newAlertLevel;
            alertMessage = alertLevelAndMessage.getAlertMessage();
            tableResultUpdated(result);
            if(oldAlertLevel!=newAlertLevel) {
                synchronized(tableResultNodeImpls) {
                    for(TableResultNodeImpl tableResultNodeImpl : tableResultNodeImpls) {
                        tableResultNodeImpl.nodeAlertLevelChanged(
                            oldAlertLevel,
                            newAlertLevel,
                            result
                        );
                    }
                }
            }
        } catch(Exception err) {
            errorHandler.reportError(err, null);
            lastSuccessful = false;
        } finally {
            // Reschedule next timer task if still running
            synchronized(timerTaskLock) {
                if(timerTask!=null) {
                    timerTask = RootNodeImpl.schedule(
                        this,
                        getSleepDelay(lastSuccessful)
                    );
                }
            }
        }
    }

    final void addTableResultNodeImpl(TableResultNodeImpl tableResultNodeImpl) {
        synchronized(tableResultNodeImpls) {
            boolean needsStart = tableResultNodeImpls.isEmpty();
            tableResultNodeImpls.add(tableResultNodeImpl);
            if(needsStart) start();
        }
    }

    final void removeTableResultNodeImpl(TableResultNodeImpl tableResultNodeImpl) {
        // TODO: log error if wrong number of listeners matched
        synchronized(tableResultNodeImpls) {
            if(tableResultNodeImpls.isEmpty()) throw new AssertionError("tableResultNodeImpls is empty");
            for(int c=tableResultNodeImpls.size()-1;c>=0;c--) {
                if(tableResultNodeImpls.get(c)==tableResultNodeImpl) {
                    tableResultNodeImpls.remove(c);
                    break;
                }
            }
            if(tableResultNodeImpls.isEmpty()) {
                stop();
            }
        }
    }

    /**
     * Notifies all of the listeners.
     */
    private void tableResultUpdated(TableResult tableResult) {
        assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

        synchronized(tableResultNodeImpls) {
            for(TableResultNodeImpl tableResultNodeImpl : tableResultNodeImpls) {
                tableResultNodeImpl.tableResultUpdated(tableResult);
            }
        }
    }

    /**
     * The default sleep delay is one minute.
     */
    protected long getSleepDelay(boolean lastSuccessful) {
        return 60000;
    }

    /**
     * Determines the alert level and message for the provided result and locale.  This result may also represent the error state.
     * The error state will always have columns=1, rows=1, and tableData.size()==1
     */
    protected abstract AlertLevelAndMessage getAlertLevelAndMessage(Locale locale, TableResult result);

    /**
     * Gets the number of columns in the table data.
     */
    protected abstract int getColumns();

    /**
     * Gets the column headers.
     */
    protected abstract List<?> getColumnHeaders(Locale locale);

    /**
     * Gets the current table data for this worker.
     */
    protected abstract List<?> getTableData(Locale locale) throws Exception;
    
    /**
     * Gets the alert levels for the provided data.
     */
    protected abstract List<AlertLevel> getAlertLevels(List<?> tableData);
}
