package remote.claude.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "claude-remote")
public class ClaudeCliConfig {

    private String claudePath = "C:\\Users\\MR\\AppData\\Roaming\\npm\\claude.cmd";
    private String defaultProjectDir = "C:\\Users\\MR\\Desktop\\deepseek";
    private String authToken = "";
    private String overrideConfigFile = "";
    private String proxyBaseUrl = "http://127.0.0.1:15721/claude-desktop";
    private List<ModelConfig> models = new ArrayList<>();

    public String getClaudePath() { return claudePath; }
    public void setClaudePath(String claudePath) { this.claudePath = claudePath; }
    public String getDefaultProjectDir() { return defaultProjectDir; }
    public void setDefaultProjectDir(String defaultProjectDir) { this.defaultProjectDir = defaultProjectDir; }
    public String getAuthToken() { return authToken; }
    public void setAuthToken(String authToken) { this.authToken = authToken; }
    public String getOverrideConfigFile() { return overrideConfigFile; }
    public void setOverrideConfigFile(String overrideConfigFile) { this.overrideConfigFile = overrideConfigFile; }
    public String getProxyBaseUrl() { return proxyBaseUrl; }
    public void setProxyBaseUrl(String proxyBaseUrl) { this.proxyBaseUrl = proxyBaseUrl; }
    public List<ModelConfig> getModels() { return models; }
    public void setModels(List<ModelConfig> models) { this.models = models; }

    public static class ModelConfig {
        private String id;
        private String name;
        private String provider;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
    }
}