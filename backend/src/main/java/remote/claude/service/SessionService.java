package remote.claude.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import remote.claude.model.Session;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    // Replace special chars to match Claude Code's project dir naming scheme
    private static final String SANITIZE_PREFIX = "C--Users-MR-Desktop-";

    public String getSessionDir(String projectDir) {
        if (projectDir == null || projectDir.isEmpty()) return null;

        String sessionStorage = findSessionDirByCwd(projectDir);
        if (sessionStorage != null) return sessionStorage;

        // Fallback: compute sanitized name
        String sanitized = projectDir
                .replace(":", "")
                .replace("\\", "-")
                .replace("/", "-")
                .replaceAll("[^a-zA-Z0-9-]", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");

        if (!sanitized.startsWith("C--Users-MR-Desktop-")) {
            sanitized = SANITIZE_PREFIX + sanitized;
        }

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

            // Check first session file for matching cwd
            for (File jf : jsonlFiles) {
                try (java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.FileReader(jf))) {
                    String line;
                    int lines = 0;
                    while ((line = reader.readLine()) != null && lines < 10) {
                        if (line.contains("\"cwd\"") && line.contains(projectDir.replace("\\", "\\\\"))) {
                            return pd.getAbsolutePath();
                        }
                        lines++;
                    }
                } catch (Exception ignored) {}
                break; // Only check first file per project dir
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

            // Parse JSONL to count messages and extract preview
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            int msgCount = 0;
            StringBuilder preview = new StringBuilder();

            for (String line : lines) {
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

            session.setMessageCount(msgCount);
            session.setPreview(preview.length() > 0 ? preview.toString() : null);

            return session;
        } catch (IOException e) {
            log.warn("Failed to parse session file: {}", file.getName(), e);
            return null;
        }
    }
}