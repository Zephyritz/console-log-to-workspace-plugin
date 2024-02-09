package hudson.plugins.ConsoleLogToWorkspace;

import hudson.FilePath;
import hudson.console.AnnotatedLargeText;
import hudson.model.TaskListener;
import hudson.model.Result;
import hudson.model.Run;
import java.io.IOException;
import java.io.OutputStream;
import hudson.EnvVars;

public class ConsoleLogToWorkspace {
    private static final int DEFAULT_LOG_SIZE_LIMIT = 1024 * 1024; // 1 MB
    private static final int DEFAULT_TIMEOUT_SECONDS = 300; // 5 minutes

    public static boolean perform(Run<?, ?> build, FilePath workspace, TaskListener listener,
            boolean writeConsoleLog, String fileName, boolean blockOnAllOutput, int logSizeLimit, int timeoutSeconds) {
        final OutputStream os;

        try {
            final EnvVars env = build.getEnvironment(listener);
            fileName=env.expand(fileName);

            if (writeConsoleLog) {
                log("Writing console log to workspace file " + fileName + " started", listener);
                os = workspace.child(fileName).write();
                writeLogFile(build.getLogText(), os, blockOnAllOutput);
                os.close();
                log("Wrote console log to workspace file " + fileName + " successfully", listener);
            }
        } catch (IOException|InterruptedException e) {
            build.setResult(Result.UNSTABLE);
            log("Writing console log to workspace file " + fileName + " failed", listener);
        }
        return true;
    }

    private static void log(String message, TaskListener listener) {
        listener.getLogger().println("[ConsoleLogToWorkspace] " + message);
    }

    // Added new log function to accommodate for timeout and line check blocks
    private static void log(String message, OutputStream out) throws IOException {
        out.write(("[ConsoleLogToWorkspace] " + message + "\n").getBytes());
    }
    private static void writeLogFile(AnnotatedLargeText logText, OutputStream out,
                                     boolean block, int logSizeLimit, int timeoutSeconds) throws IOException, InterruptedException {
        long pos = 0;
        long prevPos = pos;
        long startTime = System.currentTimeMillis();

        do {
            prevPos = pos;
            pos = logText.writeLogTo(pos, out);
            long elapsedTime = System.currentTimeMillis() - startTime;

            // Check log size limit
            if (pos > logSizeLimit) {
                log("Reached log size limit of " + logSizeLimit + " bytes. Aborting.",out);
                out.write("\nACID: log truncated (size exceeded)\n".getBytes());
                break;
            }

            // Check timeout
            if (block && elapsedTime > timeoutSeconds) {
                log("Timeout exceeded (" + timeoutSeconds + " seconds). Aborting.",out);
                out.write("\nACID: log truncated (timeout)\n".getBytes());
                break;
            }

            // Nothing new has been written or not blocking
            if (prevPos >= pos || !block) {
                break;
            }
            Thread.sleep(1000);
        } while(true);
    }

}
