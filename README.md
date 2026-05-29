# Claude Remote

Mobile remote control panel for [Claude Code](https://claude.ai/code). Control Claude Code from your phone using a web UI, connected via Tailscale private network.

## Features

### Chat & Conversation
- **SSE Streaming** — Real-time response streaming via raw ServletOutputStream
- **Markdown Rendering** — Full GFM support with syntax highlighting (VS Code Dark+ theme)
- **Copy Buttons** — One-click copy for markdown and plain text
- **Chat Retry** — Auto-retry on timeout (up to 2 retries)
- **Cancel** — Cancel running requests mid-stream

### Session Management
- **Browse & Resume** — View all Claude Code sessions across projects (140+)
- **Smart Names** — Session names auto-extracted from first user message or custom title
- **Search & Filter** — Search sessions by name or content
- **Export/Import** — Download sessions as .jsonl files, import from file
- **Pagination** — Sidebar paginates for large session lists

### Project & Model Switching
- **Multi-Project** — Switch between working directories via sidebar or Settings
- **Model Auto-Detect** — Automatically detects available models from proxy API
- **Project Confirmation** — Confirmation dialog before switching (prevents accidental loss)

### UI & UX
- **Dark/Light Theme** — Toggle between themes, persisted in localStorage
- **PWA Support** — Installable on phone home screen
- **Offline Indicator** — Shows [Offline] when backend is unreachable
- **Keyboard Shortcuts** — Ctrl+Enter send, Esc cancel/close
- **Mobile-first** — Responsive sidebar, auto-growing textarea

### File Management
- **File Browser** — Browse project files with expand/collapse tree
- **File Upload** — Upload files from phone to project directory

### Security & Config
- **Settings Page** — Configure working directory, proxy URL, auth token
- **Path Traversal Protection** — All file access validated against allowed paths
- **Token Authentication** — Optional Bearer token for API endpoints
- **CORS** — Configured for Tailscale private network

## Architecture

```
Phone Browser (Tailscale IP)
        │
        ▼
  Spring Boot 3.x (port 3000)
        │
        ├── spawns: claude --print --resume <id> --model <model>
        │      (inherits env: ANTHROPIC_BASE_URL, ANTHROPIC_AUTH_TOKEN, etc.)
        │
        ├── reads: ~/.claude/projects/*/  (session JSONL files)
        │
        └── serves: React SPA (static resources)
```

## Quick Start

```bash
# Clone
git clone https://github.com/Rockomate/claude-remote.git
cd claude-remote

# Build frontend
cd frontend
npm install && npm run build
cp -r dist/* ../backend/src/main/resources/static/

# Configure (edit backend/src/main/resources/application.yml)
# Set claude-path and default-project-dir for your machine

# Start
cd ../backend
./mvnw spring-boot:run -s .mvn/settings-custom.xml

# Open on phone
# http://<your-tailscale-ip>:3000
```

## Configuration

### application.yml
```yaml
server:
  port: 3000

claude-remote:
  claude-path: "C:\\Users\\YOU\\AppData\\Roaming\\npm\\claude.cmd"
  default-project-dir: "C:\\Users\\YOU\\Desktop\\my-project"
  auth:
    token: ""  # Set to enable API authentication
  proxy-base-url: "http://127.0.0.1:15721/claude-desktop"
```

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/chat` | Send prompt (SSE stream) |
| `POST` | `/api/chat/cancel` | Cancel running chat |
| `GET` | `/api/models` | List available models (auto-detected) |
| `GET` | `/api/sessions` | List sessions |
| `GET` | `/api/sessions/{id}/messages` | Get session history |
| `DELETE` | `/api/sessions/{id}` | Delete session |
| `GET` | `/api/sessions/export/{id}` | Export session as JSONL |
| `POST` | `/api/sessions/import` | Import session from file |
| `GET` | `/api/config` | Get configuration |
| `PUT` | `/api/config` | Update configuration |
| `GET` | `/api/config/projects` | List projects |
| `GET` | `/api/files/tree` | Browse file tree |
| `GET` | `/api/files/read` | Read file content |

## Security

- **Path traversal** — Canonical path verification for all file access
- **Input validation** — Prompt validation, filename sanitization
- **Token auth** — Optional Bearer token for API endpoints
- **CORS** — Configured for Tailscale private network
- **No shell injection** — ProcessBuilder argument lists (not shell strings)

## Troubleshooting

| Issue | Solution |
|-------|----------|
| Chat not working | Check proxy running and `ANTHROPIC_AUTH_TOKEN` set |
| Model not found | Auto-detects from proxy; configure in `application.yml` if needed |
| Port 3000 in use | `netstat -ano \| findstr :3000` then `taskkill /PID <pid> /F` |
| Can't access from phone | Check Tailscale status, Windows Firewall, IP address |
| CLI not found | Verify `claude-path` in `application.yml` |

## License

MIT

## Contributing

1. Fork the repo
2. Create feature branch
3. Add tests for new functionality
4. Submit PR

[github.com/Rockomate/claude-remote](https://github.com/Rockomate/claude-remote)
