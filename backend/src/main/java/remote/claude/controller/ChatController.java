package remote.claude.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import remote.claude.config.ClaudeCliConfig;
import remote.claude.dto.ChatRequest;
import remote.claude.service.ClaudeCliService;

import java.io.IOException;
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
    public SseEmitter chat(@RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(600_000L);
        String sessionId = request.getSessionId();
        String model = request.getModel();
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
        String key = sessionId != null ? sessionId : "new";
        Process process = activeProcesses.get(key);
        if (process != null && process.isAlive()) {
            process.destroyForcibly();
            activeProcesses.remove(key);
        }
        return ResponseEntity.ok().build();
    }

    private void sendEvent(SseEmitter emitter, String event, String data) {
        try {
            emitter.send(SseEmitter.event()
                    .name(event)
                    .data(data != null ? data : ""));
        } catch (IOException e) {
            // Client disconnected, ignore
        }
    }
}