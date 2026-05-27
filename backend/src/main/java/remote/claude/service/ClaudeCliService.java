package remote.claude.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import remote.claude.config.ClaudeCliConfig;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Service
public class ClaudeCliService {

    private static final Logger log = LoggerFactory.getLogger(ClaudeCliService.class);
    private static final long CLI_TIMEOUT_MS = 300_000; // 5 minutes

    private final ClaudeCliConfig config;

    public ClaudeCliService(ClaudeCliConfig config) {
        this.config = config;
    }

    public String getClaudePath() {
        return config.getClaudePath();
    }

    public String getDefaultProjectDir() {
        return config.getDefaultProjectDir();
    }

    /**
     * Run a non-interactive Claude prompt with streaming output.
     * @param prompt  the prompt text
     * @param sessionId optional session ID to resume
     * @param model   optional model name
     * @param projectDir working directory
     * @param onLine  callback for each output line
     * @param onError callback for error lines
     * @param onComplete callback when process completes
     * @return the process handle
     */
    public Process runCommand(String prompt, String sessionId, String model,
                              String projectDir,
                              Consumer<String> onLine,
                              Consumer<String> onError,
                              Runnable onComplete) {

        ProcessBuilder pb = buildProcess(prompt, sessionId, model, projectDir);
        Map<String, String> env = pb.environment();
        env.put("CLI_CODE_MODE", "true");
        // Pass through env vars for API proxy auth
        copyEnvIfPresent(env, "ANTHROPIC_BASE_URL");
        copyEnvIfPresent(env, "ANTHROPIC_API_KEY");
        copyEnvIfPresent(env, "ANTHROPIC_AUTH_TOKEN");
        copyEnvIfPresent(env, "ANTHROPIC_CUSTOM_HEADERS");

        try {
            Process process = pb.start();

            // Read stdout stream
            CompletableFuture.runAsync(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (onLine != null) onLine.accept(line);
                    }
                } catch (IOException e) {
                    log.error("Error reading CLI output", e);
                }
            });

            // Read stderr stream
            CompletableFuture.runAsync(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (onError != null) onError.accept(line);
                    }
                } catch (IOException e) {
                    log.error("Error reading CLI error", e);
                }
            });

            // Wait for completion with timeout
            CompletableFuture.runAsync(() -> {
                try {
                    boolean finished = process.waitFor(CLI_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                    if (!finished) {
                        log.warn("CLI process timed out after {}ms, destroying", CLI_TIMEOUT_MS);
                        process.destroyForcibly();
                    } else {
                        int exitCode = process.exitValue();
                        log.info("CLI process exited with code {}", exitCode);
                    }
                    if (onComplete != null) onComplete.run();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    process.destroyForcibly();
                    log.error("CLI process interrupted", e);
                }
            });

            return process;
        } catch (IOException e) {
            log.error("Failed to start CLI process", e);
            if (onError != null) onError.accept("Error: " + e.getMessage());
            if (onComplete != null) onComplete.run();
            return null;
        }
    }

    private void copyEnvIfPresent(Map<String, String> target, String key) {
        String value = System.getenv(key);
        if (value != null && !value.isEmpty()) {
            target.put(key, value);
        }
    }

    private ProcessBuilder buildProcess(String prompt, String sessionId,
                                        String model, String projectDir) {

        String claude = config.getClaudePath();
        String workDir = projectDir != null ? projectDir : config.getDefaultProjectDir();

        ProcessBuilder pb = new ProcessBuilder();

        // Build arguments using ProcessBuilder's built-in argument escaping
        // This is SAFE: ProcessBuilder passes each argument as a separate token
        // to the OS, preventing command injection. User input NEVER goes into
        // a shell string.
        List<String> args = new ArrayList<>();
        args.add(claude);

        if (model != null && !model.isEmpty()) {
            // Validate model against allowed list
            boolean validModel = config.getModels().stream()
                    .anyMatch(m -> m.getId().equals(model));
            if (!validModel) {
                log.warn("Ignoring unknown model: {}", model);
            } else {
                args.add("--model");
                args.add(model);
            }
        }

        if (sessionId != null && !sessionId.isEmpty()) {
            args.add("--resume");
            args.add(sessionId);
            args.add("--print");
            args.add(prompt);
        } else {
            args.add("--print");
            args.add(prompt);
        }

        pb.command(args);
        pb.directory(new File(workDir));
        pb.redirectErrorStream(false);

        log.info("Running: {} (dir: {})", String.join(" ", args), workDir);

        return pb;
    }
}