package remote.claude.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import remote.claude.model.Session;
import remote.claude.model.ChatMessage;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
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

        String sessionStorage = findSessionDirByCwd(projectDir);
        if (sessionStorage != null) return sessionStorage;

        // Fallback: compute sanitized name (works for ASCII paths, C: drive)
        String sanitized = projectDir
                .replace(":", "")
                .replace("\\", "-")
                .replace("/", "-")
                .replaceAll("[^a-zA-Z0-9-]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");

        String userHome = System.getProperty("user.home");
        return userHome + "\\.claude\\projects\\" + sanitized;
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
        for (File f : files) {
            Session session = parseSessionFile(f);
            if (session != null) sessions.add(session);
        }

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
        if (after.startsWith("[")) {
            int textIdx = after.indexOf("\"text\":\"");
            if (textIdx < 0) return null;
            int t1 = textIdx + 8;
            StringBuilder sb = new StringBuilder();
            for (int i = t1; i < after.length(); i++) {
                char c = after.charAt(i);
                if (c == '\\') { sb.append(after.charAt(++i)); continue; }
                if (c == '"') break;
                sb.append(c);
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
            Session session = new Session();
            session.setId(sessionId);
            session.setProjectDir(file.getParentFile().getName());
            session.setName("Session " + sessionId.substring(0, 8));

            long lastModified = file.lastModified();
            session.setUpdatedAt(LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(lastModified),
                    ZoneId.systemDefault()));
            session.setCreatedAt(session.getUpdatedAt());

            // Parse JSONL to extract name, messages, count and preview
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            int msgCount = 0;
            StringBuilder preview = new StringBuilder();
            String customTitle = null;
            boolean titleFound = false;

            for (String line : lines) {
                // Extract custom title (last occurrence = current name)
                if (!titleFound && line.contains("\"type\":\"custom-title\"")) {
                    // Next line or same line has the content
                    int ci = line.indexOf("\"custom-title\"");
                    // The title content could be in the next JSON object
                    titleFound = true; // Mark to find the actual title text
                }
                if (titleFound && customTitle == null) {
                    // Try to extract title from quotes in content field
                    int ci = line.indexOf("\"content\"");
                    if (ci >= 0) {
                        int q1 = line.indexOf('"', ci + 10);
                        if (q1 >= 0) {
                            int q2 = line.indexOf('"', q1 + 1);
                            if (q2 >= 0) {
                                customTitle = line.substring(q1 + 1, q2);
                                if (customTitle.length() > 60) customTitle = customTitle.substring(0, 60);
                            }
                        }
                    }
                }

                if (line.contains("\"role\":\"user\"") || line.contains("\"type\":\"message\"")) {
                    msgCount++;
                }
                // Extract content preview from first user message
                if (msgCount == 1 && preview.isEmpty() && line.contains("\"content\"")) {
                    int idx = line.indexOf("\"content\"");
                    String snippet = line.substring(Math.min(idx + 30, line.length()));
                    snippet = snippet.replaceAll("[\\n\\r]", " ").trim();
                    if (snippet.length() > 200) snippet = snippet.substring(0, 200) + "...";
                    preview.append(snippet);
                }
            }

            // Use custom title if found, otherwise first 30 chars of preview
            if (customTitle != null && !customTitle.isEmpty()) {
                session.setName(customTitle);
            } else if (preview.length() > 0) {
                String nameFromPreview = preview.toString().replaceAll("[\\n\\r]", " ").trim();
                if (nameFromPreview.length() > 40) nameFromPreview = nameFromPreview.substring(0, 40) + "...";
                session.setName(nameFromPreview);
            }

            session.setMessageCount(msgCount);
            session.setPreview(preview.length() > 0 ? preview.toString() : null);

            return session;
        } catch (IOException e) {
            log.warn("Failed to parse session file: {}", file.getName(), e);
            return null;
        }
    }
}