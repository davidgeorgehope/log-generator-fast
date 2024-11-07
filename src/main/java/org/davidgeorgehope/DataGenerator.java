package org.davidgeorgehope;

import org.davidgeorgehope.mysql.MySQLErrorLogGenerator;
import org.davidgeorgehope.mysql.MySQLGeneralLogGenerator;
import org.davidgeorgehope.mysql.MySQLSlowLogGenerator;
import org.davidgeorgehope.nginx.logs.AccessLogGenerator;
import org.davidgeorgehope.nginx.logs.ErrorLogGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class DataGenerator {
    private static final Logger logger = LoggerFactory.getLogger(DataGenerator.class);
    // Simulate starting 4 days ago
    private static final long SIMULATION_START_TIME = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(4);
    private static final long SIMULATION_END_TIME = System.currentTimeMillis();
    private static final long TIME_STEP = TimeUnit.MINUTES.toMillis(1); // Simulate data every minute

    private static final Random random = new Random();
    private static boolean disableAnomalies = false; // Default value

    // Configuration parameter with default value
    private static double meanRequestsPerSecond = 1; // Adjust this default value as needed

    // Keep track of current anomaly event
    private static AnomalyEvent currentAnomalyEvent = null;

    public static void main(String[] args) {
        // Parse command-line arguments
        for (String arg : args) {
            if (arg.equalsIgnoreCase("--no-anomalies")) {
                disableAnomalies = true;
                logger.info("Anomaly generation and database outages are disabled.");
            } else if (arg.startsWith("--mean-requests-per-second=")) {
                meanRequestsPerSecond = Double.parseDouble(arg.split("=")[1]);
                logger.info("Set meanRequestsPerSecond to " + meanRequestsPerSecond);
            }
        }

        // Standard log directories
        String nginxFrontEndLogDir = "/var/log/nginx_frontend";
        String nginxBackendLogDir = "/var/log/nginx_backend";
        String mysqlLogDir = "/var/log/mysql";

        // Create directories if they don't exist (requires appropriate permissions)
        new File(nginxFrontEndLogDir).mkdirs();
        new File(nginxBackendLogDir).mkdirs();
        new File(mysqlLogDir).mkdirs();

        UserSessionManager userSessionManager = new UserSessionManager();

        // Initialize anomaly events if anomalies are enabled
        List<AnomalyEvent> anomalyEvents = new ArrayList<>();
        if (!disableAnomalies) {
            anomalyEvents = generateAnomalyEvents();
        }

        // Initialize simulated time
        long simulatedTime = SIMULATION_START_TIME;

        // Reset anomaly config at the beginning
        resetAnomalyConfig();

        while (simulatedTime < SIMULATION_END_TIME) {
            // Check and update anomaly config based on current simulated time
            updateAnomalyConfig(simulatedTime, anomalyEvents);

            // Generate logs with simulated time
            int logsToGenerate = LogGeneratorUtils.getLogsToGenerate(meanRequestsPerSecond);

            AccessLogGenerator.generateAccessLogs(
                logsToGenerate,
                nginxFrontEndLogDir + "/access.log",
                true,
                userSessionManager,
                simulatedTime
            );

            AccessLogGenerator.generateAccessLogs(
                logsToGenerate,
                nginxBackendLogDir + "/access.log",
                false,
                userSessionManager,
                simulatedTime
            );

            ErrorLogGenerator.generateErrorLogs(
                1,
                nginxFrontEndLogDir + "/error.log",
                true,
                simulatedTime
            );

            ErrorLogGenerator.generateErrorLogs(
                1,
                nginxBackendLogDir + "/error.log",
                false,
                simulatedTime
            );

            MySQLErrorLogGenerator.generateErrorLogs(
                1,
                mysqlLogDir + "/error.log",
                false,
                simulatedTime
            );

            MySQLSlowLogGenerator.generateSlowLogs(
                1,
                mysqlLogDir + "/mysql-slow.log",
                simulatedTime
            );

            MySQLGeneralLogGenerator.generateGeneralLogs(
                1,
                mysqlLogDir + "/mysql.log",
                simulatedTime
            );

            // Increment simulated time
            simulatedTime += TIME_STEP;
        }

        logger.info("Data generation simulation completed.");
    }

    private static List<AnomalyEvent> generateAnomalyEvents() {
        List<AnomalyEvent> events = new ArrayList<>();
        long currentTime = SIMULATION_START_TIME;
        while (currentTime < SIMULATION_END_TIME) {
            // Generate delay until next anomaly
            double meanDelay = TimeUnit.HOURS.toSeconds(2); // Mean time between anomalies (2 hours)
            double maxDelay = TimeUnit.HOURS.toSeconds(3);  // Maximum delay (3 hours)
            long delay = (long) (getTruncatedExponentialRandom(meanDelay, maxDelay) * 1000);
            currentTime += delay;

            if (currentTime >= SIMULATION_END_TIME) {
                break;
            }

            // Generate anomaly duration
            double meanAnomalyDuration = TimeUnit.MINUTES.toSeconds(5); // Mean anomaly duration (5 minutes)
            double maxAnomalyDuration = TimeUnit.MINUTES.toSeconds(10); // Maximum anomaly duration (10 minutes)
            long duration = (long) (getTruncatedExponentialRandom(meanAnomalyDuration, maxAnomalyDuration) * 1000);

            if (currentTime + duration > SIMULATION_END_TIME) {
                duration = SIMULATION_END_TIME - currentTime;
            }

            // Choose random anomalies
            int numberOfAnomalies = random.nextInt(3) + 1; // 1 to 3 anomalies
            List<Runnable> anomalies = Arrays.asList(
                () -> AnomalyConfig.setInduceHighVisitorRate(true),
                () -> AnomalyConfig.setInduceHighErrorRate(true),
                () -> AnomalyConfig.setInduceHighRequestRateFromSingleIP(true),
                () -> AnomalyConfig.setInduceHighDistinctURLsFromSingleIP(true),
                () -> AnomalyConfig.setInduceLowRequestRate(true)
            );
            Collections.shuffle(anomalies);
            List<Runnable> activatedAnomalies = anomalies.subList(0, numberOfAnomalies);

            events.add(new AnomalyEvent(currentTime, currentTime + duration, activatedAnomalies));
            currentTime += duration;
        }
        return events;
    }

    private static void updateAnomalyConfig(long simulatedTime, List<AnomalyEvent> anomalyEvents) {
        // Check if we need to deactivate the current anomalies
        if (currentAnomalyEvent != null && simulatedTime >= currentAnomalyEvent.endTime) {
            resetAnomalyConfig();
            currentAnomalyEvent = null;
        }

        // Check if a new anomaly needs to be activated
        if (currentAnomalyEvent == null) {
            for (AnomalyEvent event : anomalyEvents) {
                if (simulatedTime >= event.startTime && simulatedTime < event.endTime) {
                    currentAnomalyEvent = event;
                    // Activate anomalies for this event
                    for (Runnable anomaly : event.anomalies) {
                        anomaly.run();
                    }
                    // Log which anomalies are activated
                    logActiveAnomalies(simulatedTime);
                    break;
                }
            }
        }
    }

    private static void logActiveAnomalies(long simulatedTime) {
        List<String> activeAnomalies = new ArrayList<>();
        if (AnomalyConfig.isInduceHighVisitorRate()) {
            activeAnomalies.add("HighVisitorRate");
        }
        if (AnomalyConfig.isInduceHighErrorRate()) {
            activeAnomalies.add("HighErrorRate");
        }
        if (AnomalyConfig.isInduceHighRequestRateFromSingleIP()) {
            activeAnomalies.add("HighRequestRateFromSingleIP");
        }
        if (AnomalyConfig.isInduceHighDistinctURLsFromSingleIP()) {
            activeAnomalies.add("HighDistinctURLsFromSingleIP");
        }
        if (AnomalyConfig.isInduceLowRequestRate()) {
            activeAnomalies.add("LowRequestRate");
        }

        logger.info("Anomalies activated at simulated time: " + new Date(simulatedTime) +
                ". Active anomalies: " + String.join(", ", activeAnomalies));
    }

    private static double getTruncatedExponentialRandom(double mean, double maxValue) {
        double u = random.nextDouble();
        double Fmax = 1 - Math.exp(-maxValue / mean);
        double value = -mean * Math.log(1 - u * Fmax);
        return value;
    }

    private static void resetAnomalyConfig() {
        // Reset all anomalies to normal state
        if (AnomalyConfig.isAnyAnomalyActive()) {
            AnomalyConfig.setInduceHighVisitorRate(false);
            AnomalyConfig.setInduceHighErrorRate(false);
            AnomalyConfig.setInduceHighRequestRateFromSingleIP(false);
            AnomalyConfig.setInduceHighDistinctURLsFromSingleIP(false);
            AnomalyConfig.setInduceLowRequestRate(false);
            logger.info("Anomaly configuration reset to normal at simulated time: " + new Date());
        }
    }

    private static class AnomalyEvent {
        long startTime;
        long endTime;
        List<Runnable> anomalies;

        public AnomalyEvent(long startTime, long endTime, List<Runnable> anomalies) {
            this.startTime = startTime;
            this.endTime = endTime;
            this.anomalies = anomalies;
        }
    }
}
