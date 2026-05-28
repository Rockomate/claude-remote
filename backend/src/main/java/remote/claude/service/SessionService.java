package remote.claude.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import remote.claude.model.Session;
import remote.claude.model.ChatMessage;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    public String getSessionDir(String projectDir) {
        if (projectDir == null || projectDir.isEmpty()) return null;

        // Direct computation - Claude Code sanitizes: ":" → "-", "\" → "-", "/" → "-"
        // Do NOT collapse consecutive dashes - "C:\Users" becomes "C--Users" (double dash)
        String sanitized = projectDir
                .replace(":", "-")
                .replace("\\", "-")
                .replace("/", "-")
                .replaceAll("^-+|-+$", "");

        String userHome = System.getProperty("user.home");
        String result = userHome + "\\.claude\\projects\\" + sanitized;
        log.debug("Session dir for {}: {}", projectDir, result);
        return result;
    }

    /**
     * Find the session directory by scanning all project dirs for one
     * whose session files contain the given cwd.
     */
    private String findSessionDirByCwd(String projectDir) {
        String userHome = System.getProperty("user.home");
        File projectsRoot = new File(userHome + "\\.claude\\projects");
        if (!projectsRoot.exists() || !projectsRoot.isDirectory()) return null;

        File[] projectDirs = projectsRoot.listFiles(File::isDirectory);
        if (projectDirs == null) return null;

        for (File pd : projectDirs) {
            File[] jsonlFiles = pd.listFiles((f, n) -> n.endsWith(".jsonl"));
            if (jsonlFiles == null || jsonlFiles.length == 0) continue;

            for (File jf : jsonlFiles) {
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.FileReader(jf))) {
                    String line;
                    int lines = 0;
                    while ((line = reader.readLine()) != null && lines < 10) {
                        // Handle both forward and backslash in cwd
                        String normalizedStoredPath = projectDir.replace("\\", "\\\\");
                        if (line.contains("\"cwd\"")
                                && (line.contains(projectDir)
                                    || line.contains(normalizedStoredPath))) {
                            return pd.getAbsolutePath();
                        }
                        lines++;
                    }
                } catch (Exception ignored) {}
                break;
            }
        }
        return null;
    }

    public List<Session> listSessions(String projectDir) {
        String sessionDir = getSessionDir(projectDir);
        if (sessionDir == null) return Collections.emptyList();

        File dir = new File(sessionDir);
        if (!dir.exists() || !dir.isDirectory()) return Collections.emptyList();

        File[] files = dir.listFiles((d, name) -> name.endsWith(".jsonl"));
        if (files == null) return Collections.emptyList();

        List<Session> sessions = new ArrayList<>();
        int total = files.length;
        int parsed = 0;
        for (File f : files) {
            Session session = parseSessionFile(f);
            if (session != null) {
                sessions.add(session);
                parsed++;
            }
        }
        log.info("Session scan: {}/{} parsed from {}", parsed, total, sessionDir);

        // Sort by updatedAt descending
        sessions.sort((a, b) -> b.getUpdatedAt().compareTo(a.getUpdatedAt()));
        return sessions;
    }

    public Session getSession(String sessionId, String projectDir) {
        String sessionDir = getSessionDir(projectDir);
        if (sessionDir == null) return null;

        File f = new File(sessionDir, sessionId + ".jsonl");
        if (!f.exists()) return null;

        return parseSessionFile(f);
    }

    public List<ChatMessage> getSessionMessages(String sessionId, String projectDir) {
        String sessionDir = getSessionDir(projectDir);
        if (sessionDir == null) return Collections.emptyList();

        File f = new File(sessionDir, sessionId + ".jsonl");
        if (!f.exists()) return Collections.emptyList();

        List<ChatMessage> messages = new ArrayList<>();
        try {
            List<String> lines = Files.readAllLines(f.toPath(), StandardCharsets.UTF_8);
            for (String line : lines) {
                try {
                    // Extract role
                    String role = null;
                    int ri = line.indexOf("\"role\":\"");
                    if (ri >= 0) {
                        int rs = ri + 8;
                        int re = line.indexOf('"', rs);
                        if (re > rs) role = line.substring(rs, re);
                    }
                    // Only process user/assistant messages
                    if (role == null || (!role.equals("user") && !role.equals("assistant"))) continue;

                    // Extract content (can be string or array of blocks)
                    String content = extractContent(line);
                    if (content != null && !content.isEmpty()) {
                        messages.add(new ChatMessage(role, content, null));
                    }
                } catch (Exception ignored) {}
            }
        } catch (IOException e) {
            log.warn("Failed to read session messages: {}", f.getName(), e);
        }

        return messages;
    }

    private String extractContent(String jsonLine) {
        // Find "content": field
        int ci = jsonLine.indexOf("\"content\":");
        if (ci < 0) return null;

        String after = jsonLine.substring(ci + 10).trim();

        // Case 1: content is a string: "content":"some text"
        if (after.startsWith("\"")) {
            int q1 = 0;
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < after.length(); i++) {
                char c = after.charAt(i);
                if (c == '\\') { sb.append(after.charAt(++i)); continue; }
                if (c == '"') break;
                sb.append(c);
            }
            return sb.toString().trim();
        }

        // Case 2: content is array of blocks: "content":[{"type":"text","text":"..."}]
        // Extract ALL text blocks, not just the first one
        if (after.startsWith("[")) {
            StringBuilder sb = new StringBuilder();
            int searchFrom = 0;
            while (searchFrom < after.length()) {
                int textIdx = after.indexOf("\"text\":\"", searchFrom);
                if (textIdx < 0) break;
                int t1 = textIdx + 8;
                for (int i = t1; i < after.length(); i++) {
                    char c = after.charAt(i);
                    if (c == '\\') { sb.append(after.charAt(++i)); continue; }
                    if (c == '"') break;
                    sb.append(c);
                }
                sb.append("\n");
                searchFrom = textIdx + 8;
            }
            return sb.toString().trim();
        }

        return null;
    }

    public boolean deleteSession(String sessionId, String projectDir) {
        String sessionDir = getSessionDir(projectDir);
        if (sessionDir == null) return false;

        File f = new File(sessionDir, sessionId + ".jsonl");
        File metaDir = new File(sessionDir, sessionId);

        boolean deleted = true;
        if (f.exists()) deleted = f.delete();
        if (metaDir.exists()) {
            // Delete all files in meta directory
            File[] metaFiles = metaDir.listFiles();
            if (metaFiles != null) {
                for (File mf : metaFiles) mf.delete();
            }
            metaDir.delete();
        }
        return deleted;
    }

    private Session parseSessionFile(File file) {
        try {
            String sessionId = file.getName().replace(".jsonl", "");
            if (sessionId.length() < 8) return null;

            Session session = new Session();
            session.setId(sessionId);
            session.setProjectDir(file.getParentFile().getName());
            session.setName("Session " + sessionId.substring(0, 8));

            long lastModified = file.lastModified();
            session.setUpdatedAt(LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(lastModified),
                    ZoneId.systemDefault()));
            session.setCreatedAt(session.getUpdatedAt());

            // Read only first 32KB to avoid OOM on large JSONL files
            // This is enough to extract title, first user message, and message count
            int maxBytes = 32768;
            byte[] buffer = new byte[maxBytes];
            int bytesRead;
            try (var raf = new RandomAccessFile(file, "r")) {
                bytesRead = raf.read(buffer, 0, maxBytes);
            }
            if (bytesRead <= 0) return session;

            String chunk = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
            String[] lines = chunk.split("\n");

            int msgCount = 0;
            String preview = null;
            String customTitle = null;
            boolean titleFound = false;

            for (String line : lines) {
                if (line.isEmpty()) continue;

                if (!titleFound && line.contains("\"type\":\"custom-title\"")) {
                    titleFound = true;
                }
                if (titleFound && customTitle == null) {
                    String extracted = extractJsonField(line, "content");
                    if (extracted != null) {
                        customTitle = extracted.length() > 60 ? extracted.substring(0, 60) : extracted;
                    }
                }

                if (line.contains("\"role\":\"user\"") || line.contains("\"type\":\"message\"")) {
                    msgCount++;
                }

                if (msgCount == 1 && preview == null && line.contains("\"role\":\"user\"")) {
                    String content = extractJsonField(line, "content");
                    if (content != null) {
                        preview = content.replaceAll("[\\n\\r]", " ").trim();
                        if (preview.length() > 200) preview = preview.substring(0, 200) + "...";
                    }
                }
            }

            if (customTitle != null && !customTitle.isEmpty()) {
                session.setName(sanitizeName(customTitle));
            } else if (preview != null && !preview.isEmpty()) {
                session.setName(sanitizeName(preview));
            }

            session.setMessageCount(msgCount);
            session.setPreview(preview);
            return session;

        } catch (Exception e) {
            log.debug("Failed to parse session {}: {}", file.getName(), e.getMessage());
            return null;
        }
    }

    private String sanitizeName(String raw) {
        if (raw == null) return null;
        // Remove JSON artifacts, escape sequences, and control characters
        String clean = raw
                .replaceAll("[\\x00-\\x1F\\x7F]", "") // control chars
                .replaceAll("\\\\[nrt]", " ") // escaped newlines/tabs
                .replaceAll("\\\\\"", "\"") // escaped quotes
                .replaceAll("\"[a-zA-Z_]+\":\\s*\"?", "") // JSON field patterns
                .replaceAll("\\{[^}]{0,20}\\}", "") // short JSON fragments
                .replaceAll("\\s+", " ") // collapse whitespace
                .trim();
        if (clean.length() > 50) clean = clean.substring(0, 50) + "...";
        return clean.isEmpty() ? null : clean;
    }

    /**
     * Quick JSON field extraction without full parser.
     * Returns value of "field":"value" from a JSON line.
     */
    private String extractJsonField(String json, String field) {
        String search = "\"" + field + "\":";
        int idx = json.indexOf(search);
        if (idx < 0) return null;
        int start = idx + search.length();
        // Skip whitespace
        while (start < json.length() && json.charAt(start) == ' ') start++;
        if (start >= json.length()) return null;
        if (json.charAt(start) == '"') {
            // String value
            start++;
            StringBuilder sb = new StringBuilder();
            for (int i = start; i < json.length(); i++) {
                char c = json.charAt(i);
                if (c == '\\') { i++; continue; }
                if (c == '"') break;
                sb.append(c);
            }
            return sb.toString();
        }
        return null;
    }
}