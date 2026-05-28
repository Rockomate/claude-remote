# Claude Remote

Mobile remote control panel for [Claude Code](https://claude.ai/code). Control Claude Code from your phone using a web UI, connected via Tailscale private network.

## Features

- **Chat Interface** — Send prompts, view responses with Markdown rendering
- **Session Management** — Browse, search, resume, and delete Claude Code sessions
- **Multi-Project** — Switch between different working directories with auto-detection
- **Model Switching** — Auto-detects available models from your API proxy
- **File Browser** — Browse project files and preview content
- **Real-time Streaming** — SSE streaming for instant response delivery
- **Settings** — Configure working directory, proxy URL, auth token via web UI
- **Secure** — Path traversal protection, input validation, optional token auth
- **Mobile-first** — Responsive design optimized for phone browsers

## Architecture

```
Phone Browser (Tailscale IP)
        │
        ▼
  Spring Boot 3.x (port 3000)
        │
        ├── spawns: claude --print --resume <id> --model <model>
        │      (inherits env: ANTHROPIC_BASE_URL, ANTHROPIC_AUTH_TOKEN)
        │
        ├── reads: ~/.claude/projects/*/  (session JSONL files)
        │
        └── serves: React SPA (static resources)
```

## Quick Start

### Prerequisites

- **Windows** (primary dev platform; Linux/Mac work with path adjustments)
- **JDK 21** (`winget install Microsoft.OpenJDK.21`)
- **Node.js 18+** (`winget install OpenJS.NodeJS.LTS`)
- **Claude Code CLI** (`npm install -g @anthropic-ai/claude-code`)
- **Tailscale** (`winget install tailscale`)
- **API proxy** — Claude Desktop 3P, OpenRouter, or Anthropic-compatible endpoint

### Install & Run

```bash
# Clone
git clone https://github.com/Rockomate/claude-remote.git
cd claude-remote

# Build frontend
cd frontend
npm install
npm run build

# Copy to backend
cp -r dist/* ../backend/src/main/resources/static/

# Configure
# Edit backend/src/main/resources/application.yml
# Set claude-path and default-project-dir

# Start
cd ../backend
./mvnw spring-boot:run -s .mvn/settings-custom.xml

# Open on phone
# http://<your-tailscale-ip>:3000
```

### Configuration

Edit `backend/src/main/resources/application.yml`:

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

### Settings Page

Access `/settings` on the web UI to:

- **Switch working directory** — Auto-detected Claude Code projects
- **Change proxy URL** — Point to different API endpoint
- **Set auth token** — Protect API access

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/api/models` | List available models (auto-detected) |
| `GET` | `/api/sessions?projectDir=` | List sessions |
| `GET` | `/api/sessions/{id}/messages` | Get session history |
| `DELETE` | `/api/sessions/{id}` | Delete session |
| `POST` | `/api/chat` | Send prompt (SSE stream) |
| `POST` | `/api/chat/cancel` | Cancel running chat |
| `GET` | `/api/files/tree?dir=&depth=` | Browse file tree |
| `GET` | `/api/files/read?path=` | Read file content |
| `POST` | `/api/files/upload` | Upload file |
| `GET` | `/api/config` | Get configuration |
| `PUT` | `/api/config` | Update configuration |
| `GET` | `/api/config/projects` | List projects |

## Development

```bash
# Frontend dev server (hot reload)
cd frontend
npm run dev  # http://localhost:5173

# Backend (separate terminal)
cd backend
./mvnw spring-boot:run -s .mvn/settings-custom.xml

# Run tests
cd frontend && npm test
cd backend && ./mvnw test
```

## Security

- **Command injection**: `ProcessBuilder` argument lists (no shell strings)
- **Path traversal**: Canonical path verification for all file access
- **Model validation**: Only configured models accepted
- **File upload**: 10MB max, filename sanitization
- **Token auth**: Optional Bearer token for API endpoints
- **CORS**: Configured for Tailscale private network

## Troubleshooting

| Issue | Solution |
|-------|----------|
| Model route not configured | Update `application.yml` models or configure proxy |
| Port 3000 in use | `netstat -ano \| findstr :3000` then `taskkill /PID <pid> /F` |
| Can't access from phone | Check Tailscale status, Windows Firewall, IP address |
| CLI not found | Verify `claude-path` in `application.yml` |
| API errors | Check proxy is running and `ANTHROPIC_AUTH_TOKEN` is set |

## License

MIT

## Contributing

1. Fork the repo
2. Create feature branch
3. Add tests for new functionality
4. Submit PR

[github.com/Rockomate/claude-remote](https://github.com/Rockomate/claude-remote)
