package org.davidgeorgehope.mysql;

import org.davidgeorgehope.AnomalyConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.TimeUnit;

public class MySQLErrorLogGenerator {
    private static final Logger logger = LoggerFactory.getLogger(MySQLErrorLogGenerator.class);
    public static final AtomicLong warningStartTime = new AtomicLong();

    private static int lowStorageWarningCount = 0;
    private static int warningThreshold = ThreadLocalRandom.current().nextInt(8, 15); // Random threshold between 8 and 14

    private static boolean isOutageActive = false;
    private static long outageEndTime = 0L;

    public static void generateErrorLogs(int logsToGenerate, String filePath, boolean anomaliesDisabled, long simulatedTime) {
        List<MySQLErrorLogEntry> entries = new ArrayList<>();

        for (int i = 0; i < logsToGenerate; i++) {
            List<MySQLErrorLogEntry> entry = MySQLErrorLogEntry.createRandomEntries(anomaliesDisabled, simulatedTime);

            MySQLErrorLogEntry firstEntry = entry.get(0);
            if (firstEntry.isLowStorageWarning()) {
                lowStorageWarningCount++;
            }

            entries.addAll(entry);

            // Trigger database outage after random threshold is reached
            if (!anomaliesDisabled && lowStorageWarningCount >= warningThreshold && !isOutageActive) {
                isOutageActive = true;
                AnomalyConfig.setInduceDatabaseOutage(true);
                logger.info("Database outage induced due to low storage warnings at simulated time: " + simulatedTime);

                // Schedule reset of the database outage
                scheduleDatabaseOutageReset(simulatedTime);

                // Reset warning count and set a new random threshold for next time
                lowStorageWarningCount = 0;
                warningThreshold = ThreadLocalRandom.current().nextInt(8, 15);
            }
        }

        // Check if outage should end
        if (isOutageActive && simulatedTime >= outageEndTime) {
            isOutageActive = false;
            AnomalyConfig.setInduceDatabaseOutage(false);
            logger.info("Database outage resolved at simulated time: " + simulatedTime);

            // Reset the low storage warning count
            resetLowStorageWarningCount();

            // Reset the warning start time
            warningStartTime.set(simulatedTime);
        }

        try (FileWriter writer = new FileWriter(filePath, true)) {
            for (MySQLErrorLogEntry entry : entries) {
                writer.write(entry.toString());
            }
        } catch (IOException e) {
            logger.error("Error writing MySQL error log", e);
        }
    }

    private static void scheduleDatabaseOutageReset(long simulatedTime) {
        long minOutageDuration = TimeUnit.MINUTES.toMillis(3); // 3 minutes in milliseconds
        long maxOutageDuration = TimeUnit.MINUTES.toMillis(10); // 10 minutes in milliseconds
        long outageDuration = ThreadLocalRandom.current().nextLong(minOutageDuration, maxOutageDuration + 1);

        outageEndTime = simulatedTime + outageDuration;
    }

    public static void resetLowStorageWarningCount() {
        lowStorageWarningCount = 0;
        warningThreshold = ThreadLocalRandom.current().nextInt(8, 15); // Reset with a new random threshold
    }
}
