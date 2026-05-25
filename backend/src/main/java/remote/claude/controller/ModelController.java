package remote.claude.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import remote.claude.config.ClaudeCliConfig;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/models")
public class ModelController {

    private final ClaudeCliConfig config;

    public ModelController(ClaudeCliConfig config) {
        this.config = config;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, String>>> listModels() {
        List<Map<String, String>> models = config.getModels().stream()
                .map(m -> Map.of(
                        "id", m.getId(),
                        "name", m.getName(),
                        "provider", m.getProvider()
                ))
                .collect(Collectors.toList());
        return ResponseEntity.ok(models);
    }
}