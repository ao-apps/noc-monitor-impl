/*
 * Copyright 2008 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.noc.common.AlertLevel;
import com.aoindustries.noc.common.TableMultiResult;
import com.aoindustries.util.ErrorHandler;
import java.io.File;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import javax.swing.SwingUtilities;

/**
 * The workers for table multi-results node.
 * 
 * @author  AO Industries, Inc.
 */
abstract class TableMultiResultNodeWorker implements Runnable {

    /**
     * The most recent timer task
     */
    private final Object timerTaskLock = new Object();
    private RootNodeImpl.RunnableTimerTask timerTask;

    final private LinkedList<TableMultiResult> results = new LinkedList<TableMultiResult>();

    volatile private AlertLevel alertLevel = AlertLevel.UNKNOWN;

    final private List<TableMultiResultNodeImpl> tableMultiResultNodeImpls = new ArrayList<TableMultiResultNodeImpl>();

    final protected ErrorHandler errorHandler;
    final protected File newPersistenceFile;
    final protected File persistenceFile;
    final protected boolean gzipPersistenceFile;

    TableMultiResultNodeWorker(ErrorHandler errorHandler, File persistenceFile, File newPersistenceFile, boolean gzipPersistenceFile) {
        this.errorHandler = errorHandler;
        this.persistenceFile = persistenceFile;
        this.newPersistenceFile = newPersistenceFile;
        this.gzipPersistenceFile = gzipPersistenceFile;
    }

    /**
     * Gets an unmodifiable copy of the results.
     */
    final List<TableMultiResult> getResults() {
        synchronized(results) {
            return Collections.unmodifiableList(new ArrayList<TableMultiResult>(results));
        }
    }

    final AlertLevel getAlertLevel() {
        return alertLevel;
    }

    @SuppressWarnings("unchecked")
    private void start() {
        synchronized(results) {
            results.clear();
            try {
                File localPersistenceFile = this.persistenceFile;
                if(!localPersistenceFile.exists()) localPersistenceFile = this.newPersistenceFile;
                if(localPersistenceFile.exists()) {
                    ObjectInputStream in = new ObjectInputStream(gzipPersistenceFile ? new GZIPInputStream(new FileInputStream(localPersistenceFile)) : new FileInputStream(localPersistenceFile));
                    Collection<TableMultiResult> saved = (Collection)in.readObject();
                    in.close();
                    results.addAll(saved);
                }
            } catch(Exception err) {
                errorHandler.reportError(err, null);
            }
        }
        synchronized(timerTaskLock) {
            assert timerTask==null : "thread already started";
            timerTask = RootNodeImpl.schedule(this, TableResultNodeWorker.getNextStartupDelay());
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

            String error;
            List<?> rowData;
            AlertLevelAndMessage alertLevelAndMessage;
            try {
                error = null;
                if(useFutureTimeout()) {
                    Future<List<?>> future = RootNodeImpl.executorService.submit(
                        new Callable<List<?>>() {
                            @Override
                            public List<?> call() throws Exception {
                                return getRowData(locale);
                            }
                        }
                    );
                    rowData = future.get(5, TimeUnit.MINUTES);
                } else {
                    rowData = getRowData(locale);
                }
                synchronized(results) {
                    alertLevelAndMessage = getAlertLevelAndMessage(locale, rowData, results);
                }
                lastSuccessful = true;
            } catch(Exception err) {
                error = err.getLocalizedMessage();
                if(error==null) error = err.toString();
                rowData = null;
                alertLevelAndMessage = new AlertLevelAndMessage(
                    AlertLevel.CRITICAL,
                    ApplicationResourcesAccessor.getMessage(locale, "TableMultiResultNodeWorker.tableData.error", error)
                );
                lastSuccessful = false;
            }
            long pingNanos = System.nanoTime() - startNanos;

            synchronized(timerTaskLock) {if(timerTask==null) return;}

            TableMultiResult added = new TableMultiResult(
                startMillis,
                pingNanos,
                error,
                rowData,
                alertLevelAndMessage.getAlertLevel()
            );

            // Update the results storage
            TableMultiResult removed = null;
            synchronized(results) {
                results.addFirst(added);
                if(results.size()>getHistorySize()) removed = results.removeLast();
                BackgroundWriter.enqueueObject(persistenceFile, newPersistenceFile, errorHandler, results, gzipPersistenceFile);
            }

            tableMultiResultAdded(added);
            if(removed!=null) tableMultiResultRemoved(removed);

            AlertLevel curAlertLevel = alertLevel;
            if(curAlertLevel==AlertLevel.UNKNOWN) curAlertLevel = AlertLevel.NONE;
            AlertLevel maxAlertLevel = alertLevelAndMessage.getAlertLevel();
            AlertLevel newAlertLevel;
            if(maxAlertLevel==AlertLevel.UNKNOWN) {
                newAlertLevel = AlertLevel.UNKNOWN;
            } else if(maxAlertLevel.compareTo(curAlertLevel)<0) {
                // If maxAlertLevel < current, drop current to be the max
                newAlertLevel = maxAlertLevel;
            } else if(curAlertLevel.compareTo(maxAlertLevel)<0) {
                // If current < maxAlertLevel, increment by one
                newAlertLevel = AlertLevel.values()[curAlertLevel.ordinal()+1];
            } else {
                newAlertLevel = maxAlertLevel;
            }

            AlertLevel oldAlertLevel = alertLevel;
            if(newAlertLevel!=alertLevel) {
                alertLevel = newAlertLevel;
            }
            if(oldAlertLevel!=newAlertLevel) {
                synchronized(tableMultiResultNodeImpls) {
                    for(TableMultiResultNodeImpl tableMultiResultNodeImpl : tableMultiResultNodeImpls) {
                        tableMultiResultNodeImpl.nodeAlertLevelChanged(
                            oldAlertLevel,
                            newAlertLevel,
                            alertLevelAndMessage.getAlertMessage()
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

    final void addTableMultiResultNodeImpl(TableMultiResultNodeImpl tableMultiResultNodeImpl) {
        synchronized(tableMultiResultNodeImpls) {
            boolean needsStart = tableMultiResultNodeImpls.isEmpty();
            tableMultiResultNodeImpls.add(tableMultiResultNodeImpl);
            if(needsStart) start();
        }
    }

    final void removeTableMultiResultNodeImpl(TableMultiResultNodeImpl tableMultiResultNodeImpl) {
        // TODO: log error if wrong number of listeners matched
        synchronized(tableMultiResultNodeImpls) {
            if(tableMultiResultNodeImpls.isEmpty()) throw new AssertionError("tableMultiResultNodeImpls is empty");
            for(int c=tableMultiResultNodeImpls.size()-1;c>=0;c--) {
                if(tableMultiResultNodeImpls.get(c)==tableMultiResultNodeImpl) {
                    tableMultiResultNodeImpls.remove(c);
                    break;
                }
            }
            if(tableMultiResultNodeImpls.isEmpty()) {
                stop();
            }
        }
    }

    /**
     * Notifies all of the listeners.
     */
    private void tableMultiResultAdded(TableMultiResult result) {
        assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

        synchronized(tableMultiResultNodeImpls) {
            for(TableMultiResultNodeImpl tableMultiResultNodeImpl : tableMultiResultNodeImpls) {
                tableMultiResultNodeImpl.tableMultiResultAdded(result);
            }
        }
    }

    /**
     * Notifies all of the listeners.
     */
    private void tableMultiResultRemoved(TableMultiResult result) {
        assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

        synchronized(tableMultiResultNodeImpls) {
            for(TableMultiResultNodeImpl tableMultiResultNodeImpl : tableMultiResultNodeImpls) {
                tableMultiResultNodeImpl.tableMultiResultRemoved(result);
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
     * The number of history items to store.
     */
    protected abstract int getHistorySize();

    /**
     * Gets the current row data for this worker, any error should result in an exception.
     * This is the main monitor routine.
     */
    protected abstract List<?> getRowData(Locale locale) throws Exception;

    /**
     * Determines the alert level and message for the provided result and locale.
     * If unable to parse, may throw an exception to report the error.  This
     * should not block or delay for any reason.
     */
    protected abstract AlertLevelAndMessage getAlertLevelAndMessage(Locale locale, List<?> rowData, LinkedList<TableMultiResult> previousResults) throws Exception;
    
    /**
     * By default, the call to <code>getRowData</code> uses a <code>Future</code>
     * and times-out at 5 minutes.  If the monitoring check cannot block
     * indefinitely, it is more efficient to not use this decoupling.
     */
    protected boolean useFutureTimeout() {
        return true;
    }
}
