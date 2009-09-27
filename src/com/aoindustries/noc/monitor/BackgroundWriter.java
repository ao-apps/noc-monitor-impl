/*
 * Copyright 2008-2009 by AO Industries, Inc.,
 * 7262 Bull Pen Cir, Mobile, Alabama, 36695, U.S.A.
 * All rights reserved.
 */
package com.aoindustries.noc.monitor;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
class BackgroundWriter {

    private BackgroundWriter() {}

    private static final Logger logger = Logger.getLogger(BackgroundWriter.class.getName());

    private static boolean DEBUG = false;

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
    private static final LinkedHashMap<File,QueueEntry> queue = new LinkedHashMap<File,QueueEntry>();
    private static boolean running = false;

    /**
     * Queues the object for write.  No defensive copy of the object is made - do not change after giving to this method.
     */
    static void enqueueObject(File persistenceFile, File newPersistenceFile, Serializable serializable, boolean gzip) throws IOException {
        QueueEntry queueEntry = new QueueEntry(newPersistenceFile, serializable, gzip);
        synchronized(queue) {
            if(queue.put(persistenceFile, queueEntry)!=null) {
                if(DEBUG) System.out.println("DEBUG: BackgroundWriter: Updating existing in queue");
            }
            if(!running) {
                RootNodeImpl.executorService.submit(
                    new Runnable() {
                        @Override
                        public void run() {
                            int counter = 0;
                            while(true) {
                                // Get the next file from the queue until done
                                File persistenceFile;
                                QueueEntry queueEntry;
                                synchronized(queue) {
                                    Iterator<Map.Entry<File,QueueEntry>> iter = queue.entrySet().iterator();
                                    if(!iter.hasNext()) {
                                        running = false;
                                        if(DEBUG) System.out.println("DEBUG: BackgroundWriter: Total burst from queue: "+counter);
                                        return;
                                    }
                                    Map.Entry<File,QueueEntry> first = iter.next();
                                    persistenceFile = first.getKey();
                                    queueEntry = first.getValue();
                                    iter.remove();
                                    counter++;
                                }
                                try {
                                    OutputStream out = new BufferedOutputStream(new FileOutputStream(queueEntry.newPersistenceFile));
                                    if(queueEntry.gzip) out = new GZIPOutputStream(out);
                                    ObjectOutputStream oout = new ObjectOutputStream(out);
                                    try {
                                        oout.writeObject(queueEntry.object);
                                    } finally {
                                        oout.close();
                                    }
                                    // Try move over first (Linux)
                                    if(!queueEntry.newPersistenceFile.renameTo(persistenceFile)) {
                                        // Delete and rename (Windows)
                                        if(!persistenceFile.delete()) {
                                            throw new IOException(
                                                ApplicationResourcesAccessor.getMessage(
                                                    Locale.getDefault(),
                                                    "BackgroundWriter.error.unableToDelete",
                                                    persistenceFile.getCanonicalPath()
                                                )
                                            );
                                        }
                                        if(!queueEntry.newPersistenceFile.renameTo(persistenceFile)) {
                                            throw new IOException(
                                                ApplicationResourcesAccessor.getMessage(
                                                    Locale.getDefault(),
                                                    "BackgroundWriter.error.unableToRename",
                                                    queueEntry.newPersistenceFile.getCanonicalPath(),
                                                    persistenceFile.getCanonicalPath()
                                                )
                                            );
                                        }
                                    }
                                } catch(Exception err) {
                                    logger.log(Level.SEVERE, null, err);
                                }
                            }
                        }
                    }
                );
                running = true;
            }
        }
    }
}
