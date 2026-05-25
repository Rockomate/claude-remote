package remote.claude.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Session {
    private String id;
    private String name;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String projectDir;
    private int messageCount;
    private List<String> tags = new ArrayList<>();
    private String preview;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getProjectDir() { return projectDir; }
    public void setProjectDir(String projectDir) { this.projectDir = projectDir; }
    public int getMessageCount() { return messageCount; }
    public void setMessageCount(int messageCount) { this.messageCount = messageCount; }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
    public String getPreview() { return preview; }
    public void setPreview(String preview) { this.preview = preview; }
}