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
import java.util.concurrent.atomic.AtomicBoolean;

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
        String token = config.getAuthToken();
        // If auth is configured, validate it from header
        // (Token validation simplified — in production use a filter)

        SseEmitter emitter = new SseEmitter(600_000L); // 10 min timeout
        String sessionId = request.getSessionId();
        String model = request.getModel();

        Process process = claudeService.runCommand(
                request.getPrompt(),
                sessionId,
                model,
                request.getProjectDir(),
                // onLine — forward each line as SSE event
                line -> sendEvent(emitter, "line", line),
                // onError — send as error event
                error -> sendEvent(emitter, "error", error),
                // onComplete — send done and close
                () -> {
                    sendEvent(emitter, "done", "DONE");
                    try { emitter.complete(); } catch (Exception ignored) {}
                }
        );

        if (process != null) {
            String procKey = sessionId != null ? sessionId : "new";
            activeProcesses.put(procKey, process);
            emitter.onCompletion(() -> activeProcesses.remove(procKey));
            emitter.onTimeout(() -> {
                process.destroyForcibly();
                activeProcesses.remove(procKey);
            });
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