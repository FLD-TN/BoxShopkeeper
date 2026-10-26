package com.dex.boxshopkeeper.trade;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Appends one line per successful trade to {@code logs/trades-<yyyy-MM-dd>.log} (§10) on a single
 * background thread with a bounded queue. When the queue is full the line is dropped and one
 * warning per minute is logged.
 */
public final class TradeLogger {

    private static final String PREFIX = "trades-";
    private static final String SUFFIX = ".log";

    private final Path directory;
    private final boolean enabled;
    private final Logger logger;
    private final ThreadPoolExecutor executor;
    private long lastDropWarning;

    public TradeLogger(Path directory, boolean enabled, int queueSize, Logger logger) {
        this.directory = directory;
        this.enabled = enabled;
        this.logger = logger;
        this.executor = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueSize),
                runnable -> {
                    Thread thread = new Thread(runnable, "BoxShopkeeper-TradeLog");
                    thread.setDaemon(true);
                    return thread;
                },
                (runnable, pool) -> dropped());
    }

    public static String timestamp() {
        return OffsetDateTime.now().truncatedTo(ChronoUnit.SECONDS).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    /** Queues one line; called on the main thread, never blocks on disk. */
    public void log(String line) {
        if (!enabled || executor.isShutdown()) {
            return;
        }
        Path file = directory.resolve(PREFIX + LocalDate.now() + SUFFIX);
        String text = line + System.lineSeparator();
        executor.execute(() -> {
            try {
                Files.createDirectories(directory);
                Files.writeString(file, text, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE);
            } catch (IOException e) {
                logger.log(Level.WARNING, "Cannot write trade log " + file, e);
            }
        });
    }

    private void dropped() {
        long now = System.currentTimeMillis();
        if (now - lastDropWarning >= 60_000L) {
            lastDropWarning = now;
            logger.warning("Trade log queue is full, dropping lines");
        }
    }

    /** Deletes log files older than {@code keepDays}. */
    public void pruneOld(int keepDays) {
        if (!Files.isDirectory(directory)) {
            return;
        }
        LocalDate oldest = LocalDate.now().minusDays(keepDays);
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, PREFIX + "*" + SUFFIX)) {
            for (Path file : files) {
                String name = file.getFileName().toString();
                String date = name.substring(PREFIX.length(), name.length() - SUFFIX.length());
                try {
                    if (LocalDate.parse(date).isBefore(oldest)) {
                        Files.deleteIfExists(file);
                    }
                } catch (DateTimeParseException ignored) {
                    // not one of our files
                }
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Cannot prune trade logs", e);
        }
    }

    /** Stops accepting lines and waits for queued lines to be written. */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                logger.warning("Trade log did not finish writing in time");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
