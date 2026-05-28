package remote.claude.service;

import org.junit.jupiter.api.Test;
import remote.claude.config.ClaudeCliConfig;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClaudeCliServiceTest {

    @Test
    void getClaudePath_returnsConfiguredPath() {
        ClaudeCliConfig config = new ClaudeCliConfig();
        config.setClaudePath("C:\\test\\claude.cmd");
        ClaudeCliService service = new ClaudeCliService(config);
        assertEquals("C:\\test\\claude.cmd", service.getClaudePath());
    }

    @Test
    void getDefaultProjectDir_returnsConfiguredDir() {
        ClaudeCliConfig config = new ClaudeCliConfig();
        config.setDefaultProjectDir("C:\\test\\project");
        ClaudeCliService service = new ClaudeCliService(config);
        assertEquals("C:\\test\\project", service.getDefaultProjectDir());
    }

    @Test
    void runCommand_returnsNullForInvalidPath() {
        ClaudeCliConfig config = new ClaudeCliConfig();
        config.setClaudePath("C:\\nonexistent\\claude.cmd");
        ClaudeCliService service = new ClaudeCliService(config);

        var process = service.runCommand(
                "test", null, null, "C:\\test",
                line -> {}, err -> {}, () -> {});
        assertNull(process);
    }
}
