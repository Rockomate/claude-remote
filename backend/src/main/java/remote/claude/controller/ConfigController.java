package remote.claude.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import remote.claude.config.ClaudeCliConfig;

import java.util.Map;

@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private final ClaudeCliConfig config;

    public ConfigController(ClaudeCliConfig config) {
        this.config = config;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getConfig() {
        // Don't expose full config (e.g., paths) but provide what UI needs
        return ResponseEntity.ok(Map.of(
                "claudePath", config.getClaudePath(),
                "defaultProjectDir", config.getDefaultProjectDir(),
                "models", config.getModels()
        ));
    }
}