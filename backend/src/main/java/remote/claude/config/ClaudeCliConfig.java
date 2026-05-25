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
    private List<ModelConfig> models = new ArrayList<>();

    public String getClaudePath() { return claudePath; }
    public void setClaudePath(String claudePath) { this.claudePath = claudePath; }

    public String getDefaultProjectDir() { return defaultProjectDir; }
    public void setDefaultProjectDir(String defaultProjectDir) { this.defaultProjectDir = defaultProjectDir; }

    public String getAuthToken() { return authToken; }
    public void setAuthToken(String authToken) { this.authToken = authToken; }

    public List<ModelConfig> getModels() { return models; }
    public void setModels(List<ModelConfig> models) { this.models = models; }

    public static class ModelConfig {
        private String id;
        private String name;
        private String provider;
        private String fullName;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public String getFullName() { return fullName; }
        public void setFullName(String fullName) { this.fullName = fullName; }
    }
}