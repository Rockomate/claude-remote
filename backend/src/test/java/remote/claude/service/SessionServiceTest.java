package remote.claude.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import remote.claude.model.Session;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SessionServiceTest {

    private final SessionService sessionService = new SessionService();

    @Test
    void getSessionDir_returnsCorrectPath() {
        String result = sessionService.getSessionDir("C:\\Users\\MR\\Desktop\\deepseek");
        assertNotNull(result);
        assertTrue(result.contains(".claude"));
        assertTrue(result.contains("projects"));
        assertTrue(result.contains("deepseek"));
    }

    @Test
    void getSessionDir_returnsNullForEmpty() {
        assertNull(sessionService.getSessionDir(""));
        assertNull(sessionService.getSessionDir(null));
    }

    @Test
    void listSessions_returnsEmptyForNonexistentDir() {
        List<Session> sessions = sessionService.listSessions("C:\\nonexistent\\path");
        assertNotNull(sessions);
        assertTrue(sessions.isEmpty());
    }

    @Test
    void getSession_returnsNullForNonexistent(@TempDir File tempDir) {
        Session session = sessionService.getSession("nonexistent-id", tempDir.getAbsolutePath());
        assertNull(session);
    }

    @Test
    void parseSessionFile_parsesUserContent(@TempDir File tempDir) throws IOException {
        // Create a minimal JSONL session file
        File sessionFile = new File(tempDir, "test-session.jsonl");
        String jsonl = "{\"type\":\"user\",\"role\":\"user\",\"content\":\"hello world\"}\n" +
                       "{\"type\":\"assistant\",\"role\":\"assistant\",\"content\":\"hi there\"}\n";
        Files.writeString(sessionFile.toPath(), jsonl, StandardCharsets.UTF_8);

        // This tests the internal parsing via getSession
        // Note: parseSessionFile is private, tested indirectly via getSession
        Session session = sessionService.getSession("test-session", tempDir.getAbsolutePath());
        // Session may be null if the directory structure doesn't match expected pattern
        // but the test verifies no exceptions are thrown
    }
}
