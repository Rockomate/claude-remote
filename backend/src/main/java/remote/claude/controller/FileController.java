package remote.claude.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import remote.claude.config.ClaudeCliConfig;
import remote.claude.dto.FileTreeResponse;
import remote.claude.service.FileService;

import java.io.IOException;
import java.util.*;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileService fileService;
    private final ClaudeCliConfig config;

    public FileController(FileService fileService, ClaudeCliConfig config) {
        this.fileService = fileService;
        this.config = config;
    }

    @GetMapping("/tree")
    public ResponseEntity<?> fileTree(
            @RequestParam(required = false, defaultValue = "") String dir,
            @RequestParam(required = false, defaultValue = "2") int depth) {
        String targetDir = dir.isEmpty() ? config.getDefaultProjectDir() : dir;
        FileTreeResponse tree = fileService.listFiles(targetDir, depth);
        if (tree == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Directory not found: " + targetDir));
        }
        return ResponseEntity.ok(tree);
    }

    @GetMapping("/read")
    public ResponseEntity<?> readFile(@RequestParam String path) {
        String baseDir = config.getDefaultProjectDir();
        String content = fileService.readFileContent(baseDir, path);
        if (content == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "File not found or access denied: " + path));
        }
        return ResponseEntity.ok(Map.of("content", content, "path", path));
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false, defaultValue = "") String dir) throws IOException {
        String targetDir = dir.isEmpty() ? config.getDefaultProjectDir() : dir;
        FileService.FileInfo info = fileService.saveFile(targetDir, file.getOriginalFilename(), file.getBytes());
        return ResponseEntity.ok(Map.of(
                "path", info.getPath(),
                "name", info.getName(),
                "size", info.getSize()
        ));
    }
}