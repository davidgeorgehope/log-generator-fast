package org.davidgeorgehope;

import org.davidgeorgehope.mysql.MySQLErrorLogGenerator;
import org.davidgeorgehope.mysql.MySQLGeneralLogGenerator;
import org.davidgeorgehope.mysql.MySQLSlowLogGenerator;
import org.davidgeorgehope.nginx.logs.AccessLogGenerator;
import org.davidgeorgehope.nginx.logs.ErrorLogGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Random;

import java.io.File;
import java.util.concurrent.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class DataGenerator {
    private static final Logger logger = LoggerFactory.getLogger(DataGenerator.class);
    // Simulate starting 4 days ago
    private static final long SIMULATION_START_TIME = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(4);
    private static final long SIMULATION_END_TIME = System.currentTimeMillis();
    private static final long TIME_STEP = TimeUnit.MINUTES.toMillis(1); // Simulate data every minute

    private static double meanRequestsPerSecond = 1; // Adjust this default value as needed

    public static void main(String[] args) {
        // Parse command-line arguments (keep existing code)

        // Standard log directories
        String nginxFrontEndLogDir = "/var/log/nginx_frontend";
        String nginxBackendLogDir = "/var/log/nginx_backend";
        String mysqlLogDir = "/var/log/mysql";

        // Create directories if they don't exist (requires appropriate permissions)
        new File(nginxFrontEndLogDir).mkdirs();
        new File(nginxBackendLogDir).mkdirs();
        new File(mysqlLogDir).mkdirs();

        UserSessionManager userSessionManager = new UserSessionManager();

        // Initialize simulated time
        long simulatedTime = SIMULATION_START_TIME;

        while (simulatedTime < SIMULATION_END_TIME) {
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
}
