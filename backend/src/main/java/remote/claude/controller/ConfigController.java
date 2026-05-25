package remote.claude.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import remote.claude.config.ClaudeCliConfig;

import java.io.File;
import java.util.*;

@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private static final Logger log = LoggerFactory.getLogger(ConfigController.class);

    private final ClaudeCliConfig config;
    private final ObjectMapper mapper;

    public ConfigController(ClaudeCliConfig config) {
        this.config = config;
        this.mapper = new ObjectMapper();
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getConfig() {
        return ResponseEntity.ok(Map.of(
                "claudePath", config.getClaudePath(),
                "defaultProjectDir", config.getDefaultProjectDir(),
                "proxyBaseUrl", config.getProxyBaseUrl(),
                "models", config.getModels().stream().map(m -> Map.of(
                        "id", m.getId(),
                        "name", m.getName(),
                        "provider", m.getProvider()
                )).toList()
        ));
    }

    /**
     * Update config: save changes to override file.
     * Body format: { "defaultProjectDir": "...", "proxyBaseUrl": "...", "authToken": "..." }
     */
    @PutMapping
    public ResponseEntity<?> updateConfig(@RequestBody Map<String, Object> updates) {
        try {
            // Get or determine override file path
            String overridePath = config.getOverrideConfigFile();
            if (overridePath == null || overridePath.isEmpty()) {
                String userHome = System.getProperty("user.home");
                overridePath = userHome + File.separator + ".claude-remote-config.json";
            }

            File overrideFile = new File(overridePath);

            // Load existing override config or start fresh
            Map<String, Object> overrideConfig = new HashMap<>();
            if (overrideFile.exists()) {
                overrideConfig = mapper.readValue(overrideFile,
                        new TypeReference<Map<String, Object>>() {});
            }

            // Merge updates
            for (Map.Entry<String, Object> entry : updates.entrySet()) {
                overrideConfig.put(entry.getKey(), entry.getValue());
            }

            // Write to file
            mapper.writeValue(overrideFile, overrideConfig);
            log.info("Config saved to {}", overrideFile.getAbsolutePath());

            // Apply to runtime (for current session)
            applyOverride(overrideConfig);

            return ResponseEntity.ok(Map.of("status", "ok", "path", overrideFile.getAbsolutePath()));
        } catch (Exception e) {
            log.error("Failed to save config", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/projects")
    public ResponseEntity<List<Map<String, String>>> listProjects() {
        String userHome = System.getProperty("user.home");
        File projectsDir = new File(userHome + File.separator + ".claude" + File.separator + "projects");
        List<Map<String, String>> projects = new ArrayList<>();

        if (projectsDir.exists() && projectsDir.isDirectory()) {
            File[] dirs = projectsDir.listFiles(File::isDirectory);
            if (dirs != null) {
                for (File d : dirs) {
                    String name = d.getName();
                    // Try to find actual cwd from session files
                    String displayPath = findProjectPath(d);
                    if (displayPath == null) {
                        displayPath = d.getAbsolutePath();
                    }
                    int sessionCount = Objects.requireNonNullElse(
                            d.listFiles((f, fn) -> fn.endsWith(".jsonl")), new File[0]).length;

                    projects.add(Map.of(
                            "path", displayPath,
                            "name", displayPath.contains("\\") || displayPath.contains("/")
                                    ? displayPath.substring(Math.max(
                                            displayPath.lastIndexOf('\\'), displayPath.lastIndexOf('/')) + 1)
                                    : displayPath,
                            "dirName", name,
                            "sessionCount", String.valueOf(sessionCount)
                    ));
                }
            }
        }

        projects.sort((a, b) -> Integer.parseInt(b.get("sessionCount"))
                - Integer.parseInt(a.get("sessionCount")));
        return ResponseEntity.ok(projects);
    }

    /**
     * Read cwd from the first session JSONL file to find the real project path.
     */
    private String findProjectPath(File projectDir) {
        File[] jsonlFiles = projectDir.listFiles((f, fn) -> fn.endsWith(".jsonl"));
        if (jsonlFiles == null || jsonlFiles.length == 0) return null;

        for (File f : jsonlFiles) {
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.FileReader(f))) {
                String line;
                int lines = 0;
                while ((line = reader.readLine()) != null && lines < 20) {
                    // Look for "cwd" field
                    int idx = line.indexOf("\"cwd\"");
                    if (idx >= 0) {
                        int colonIdx = line.indexOf(':', idx + 5);
                        if (colonIdx >= 0) {
                            int quoteStart = line.indexOf('"', colonIdx + 1);
                            if (quoteStart >= 0) {
                                int quoteEnd = line.indexOf('"', quoteStart + 1);
                                if (quoteEnd >= 0) {
                                    String cwd = line.substring(quoteStart + 1, quoteEnd);
                                    // Unescape JSON escapes
                                    cwd = cwd.replace("\\\\", "\\");
                                    return cwd;
                                }
                            }
                        }
                    }
                    lines++;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    public static String decodeProjectDir(String encodedName) {
        // Claude Code uses "C--Users-MR-Desktop-" as prefix for known projects
        String prefix = "C--Users-MR-Desktop-";
        if (encodedName.startsWith(prefix)) {
            return "C:\\Users\\MR\\Desktop\\" + encodedName.substring(prefix.length()).replace("-", " ");
        }
        return encodedName;
    }

    private void applyOverride(Map<String, Object> overrides) {
        if (overrides.containsKey("defaultProjectDir")) {
            config.setDefaultProjectDir((String) overrides.get("defaultProjectDir"));
        }
        if (overrides.containsKey("proxyBaseUrl")) {
            config.setProxyBaseUrl((String) overrides.get("proxyBaseUrl"));
        }
        if (overrides.containsKey("authToken")) {
            config.setAuthToken((String) overrides.get("authToken"));
        }
    }
}