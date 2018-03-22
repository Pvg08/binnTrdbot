/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.evgcompany.binntrdbot;

import org.apache.commons.exec.*;
import org.apache.commons.exec.Executor;
import java.io.IOException;
import java.util.concurrent.*;

interface ProcessExecutorHandler {
    public void onStandardOutput(String msg);
    public void onStandardError(String msg);
}

/**
 *
 * @author EVG_adm_T
 */
public class ProcessExecutor {

    public static final Long WATCHDOG_EXIST_VALUE = -999L;

    public static Future<Long> runProcess(final CommandLine commandline, final ProcessExecutorHandler handler, final long watchdogTimeout) throws IOException {

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Long> result = executor.submit(new ProcessCallable(watchdogTimeout, handler, commandline));
        executor.shutdown();
        return result;
    }

    private static class ProcessCallable implements Callable<Long> {

        private long watchdogTimeout;
        private ProcessExecutorHandler handler;
        private CommandLine commandline;

        private ProcessCallable(long watchdogTimeout, ProcessExecutorHandler handler, CommandLine commandline) {
            this.watchdogTimeout = watchdogTimeout;
            this.handler = handler;
            this.commandline = commandline;
        }

        @Override
        public Long call() throws Exception {
            Executor executor = new DefaultExecutor();
            executor.setProcessDestroyer(new ShutdownHookProcessDestroyer());
            ExecuteWatchdog watchDog = new ExecuteWatchdog(watchdogTimeout);
            executor.setWatchdog(watchDog);
            executor.setStreamHandler(new PumpStreamHandler(new LogOutputStream() {
                @Override
                protected void processLine(String line, int logLevel) {
                    System.out.println(line);
                }
            }));
            Long exitValue;
            try {
                exitValue = new Long(executor.execute(commandline));
            } catch (ExecuteException e) {
                exitValue = new Long(e.getExitValue());
            }
            if (watchDog.killedProcess()) {
                exitValue = WATCHDOG_EXIST_VALUE;
            }

            return exitValue;
        }
    }
}
