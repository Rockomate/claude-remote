package remote.claude.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import remote.claude.dto.FileTreeResponse;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

class FileServiceTest {

    private final FileService fileService = new FileService();

    @Test
    void listFiles_returnsTree(@TempDir File tempDir) throws IOException {
        // Create test structure
        Files.writeString(tempDir.toPath().resolve("test.txt"), "hello");
        Files.createDirectories(tempDir.toPath().resolve("subdir"));
        Files.writeString(tempDir.toPath().resolve("subdir/nested.txt"), "world");

        FileTreeResponse tree = fileService.listFiles(tempDir.getAbsolutePath(), 2);
        assertNotNull(tree);
        assertEquals(tempDir.getName(), tree.getName());
        assertTrue(tree.isDirectory());
        assertNotNull(tree.getChildren());
        assertTrue(tree.getChildren().size() >= 2);
    }

    @Test
    void listFiles_returnsNullForNonexistent() {
        FileTreeResponse tree = fileService.listFiles("/nonexistent/path", 2);
        assertNull(tree);
    }

    @Test
    void readFileContent_readsText(@TempDir File tempDir) throws IOException {
        File testFile = new File(tempDir, "test.txt");
        Files.writeString(testFile.toPath(), "Hello World");

        String content = fileService.readFileContent(tempDir.getAbsolutePath(), "test.txt");
        assertEquals("Hello World", content);
    }

    @Test
    void readFileContent_returnsNullForNonexistent(@TempDir File tempDir) {
        String content = fileService.readFileContent(tempDir.getAbsolutePath(), "nonexistent.txt");
        assertNull(content);
    }

    @Test
    void saveFile_savesContent(@TempDir File tempDir) throws IOException {
        FileService.FileInfo info = fileService.saveFile(
                tempDir.getAbsolutePath(), "test.txt", "Hello".getBytes());

        assertNotNull(info);
        assertEquals("test.txt", info.getName());
        assertEquals(5, info.getSize());

        File saved = new File(tempDir, "test.txt");
        assertTrue(saved.exists());
    }
}
