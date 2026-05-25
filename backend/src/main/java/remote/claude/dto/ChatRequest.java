package remote.claude.dto;

import jakarta.validation.constraints.NotBlank;

public class ChatRequest {
    @NotBlank
    private String prompt;
    private String sessionId;
    private String model;
    private String projectDir;

    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getProjectDir() { return projectDir; }
    public void setProjectDir(String projectDir) { this.projectDir = projectDir; }
}