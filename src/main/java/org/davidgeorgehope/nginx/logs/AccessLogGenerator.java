package org.davidgeorgehope.nginx.logs;

import org.davidgeorgehope.AnomalyConfig;
import org.davidgeorgehope.UserSessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;

public class AccessLogGenerator {
    private static final Logger logger = LoggerFactory.getLogger(AccessLogGenerator.class);

    public static void generateAccessLogs(
            int logsToGenerate,
            String filePath,
            boolean isFrontend,
            UserSessionManager userSessionManager,
            long simulatedTime) {
        try (FileWriter writer = new FileWriter(filePath, true)) {
            for (int i = 0; i < logsToGenerate; i++) {
                AccessLogEntry entry;
                if (AnomalyConfig.isInduceDatabaseOutage()) {
                    // Generate entries with 500 status codes
                    entry = AccessLogEntry.createErrorEntry(isFrontend, userSessionManager, simulatedTime);
                } else {
                    entry = AccessLogEntry.createRandomEntry(isFrontend, userSessionManager, simulatedTime);
                }
                writer.write(entry.toString());
            }
        } catch (IOException e) {
            logger.error("Error writing access log", e);
        }
    }
}
