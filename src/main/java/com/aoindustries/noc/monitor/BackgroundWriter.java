/*
 * noc-monitor-impl - Implementation of Network Operations Center Monitoring.
 * Copyright (C) 2008, 2009, 2016, 2020, 2021, 2022  AO Industries, Inc.
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

import com.aoapps.lang.io.FileUtils;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;

/**
 * <p>
 * Writes files in the background.  The files are written in the order received.
 * If a new version of the file is provided while the old version has not yet been
 * written, the new version will take its place in the queue, keeping the position of the older item in the queue.
 * The expected result is that different files will get written in a round-robin style
 * while keeping the most up-to-date copies during times of heavy disk I/O.
 * </p>
 * <p>
 * This is being used to get around an issue where the monitoring would cause extremely
 * high load when running on a very busy disk subsystem.  The resulting load would
 * causing things to get progressively worse.
 * </p>
 * <p>
 * TODO: Should we perform any sort of batching?  Write all built-up in a minute at once?  Save HDD disk I/O?
 *       Or more extreme?  Only once per very long time period on the laptop?  Or, when it finds the disk already spun-up
 *       for some other reason?  Been using /dev/shm to avoid night-time disk I/O to keep it quiet while sleeping, but
 *       this loses all info on a reboot.  Is a pen drive a better solution?
 * </p>
 *
 * @author  AO Industries, Inc.
 */
final class BackgroundWriter {

  /** Make no instances. */
  private BackgroundWriter() {
    throw new AssertionError();
  }

  private static final Logger logger = Logger.getLogger(BackgroundWriter.class.getName());

  private static class QueueEntry {

    private final File newPersistenceFile;
    private final Serializable object;
    private final boolean gzip;

    private QueueEntry(File newPersistenceFile, Serializable object, boolean gzip) {
      this.newPersistenceFile = newPersistenceFile;
      this.object = object;
      this.gzip = gzip;
    }
  }

  // These are both synchronized on queue
  private static final LinkedHashMap<File, QueueEntry> queue = new LinkedHashMap<>();
  private static boolean running;

  /**
   * Queues the object for write.  No defensive copy of the object is made - do not change after giving to this method.
   */
  @SuppressWarnings({"UseSpecificCatch", "TooBroadCatch"})
  static void enqueueObject(File persistenceFile, File newPersistenceFile, Serializable serializable, boolean gzip) {
    QueueEntry queueEntry = new QueueEntry(newPersistenceFile, serializable, gzip);
    synchronized (queue) {
      if (queue.put(persistenceFile, queueEntry) != null) {
        logger.finer("DEBUG: BackgroundWriter: Updating existing in queue");
      }
      if (!running) {
        RootNodeImpl.executors.getUnbounded().submit(() -> {
          int counter = 0;
          while (true) {
            // Get the next file from the queue until done
            File persistenceFile1;
            QueueEntry queueEntry1;
            synchronized (queue) {
              Iterator<Map.Entry<File, QueueEntry>> iter = queue.entrySet().iterator();
              if (!iter.hasNext()) {
                running = false;
                logger.finer("DEBUG: BackgroundWriter: Total burst from queue: " + counter);
                return;
              }
              Map.Entry<File, QueueEntry> first = iter.next();
              persistenceFile1 = first.getKey();
              queueEntry1 = first.getValue();
              iter.remove();
              counter++;
            }
            try {
              try (
                ObjectOutputStream oout = new ObjectOutputStream(
                      queueEntry1.gzip
                          ? new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(queueEntry1.newPersistenceFile)))
                          : new BufferedOutputStream(new FileOutputStream(queueEntry1.newPersistenceFile))
                  )
                  ) {
                oout.writeObject(queueEntry1.object);
              }
              FileUtils.renameAllowNonAtomic(queueEntry1.newPersistenceFile, persistenceFile1);
            } catch (ThreadDeath td) {
              throw td;
            } catch (Throwable t) {
              logger.log(Level.SEVERE, null, t);
            }
          }
        });
        running = true;
      }
    }
  }
}
