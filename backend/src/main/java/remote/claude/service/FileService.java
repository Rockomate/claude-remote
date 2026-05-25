package remote.claude.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import remote.claude.dto.FileTreeResponse;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

@Service
public class FileService {

    private static final Logger log = LoggerFactory.getLogger(FileService.class);
    private static final long MAX_UPLOAD_SIZE = 10 * 1024 * 1024; // 10MB
    private static final Set<String> BINARY_EXTENSIONS = Set.of(
            ".exe", ".dll", ".so", ".dylib", ".jar", ".war", ".zip", ".tar",
            ".gz", ".png", ".jpg", ".jpeg", ".gif", ".ico", ".svg",
            ".mp3", ".mp4", ".avi", ".mov", ".pdf", ".doc", ".docx");

    private final String baseDir;

    public FileService() {
        this.baseDir = System.getProperty("user.home") + File.separator;
    }

    /**
     * Resolve path safely relative to a base directory.
     * Prevents path traversal attacks.
     */
    private File safeResolve(String basePath, String userPath) {
        File base = new File(basePath).getAbsoluteFile();
        File resolved = new File(base, userPath).getAbsoluteFile();
        try {
            String resolvedPath = resolved.getCanonicalPath();
            String baseCanonical = base.getCanonicalPath();
            if (!resolvedPath.startsWith(baseCanonical + File.separator)
                    && !resolvedPath.equals(baseCanonical)) {
                log.warn("Path traversal detected: {} -> {}", userPath, resolvedPath);
                return null;
            }
            return resolved;
        } catch (IOException e) {
            log.warn("Failed to resolve path: {}", userPath, e);
            return null;
        }
    }

    public FileTreeResponse listFiles(String dirPath, int maxDepth) {
        File root = new File(dirPath);
        if (!root.exists() || !root.isDirectory()) return null;

        FileTreeResponse rootNode = new FileTreeResponse();
        rootNode.setName(root.getName());
        rootNode.setPath(root.getAbsolutePath());
        rootNode.setDirectory(true);

        buildTree(root, rootNode, 0, maxDepth <= 0 ? 2 : maxDepth);
        return rootNode;
    }

    public String readFileContent(String baseDir, String filePath) {
        File file = safeResolve(baseDir, filePath);
        if (file == null || !file.exists() || !file.isFile()) return null;

        String ext = getExtension(file.getName()).toLowerCase();
        if (BINARY_EXTENSIONS.contains(ext)) {
            return "[Binary file: " + file.getName() + "]";
        }

        try {
            long size = Files.size(file.toPath());
            if (size > 1024 * 1024) { // 1MB text limit
                return "[File too large to preview: " + file.getName() + " (" + (size / 1024) + "KB)]";
            }
            return Files.readString(file.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "[Failed to read: " + e.getMessage() + "]";
        }
    }

    public FileInfo saveFile(String dirPath, String fileName, byte[] content) throws IOException {
        // Validate file size
        if (content.length > MAX_UPLOAD_SIZE) {
            throw new IllegalArgumentException("File too large. Maximum size: 10MB");
        }

        // Sanitize filename (prevent path traversal in filename)
        String safeName = new File(fileName).getName();
        if (!safeName.equals(fileName)) {
            throw new IllegalArgumentException("Invalid filename");
        }

        File targetDir = new File(dirPath);
        if (!targetDir.exists()) targetDir.mkdirs();

        File target = new File(targetDir, safeName);
        Files.write(target.toPath(), content);

        FileInfo info = new FileInfo();
        info.setPath(target.getAbsolutePath());
        info.setName(safeName);
        info.setSize(content.length);
        return info;
    }

    private void buildTree(File dir, FileTreeResponse node, int depth, int maxDepth) {
        if (depth >= maxDepth) return;

        File[] files = dir.listFiles();
        if (files == null) return;

        // Sort: directories first, then alphabetical
        Arrays.sort(files, (a, b) -> {
            if (a.isDirectory() != b.isDirectory()) {
                return a.isDirectory() ? -1 : 1;
            }
            return a.getName().compareToIgnoreCase(b.getName());
        });

        List<FileTreeResponse> children = new ArrayList<>();
        for (File f : files) {
            String name = f.getName();
            if (name.startsWith(".") || name.equals("node_modules")
                    || name.equals("target") || name.equals(".git")) continue;

            FileTreeResponse child = new FileTreeResponse();
            child.setName(name);
            child.setPath(f.getAbsolutePath());
            child.setDirectory(f.isDirectory());
            if (!f.isDirectory()) {
                child.setSize(f.length());
            }
            children.add(child);

            if (f.isDirectory()) {
                buildTree(f, child, depth + 1, maxDepth);
            }
        }
        node.setChildren(children);
    }

    private String getExtension(String name) {
        int idx = name.lastIndexOf('.');
        return idx >= 0 ? name.substring(idx) : "";
    }

    public static class FileInfo {
        private String path;
        private String name;
        private long size;

        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public long getSize() { return size; }
        public void setSize(long size) { this.size = size; }
    }
}