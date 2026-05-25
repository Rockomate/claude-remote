package remote.claude.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import remote.claude.config.ClaudeCliConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/models")
public class ModelController {

    private static final Logger log = LoggerFactory.getLogger(ModelController.class);

    private final ClaudeCliConfig config;

    public ModelController(ClaudeCliConfig config) {
        this.config = config;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, String>>> listModels() {
        // Try fetching from proxy API first
        List<Map<String, String>> remoteModels = fetchFromProxy();
        if (remoteModels != null && !remoteModels.isEmpty()) {
            return ResponseEntity.ok(remoteModels);
        }

        // Fallback to config
        List<Map<String, String>> models = config.getModels().stream()
                .map(m -> Map.of(
                        "id", m.getId(),
                        "name", m.getName(),
                        "provider", m.getProvider() != null ? m.getProvider() : "unknown"
                ))
                .collect(Collectors.toList());
        return ResponseEntity.ok(models);
    }

    /**
     * Fetch available models from the proxy API's /v1/models endpoint.
     */
    private List<Map<String, String>> fetchFromProxy() {
        String baseUrl = config.getProxyBaseUrl();
        if (baseUrl == null || baseUrl.isEmpty()) return null;

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(3))
                    .build();

            String modelsUrl = baseUrl + (baseUrl.endsWith("/") ? "" : "/") + "v1/models";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(modelsUrl))
                    .header("Authorization", "Bearer " + System.getenv("ANTHROPIC_AUTH_TOKEN"))
                    .GET()
                    .timeout(java.time.Duration.ofSeconds(5))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Failed to fetch models from proxy: HTTP {}", response.statusCode());
                return null;
            }

            // Parse JSON response: {"data": [{"id": "...", ...}]}
            String body = response.body();
            // Quick manual parse without Jackson dependency for /v1/models format
            var models = new ArrayList<Map<String, String>>();

            int dataIdx = body.indexOf("\"data\"");
            if (dataIdx < 0) return null;

            int startIdx = body.indexOf('[', dataIdx);
            int endIdx = body.lastIndexOf(']');
            if (startIdx < 0 || endIdx < 0) return null;

            String arrayPart = body.substring(startIdx, endIdx + 1);
            // Parse individual model objects
            int pos = 0;
            while (true) {
                int objStart = arrayPart.indexOf('{', pos);
                if (objStart < 0) break;
                int objEnd = arrayPart.indexOf('}', objStart);
                if (objEnd < 0) break;

                String obj = arrayPart.substring(objStart, objEnd + 1);
                String id = extractJsonString(obj, "id");
                if (id != null && !id.isEmpty()) {
                    models.add(Map.of(
                            "id", id,
                            "name", id,  // Use id as display name
                            "provider", extractJsonString(obj, "provider") != null
                                    ? extractJsonString(obj, "provider") : "proxy"
                    ));
                }
                pos = objEnd + 1;
            }

            return models;
        } catch (Exception e) {
            log.warn("Error fetching models from proxy: {}", e.getMessage());
            return null;
        }
    }

    private String extractJsonString(String json, String key) {
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx < 0) return null;
        int colon = json.indexOf(':', idx + search.length());
        if (colon < 0) return null;
        int strStart = json.indexOf('"', colon + 1);
        if (strStart < 0) return null;
        int strEnd = json.indexOf('"', strStart + 1);
        if (strEnd < 0) return null;
        return json.substring(strStart + 1, strEnd);
    }
}