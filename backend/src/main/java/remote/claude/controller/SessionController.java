package remote.claude.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
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

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSession(
            @PathVariable String id,
            @RequestParam(required = false, defaultValue = "") String projectDir) {
        boolean deleted = sessionService.deleteSession(id, projectDir);
        if (deleted) return ResponseEntity.ok().build();
        return ResponseEntity.notFound().build();
    }
}