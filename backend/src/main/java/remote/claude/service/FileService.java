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
    private static final Set<String> BINARY_EXTENSIONS = Set.of(
            ".exe", ".dll", ".so", ".dylib", ".jar", ".war", ".zip", ".tar",
            ".gz", ".png", ".jpg", ".jpeg", ".gif", ".ico", ".svg",
            ".mp3", ".mp4", ".avi", ".mov", ".pdf", ".doc", ".docx");

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

    public String readFileContent(String filePath) {
        File file = new File(filePath);
        if (!file.exists() || !file.isFile()) return null;

        String ext = getExtension(file.getName()).toLowerCase();
        if (BINARY_EXTENSIONS.contains(ext)) {
            return "[Binary file: " + file.getName() + "]";
        }

        try {
            return Files.readString(file.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "[Failed to read: " + e.getMessage() + "]";
        }
    }

    public FileInfo saveFile(String dirPath, String fileName, byte[] content) throws IOException {
        File targetDir = new File(dirPath);
        if (!targetDir.exists()) targetDir.mkdirs();

        File target = new File(targetDir, fileName);
        Files.write(target.toPath(), content);

        FileInfo info = new FileInfo();
        info.setPath(target.getAbsolutePath());
        info.setName(fileName);
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
            // Skip hidden files/dirs and node_modules, target, .git
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