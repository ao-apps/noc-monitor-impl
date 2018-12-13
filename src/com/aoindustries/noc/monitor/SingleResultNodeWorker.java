/*
 * Copyright 2008-2012, 2016, 2018 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import com.aoindustries.noc.monitor.common.AlertLevel;
import com.aoindustries.noc.monitor.common.SerializableFunction;
import com.aoindustries.noc.monitor.common.SingleResult;
import com.aoindustries.util.i18n.ThreadLocale;
import java.io.File;
import java.util.ArrayList;
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
 * The workers for single results node.
 *
 * TODO: Add persistence of the last report
 *
 * @author  AO Industries, Inc.
 */
abstract class SingleResultNodeWorker implements Runnable {

	private static final Logger logger = Logger.getLogger(SingleResultNodeWorker.class.getName());

	/**
	 * The most recent timer task
	 */
	private final Object timerTaskLock = new Object();
	private Future<?> timerTask;

	volatile private SingleResult lastResult;
	volatile private AlertLevel alertLevel;
	volatile private Function<Locale,String> alertMessage = null;

	final private List<SingleResultNodeImpl> singleResultNodeImpls = new ArrayList<>();

	final protected File persistenceFile;

	SingleResultNodeWorker(File persistenceFile) {
		this.persistenceFile = persistenceFile;
	}

	final SingleResult getLastResult() {
		return lastResult;
	}

	final AlertLevel getAlertLevel() {
		return alertLevel;
	}

	final Function<Locale,String> getAlertMessage() {
		return alertMessage;
	}

	/**
	 * The default startup delay is within five minutes.
	 */
	protected int getNextStartupDelay() {
		return RootNodeImpl.getNextStartupDelayFiveMinutes();
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
				timerTask.cancel(true);
				timerTask = null;
			}
		}
	}

	private String getReportWithTimeout() throws Exception {
		Future<String> future = RootNodeImpl.executors.getUnbounded().submit(this::getReport);
		try {
			return future.get(5, TimeUnit.MINUTES);
		} catch(InterruptedException | TimeoutException err) {
			cancel(future);
			throw err;
		}
	}

	/**
	 * Enables incremental alert level ramp-up, where the node's alert level
	 * is only incremented one step at a time per monitoring pass.  This makes
	 * the resource more tolerant of intermittent problems, at the cost of
	 * slower reaction time.
	 *
	 * @implSpec  Enabled by default
	 *
	 * @see  TableMultiResultNodeWorker#isIncrementalRampUp(boolean)
	 * @see  TableResultNodeWorker#isIncrementalRampUp(boolean)
	 */
	protected boolean isIncrementalRampUp(boolean isError) {
		return true;
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

			SerializableFunction<Locale,String> error;
			String report;
			try {
				error = null;
				report = getReportWithTimeout();
				if(report==null) throw new NullPointerException("report is null");
				lastSuccessful = true;
			} catch(Exception err) {
				error = locale -> ThreadLocale.set(
					locale,
					(ThreadLocale.Supplier<String>)() -> {
						String msg = err.getLocalizedMessage();
						if(msg == null || msg.isEmpty()) msg = err.toString();
						return msg;
					}
				);
				report = null;
				lastSuccessful = false;
			}
			long pingNanos = System.nanoTime() - startNanos;

			synchronized(timerTaskLock) {if(timerTask==null) return;}

			SingleResult result = new SingleResult(
				startMillis,
				pingNanos,
				error,
				report
			);
			lastResult = result;

			AlertLevel curAlertLevel = alertLevel;
			if(curAlertLevel == null) curAlertLevel = AlertLevel.NONE;
			AlertLevelAndMessage alertLevelAndMessage = getAlertLevelAndMessage(curAlertLevel, result);
			AlertLevel maxAlertLevel = alertLevelAndMessage.getAlertLevel();
			AlertLevel newAlertLevel;
			// TODO: Immediate jump to UNKNOWN like TableMultiResultNodeWorker?
			if(maxAlertLevel.compareTo(curAlertLevel) < 0) {
				// If maxAlertLevel < current, drop current to be the max
				newAlertLevel = maxAlertLevel;
			} else if(isIncrementalRampUp(error != null) && curAlertLevel.compareTo(maxAlertLevel) < 0) {
				// If current < maxAlertLevel, increment by one
				newAlertLevel = AlertLevel.fromOrdinal(curAlertLevel.ordinal() + 1);
			} else {
				newAlertLevel = maxAlertLevel;
			}

			AlertLevel oldAlertLevel = alertLevel;
			if(oldAlertLevel == null) oldAlertLevel = AlertLevel.UNKNOWN;
			alertLevel = newAlertLevel;
			alertMessage = alertLevelAndMessage.getAlertMessage();

			singleResultUpdated(result);
			if(oldAlertLevel!=newAlertLevel) {
				synchronized(singleResultNodeImpls) {
					for(SingleResultNodeImpl singleResultNodeImpl : singleResultNodeImpls) {
						singleResultNodeImpl.nodeAlertLevelChanged(
							oldAlertLevel,
							newAlertLevel,
							alertMessage
						);
					}
				}
			}
		} catch(Exception err) {
			logger.log(Level.SEVERE, null, err);
			lastSuccessful = false;
		} finally {
			// Reschedule next timer task if still running
			synchronized(timerTaskLock) {
				if(timerTask!=null) {
					timerTask = RootNodeImpl.schedule(
						this,
						getSleepDelay(lastSuccessful, alertLevel)
					);
				}
			}
		}
	}

	final void addSingleResultNodeImpl(SingleResultNodeImpl singleResultNodeImpl) {
		synchronized(singleResultNodeImpls) {
			boolean needsStart = singleResultNodeImpls.isEmpty();
			assert !CollectionUtils.containsByIdentity(singleResultNodeImpls, singleResultNodeImpl);
			singleResultNodeImpls.add(singleResultNodeImpl);
			if(needsStart) start();
		}
	}

	final void removeSingleResultNodeImpl(SingleResultNodeImpl singleResultNodeImpl) {
		synchronized(singleResultNodeImpls) {
			if(singleResultNodeImpls.isEmpty()) throw new AssertionError("singleResultNodeImpls is empty");
			boolean found = false;
			for(int c=singleResultNodeImpls.size()-1;c>=0;c--) {
				if(singleResultNodeImpls.get(c)==singleResultNodeImpl) {
					singleResultNodeImpls.remove(c);
					found = true;
					break;
				}
			}
			if(!found && logger.isLoggable(Level.WARNING)) logger.log(Level.WARNING, "singleResultNodeImpl not found in singleResultNodeImpls: " + singleResultNodeImpl);
			assert !CollectionUtils.containsByIdentity(singleResultNodeImpls, singleResultNodeImpl);
			if(singleResultNodeImpls.isEmpty()) {
				stop();
			}
		}
	}

	/**
	 * Notifies all of the listeners.
	 */
	private void singleResultUpdated(SingleResult singleResult) {
		assert !SwingUtilities.isEventDispatchThread() : "Running in Swing event dispatch thread";

		synchronized(singleResultNodeImpls) {
			for(SingleResultNodeImpl singleResultNodeImpl : singleResultNodeImpls) {
				singleResultNodeImpl.singleResultUpdated(singleResult);
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
		return lastSuccessful && alertLevel==AlertLevel.NONE ? 5*60000 : 60000;
	}

	/**
	 * Determines the alert level and message for the provided result.
	 */
	protected abstract AlertLevelAndMessage getAlertLevelAndMessage(AlertLevel curAlertLevel, SingleResult result);

	/**
	 * Gets the report for this worker.
	 */
	protected abstract String getReport() throws Exception;

	/**
	 * Cancels the current getReport call on a best-effort basis.
	 * Implementations of this method <b>must not block</b>.
	 * This default implementation calls <code>future.cancel(true)</code>.
	 */
	protected void cancel(Future<String> future) {
		future.cancel(true);
	}
}
