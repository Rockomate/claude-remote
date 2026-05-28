package remote.claude.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import remote.claude.model.ChatMessage;
import remote.claude.model.Session;
import remote.claude.service.SessionService;

import java.util.*;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @GetMapping
    public ResponseEntity<List<Session>> listSessions(
            @RequestParam(required = false, defaultValue = "") String projectDir) {
        return ResponseEntity.ok(sessionService.listSessions(projectDir));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Session> getSession(
            @PathVariable String id,
            @RequestParam(required = false, defaultValue = "") String projectDir) {
        Session session = sessionService.getSession(id, projectDir);
        if (session == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(session);
    }

    @GetMapping("/{id}/messages")
    public ResponseEntity<List<ChatMessage>> getSessionMessages(
            @PathVariable String id,
            @RequestParam(required = false, defaultValue = "") String projectDir) {
        List<ChatMessage> messages = sessionService.getSessionMessages(id, projectDir);
        if (messages.isEmpty()) {
            // Check if session exists at all
            Session s = sessionService.getSession(id, projectDir);
            if (s == null) return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(messages);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSession(
            @PathVariable String id,
            @RequestParam(required = false, defaultValue = "") String projectDir) {
        boolean deleted = sessionService.deleteSession(id, projectDir);
        if (deleted) return ResponseEntity.ok().build();
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/export/{id}")
    public ResponseEntity<?> exportSession(
            @PathVariable String id,
            @RequestParam(required = false, defaultValue = "") String projectDir) {
        String sessionDir = sessionService.getSessionDir(projectDir);
        if (sessionDir == null) return ResponseEntity.badRequest().body(Map.of("error", "Project dir not found"));

        java.io.File jsonlFile = new java.io.File(sessionDir, id + ".jsonl");
        if (!jsonlFile.exists()) return ResponseEntity.notFound().build();

        try {
            String content = java.nio.file.Files.readString(jsonlFile.toPath(), java.nio.charset.StandardCharsets.UTF_8);
            Session session = sessionService.getSession(id, projectDir);
            String name = session != null ? session.getName() : id;

            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=\"session-" + id.substring(0, 8) + ".jsonl\"")
                    .contentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM)
                    .body(content);
        } catch (java.io.IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to export: " + e.getMessage()));
        }
    }
}