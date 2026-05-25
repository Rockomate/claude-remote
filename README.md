# Claude Remote

Mobile remote control panel for [Claude Code](https://claude.ai/code). Access Claude Code from your phone via a web UI, using your own API proxy (OpenRouter, Claude Desktop 3P, or any Anthropic-compatible endpoint).

## Features

- **Mobile Chat UI** — Send prompts and read responses from any phone browser
- **Session Management** — View, resume, and delete Claude Code sessions across projects
- **Multi-Project** — Switch between different working directories
- **Model Switching** — Auto-detects available models from your API proxy
- **File Browser** — Browse project files and upload from phone
- **Streaming SSE** — Real-time response streaming via Server-Sent Events
- **Configurable** — Settings page to change working directory, proxy URL, and more
- **Secure** — Path traversal protection, input validation, optional token auth
- **Zero Cloud** — Everything runs on your machine; Tailscale provides private connectivity

## Architecture

```
Phone Browser (Tailscale IP)
        │
        ▼
  Spring Boot (port 3000)
        │
        ├── spawns: claude --print --resume <id> --model <model>
        │      (inherits env: ANTHROPIC_BASE_URL, ANTHROPIC_AUTH_TOKEN, etc.)
        │
        ├── reads: ~/.claude/projects/*/  (session JSONL files)
        │
        └── serves: React SPA (static resources)
```

## Prerequisites

- **Windows** (primary dev platform; Linux/Mac should also work with minor path adjustments)
- **JDK 21** (IntelliJ's bundled JDK works — see build script)
- **Node.js 18+** (for frontend build only)
- **Claude Code CLI** installed (`npm install -g @anthropic-ai/claude-code`)
- **Tailscale** (for secure phone access; `winget install tailscale`)
- **An API proxy** — Claude Desktop 3P, OpenRouter, or any Anthropic-compatible endpoint

## Quick Start

```bash
# 1. Clone
git clone https://github.com/Rockomate/claude-remote.git
cd claude-remote

# 2. Build frontend
cd frontend
npm install
npm run build

# 3. Copy frontend to backend
cp -r dist/* ../backend/src/main/resources/static/

# 4. Configure (edit backend/src/main/resources/application.yml)
#    Set claude-path and default-project-dir for your machine

# 5. Start backend
cd ../backend
./mvnw spring-boot:run -s .mvn/settings-custom.xml

# 6. Open on phone
#    http://<your-tailscale-ip>:3000
```

## Configuration

### application.yml

```yaml
server:
  port: 3000

claude-remote:
  claude-path: "C:\\Users\\MR\\AppData\\Roaming\\npm\\claude.cmd"
  default-project-dir: "C:\\Users\\MR\\Desktop\\my-project"
  auth:
    token: ""          # Set a token to enable API authentication
  proxy-base-url: "http://127.0.0.1:15721/claude-desktop"
  models:
    - id: "claude-opus-4-7"
      name: "Claude Opus 4.7"
      provider: "anthropic"
```

### Settings Page

Navigate to `/settings` on the web UI to:

- **Switch working directory** — Select from auto-detected Claude Code projects
- **Change proxy URL** — Point to a different API endpoint
- **Set auth token** — Protect API access

Settings are saved to `~/.claude-remote-config.json` and persist across restarts.

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/models` | List available models (auto-detected from proxy) |
| GET | `/api/sessions?projectDir=` | List sessions for a project |
| DELETE | `/api/sessions/{id}` | Delete a session |
| POST | `/api/chat` | Send prompt, returns SSE stream |
| POST | `/api/chat/cancel` | Cancel running chat |
| GET | `/api/files/tree?dir=&depth=` | Browse file tree |
| GET | `/api/files/read?path=` | Read file content |
| POST | `/api/files/upload` | Upload file (multipart) |
| GET | `/api/config` | Get current configuration |
| PUT | `/api/config` | Update configuration |
| GET | `/api/config/projects` | List Claude Code projects |

## Project Structure

```
claude-remote/
├── backend/                     # Spring Boot 3.x (JDK 21)
│   ├── pom.xml
│   ├── build.cmd                # Windows build script
│   └── src/main/java/remote/claude/
│       ├── ClaudeRemoteApplication.java
│       ├── config/
│       │   ├── WebConfig.java       # CORS
│       │   ├── ClaudeCliConfig.java # Configuration properties
│       │   └── AuthFilter.java      # Token authentication
│       ├── controller/
│       │   ├── ChatController.java      # SSE streaming chat
│       │   ├── SessionController.java   # Session CRUD
│       │   ├── FileController.java      # File browse & upload
│       │   ├── ModelController.java     # Model list (proxy auto-detect)
│       │   └── ConfigController.java    # Settings & projects
│       ├── service/
│       │   ├── ClaudeCliService.java    # CLI subprocess management
│       │   ├── SessionService.java      # JSONL session parsing
│       │   └── FileService.java         # File operations
│       ├── model/Session.java
│       └── dto/
├── frontend/                    # React + Vite + TypeScript
│   ├── src/
│   │   ├── main.tsx                 # Router setup
│   │   ├── App.tsx                  # Chat UI
│   │   ├── Settings.tsx             # Settings page
│   │   ├── api/client.ts            # API client & SSE
│   │   └── styles/global.css        # Mobile-first CSS
│   └── vite.config.ts
├── .gitignore
└── README.md
```

## Security

- **Command injection prevention**: User input is passed via `ProcessBuilder` argument lists (never shell strings)
- **Path traversal protection**: File access is resolved relative to base directory with canonical path checks
- **Model validation**: Only configured model IDs are accepted
- **File upload limits**: 10MB max, filename sanitization
- **Token authentication**: Optional Bearer token for API endpoints
- **CORS**: Configured for Tailscale private network access

## Troubleshooting

**"Model route not configured" error**
Your API proxy doesn't recognize the model name. Either:
- Configure the model route in your proxy (Claude Desktop settings)
- Update `application.yml` models list to match what your proxy supports
- The models list is auto-detected from the proxy at startup

**Port 3000 already in use**
```bash
netstat -ano | findstr :3000
taskkill /PID <pid> /F
```

**Can't access from phone**
- Ensure both devices are on the same Tailscale network (`tailscale status`)
- Check Windows Firewall allows port 3000 on the Tailscale interface
- Verify the IP in the phone URL matches `tailscale ip -4`

## License

MIT

## Contributing

Contributions welcome! Open an issue or PR at [github.com/Rockomate/claude-remote](https://github.com/Rockomate/claude-remote).
