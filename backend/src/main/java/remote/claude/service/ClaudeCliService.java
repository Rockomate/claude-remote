package remote.claude.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import remote.claude.config.ClaudeCliConfig;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

@Service
public class ClaudeCliService {

    private static final Logger log = LoggerFactory.getLogger(ClaudeCliService.class);

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
        pb.environment().put("CLI_CODE_MODE", "true");

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

            // Wait for completion
            CompletableFuture.runAsync(() -> {
                try {
                    int exitCode = process.waitFor();
                    log.info("CLI process exited with code {}", exitCode);
                    if (onComplete != null) onComplete.run();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
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

    private ProcessBuilder buildProcess(String prompt, String sessionId,
                                        String model, String projectDir) {

        String claude = config.getClaudePath();
        String workDir = projectDir != null ? projectDir : config.getDefaultProjectDir();

        ProcessBuilder pb = new ProcessBuilder();

        // Build arguments
        if (sessionId != null && !sessionId.isEmpty()) {
            // Resume existing session with --print mode
            pb.command(claude, "--resume", sessionId, "-p", prompt);
        } else {
            pb.command(claude, "-p", prompt);
        }

        if (model != null && !model.isEmpty()) {
            // Use full model name if available
            for (ClaudeCliConfig.ModelConfig mc : config.getModels()) {
                if (mc.getId().equals(model) && mc.getFullName() != null) {
                    // Use as is -- the proxy handles routing if configured
                    // But if the proxy knows the id, use the id as model name
                    break;
                }
            }
            // Add model parameter at position 1 (after claude, before -p)
            pb.command().add(1, "--model");
            pb.command().add(2, model);
        }

        pb.directory(new File(workDir));
        pb.redirectErrorStream(false);

        log.info("Running: {} (dir: {})", String.join(" ", pb.command()), workDir);

        return pb;
    }
}