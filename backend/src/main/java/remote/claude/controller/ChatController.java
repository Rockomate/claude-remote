package remote.claude.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import remote.claude.config.ClaudeCliConfig;
import remote.claude.dto.ChatRequest;
import remote.claude.service.ClaudeCliService;

import java.io.*;
import java.nio.charset.StandardCharsets;
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
    public void chat(@Valid @RequestBody ChatRequest request,
                     HttpServletRequest httpRequest,
                     HttpServletResponse response) throws IOException {

        // Validate CLI path
        try {
            validateClaudePath();
        } catch (Exception e) {
            response.setContentType("text/event-stream");
            response.setCharacterEncoding("UTF-8");
            response.setHeader("Cache-Control", "no-cache");
            try {
                var os = response.getOutputStream();
                os.write(("event:error\ndata:" + e.getMessage() + "\n\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
                os.write("event:done\ndata:DONE\n\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                os.flush();
            } catch (IOException ignored) {}
            return;
        }

        // Set SSE headers - no buffering
        response.setContentType("text/event-stream");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache, no-store");
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Connection", "keep-alive");

        ServletOutputStream os = response.getOutputStream();
        // Critical: disable Nagle's algorithm on the socket
        response.flushBuffer();

        String sessionId = request.getSessionId();
        String model = request.getModel();
        boolean passModel = model != null && !model.isEmpty() && !"default".equals(model);

        StringBuilder contentBuffer = new StringBuilder();
        boolean[] hasContent = {false};
        boolean[] finished = {false};
        String procKey = sessionId != null ? sessionId : "chat-" + System.currentTimeMillis();

        Process process = claudeService.runCommand(
                request.getPrompt(),
                sessionId,
                passModel ? model : null,
                request.getProjectDir(),
                line -> {
                    if (line.contains("Warning: no stdin data received")) return;
                    if (line.startsWith("API Error") || line.contains("API Error")) {
                        safeWrite(os, "error", line);
                        return;
                    }
                    hasContent[0] = true;
                    contentBuffer.append(line).append("\n");
                    safeWrite(os, "line", line);
                },
                error -> {
                    if (error.contains("Warning:")) return;
                    safeWrite(os, "error", error);
                },
                () -> {
                    finished[0] = true;
                    safeWrite(os, "done", "DONE");
                    activeProcesses.remove(procKey);
                }
        );

        if (process != null) {
            activeProcesses.put(procKey, process);
            try {
                process.waitFor();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        } else {
            safeWrite(os, "error", "Failed to start Claude process");
            safeWrite(os, "done", "DONE");
        }
    }

    private void safeWrite(ServletOutputStream os, String event, String data) {
        try {
            String frame = "event:" + event + "\ndata:" + (data != null ? data : "") + "\n\n";
            os.write(frame.getBytes(StandardCharsets.UTF_8));
            os.flush();
        } catch (IOException e) {
            // Client disconnected
            log.debug("SSE write failed (client disconnected): {}", e.getMessage());
        }
    }

    @PostMapping("/chat/cancel")
    public ResponseEntity<Void> cancelChat(@RequestParam(required = false) String sessionId) {
        if (sessionId != null) {
            Process process = activeProcesses.remove(sessionId);
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
                return ResponseEntity.ok().build();
            }
        }
        activeProcesses.values().forEach(p -> { if (p.isAlive()) p.destroyForcibly(); });
        activeProcesses.clear();
        return ResponseEntity.ok().build();
    }

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
