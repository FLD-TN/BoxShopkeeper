package com.dex.boxshopkeeper.util;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Logs a stack trace once per distinct error per minute (§17.4). */
public final class ErrorReporter {

    private static final long WINDOW_MILLIS = 60_000L;
    private static final int MAX_TRACKED = 256;

    private final Logger logger;
    private final Map<String, Long> lastLogged = new HashMap<>();

    public ErrorReporter(Logger logger) {
        this.logger = logger;
    }

    public synchronized void report(String context, Throwable error) {
        StackTraceElement[] trace = error.getStackTrace();
        String key = error.getClass().getName() + "|" + error.getMessage() + "|" + (trace.length > 0 ? trace[0] : "");
        long now = System.currentTimeMillis();
        Long last = lastLogged.get(key);
        if (last != null && now - last < WINDOW_MILLIS) {
            return;
        }
        if (lastLogged.size() >= MAX_TRACKED) {
            lastLogged.clear();
        }
        lastLogged.put(key, now);
        logger.log(Level.SEVERE, context, error);
    }
}
