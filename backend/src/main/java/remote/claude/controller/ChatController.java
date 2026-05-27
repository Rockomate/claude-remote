package remote.claude.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import remote.claude.config.ClaudeCliConfig;
import remote.claude.dto.ChatRequest;
import remote.claude.service.ClaudeCliService;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ClaudeCliService claudeService;
    private final ClaudeCliConfig config;
    private final ConcurrentHashMap<String, Process> activeProcesses = new ConcurrentHashMap<>();

    public ChatController(ClaudeCliService claudeService, ClaudeCliConfig config) {
        this.claudeService = claudeService;
        this.config = config;
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@Valid @RequestBody ChatRequest request) {
        // Validate CLI path before attempting to run
        try {
            validateClaudePath();
        } catch (Exception e) {
            SseEmitter errorEmitter = new SseEmitter(5000L);
            try {
                errorEmitter.send(SseEmitter.event().name("error").data(e.getMessage()));
                errorEmitter.send(SseEmitter.event().name("done").data("DONE"));
            } catch (IOException ignored) {}
            errorEmitter.complete();
            return errorEmitter;
        }

        SseEmitter emitter = new SseEmitter(600_000L);
        String sessionId = request.getSessionId();
        String model = request.getModel();
        if (model == null || model.isEmpty()) {
            List<ClaudeCliConfig.ModelConfig> models = config.getModels();
            if (!models.isEmpty()) {
                model = models.get(0).getId();
            }
        }
        // Only pass --model if explicitly requested; otherwise let CLI use its default

        StringBuilder contentBuffer = new StringBuilder();
        boolean[] hasContent = {false};

        Process process = claudeService.runCommand(
                request.getPrompt(),
                sessionId,
                model,
                request.getProjectDir(),
                // onLine — combine all output as the final result
                line -> {
                    // Skip warning lines about stdin
                    if (line.contains("Warning: no stdin data received")) return;
                    if (line.startsWith("API Error") || line.contains("API Error")) {
                        sendEvent(emitter, "error", line);
                        return;
                    }
                    hasContent[0] = true;
                    contentBuffer.append(line).append("\n");
                    sendEvent(emitter, "line", line);
                },
                error -> {
                    if (error.contains("Warning:")) return;
                    sendEvent(emitter, "error", error);
                },
                () -> {
                    sendEvent(emitter, "done", hasContent[0] ? contentBuffer.toString() : "DONE");
                    try { emitter.complete(); } catch (Exception ignored) {}
                }
        );

        if (process != null) {
            String procKey = sessionId != null ? sessionId : "chat-" + System.currentTimeMillis();
            activeProcesses.put(procKey, process);
            emitter.onCompletion(() -> {
                process.destroyForcibly();
                activeProcesses.remove(procKey);
            });
            emitter.onTimeout(() -> {
                process.destroyForcibly();
                activeProcesses.remove(procKey);
            });
        } else {
            sendEvent(emitter, "error", "Failed to start Claude process");
            sendEvent(emitter, "done", "DONE");
            try { emitter.complete(); } catch (Exception ignored) {}
        }

        return emitter;
    }

    @PostMapping("/chat/cancel")
    public ResponseEntity<Void> cancelChat(@RequestParam(required = false) String sessionId) {
        // Try exact match first, then prefix scan (for timestamp-keyed processes)
        if (sessionId != null) {
            Process process = activeProcesses.remove(sessionId);
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
                return ResponseEntity.ok().build();
            }
        }
        // Destroy all active processes if no specific sessionId
        activeProcesses.values().forEach(p -> { if (p.isAlive()) p.destroyForcibly(); });
        activeProcesses.clear();
        return ResponseEntity.ok().build();
    }

    private void sendEvent(SseEmitter emitter, String event, String data) {
        try {
            emitter.send(SseEmitter.event()
                    .name(event)
                    .data(data != null ? data : ""));
        } catch (IOException e) {
            // Client disconnected — try to complete the emitter
            try { emitter.complete(); } catch (Exception ignored) {}
        }
    }

    /**
     * Validate and return the CLI binary path. Returns null if not found.
     */
    private void validateClaudePath() {
        String path = config.getClaudePath();
        if (path == null || path.isEmpty()) {
            throw new RuntimeException("claude-path not configured");
        }
        File f = new File(path);
        if (!f.exists() || !f.isFile()) {
            throw new RuntimeException("claude binary not found: " + path);
        }
    }
}