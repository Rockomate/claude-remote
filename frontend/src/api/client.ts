import axios from 'axios';

const API_BASE = '/api';

export const api = axios.create({
  baseURL: API_BASE,
  timeout: 15000,
});

// ── Types ──
export interface Model {
  id: string;
  name: string;
  provider: string;
}

export interface Session {
  id: string;
  name: string;
  createdAt: string;
  updatedAt: string;
  projectDir: string;
  messageCount: number;
  preview: string | null;
  tags: string[];
}

export interface FileTreeNode {
  name: string;
  path: string;
  directory: boolean;
  size: number;
  children?: FileTreeNode[];
}

export interface AppConfig {
  claudePath: string;
  defaultProjectDir: string;
  proxyBaseUrl: string;
  models: Model[];
}

export interface ProjectInfo {
  path: string;
  name: string;
  dirName: string;
  sessionCount: string;
}

export interface ChatMessageItem {
  role: string;
  content: string;
  timestamp: string | null;
}

export interface ApiResponse<T> {
  data: T;
  status: number;
  statusText: string;
}

export interface ApiError {
  message: string;
  status?: number;
  code?: string;
}

// ── Logging utility ──
const log = (level: 'info' | 'warn' | 'error', msg: string, data?: unknown) => {
  const ts = new Date().toISOString();
  const prefix = `[${ts}] [${level.toUpperCase()}]`;
  if (level === 'error') console.error(prefix, msg, data);
  else if (level === 'warn') console.warn(prefix, msg, data);
  else console.log(prefix, msg, data);
};

// ── Sessions ──
export const fetchSessions = (projectDir = '') =>
  api.get<Session[]>('/sessions', { params: { projectDir } });

export const deleteSession = (id: string, projectDir = '') =>
  api.delete(`/sessions/${id}`, { params: { projectDir } });

export const fetchSessionMessages = (id: string, projectDir = '') =>
  api.get<ChatMessageItem[]>(`/sessions/${id}/messages`, { params: { projectDir } });

export const exportSession = (id: string, projectDir = '') =>
  api.get<string>(`/sessions/export/${id}`, { params: { projectDir }, responseType: 'text' });

// ── Models ──
export const fetchModels = () =>
  api.get<Model[]>('/models');

// ── Config ──
export const fetchConfig = () =>
  api.get<AppConfig>('/config');

export const updateConfig = (data: Record<string, unknown>) =>
  api.put<{ status: string; path: string }>('/config', data);

export const fetchProjects = () =>
  api.get<ProjectInfo[]>('/config/projects');

// ── Files ──
export const fetchFileTree = (dir = '', depth = 2) =>
  api.get<FileTreeNode>('/files/tree', { params: { dir, depth } });

export const readFile = (path: string) =>
  api.get<{ content: string; path: string }>('/files/read', { params: { path } });

export const uploadFile = (file: File, dir = '') => {
  const fd = new FormData();
  fd.append('file', file);
  if (dir) fd.append('dir', dir);
  return api.post('/files/upload', fd);
};

// ── Chat SSE with logging ──
export function connectChat(
  prompt: string,
  sessionId: string | null,
  model: string | null,
  projectDir: string | null,
  onLine: (text: string) => void,
  onError: (err: string) => void,
  onDone: () => void,
  timeoutMs = 120000
): AbortController {
  log('info', `Chat: model=${model || 'default'}, session=${sessionId || 'new'}, dir=${projectDir || 'default'}`);
  const ctrl = new AbortController();
  const timeout = setTimeout(() => {
    log('error', 'Chat timeout after', timeoutMs + 'ms');
    ctrl.abort();
    onError('Request timed out');
    onDone();
  }, timeoutMs);

  fetch(`${API_BASE}/chat`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ prompt, sessionId, model, projectDir }),
    signal: ctrl.signal,
  }).then(async (res) => {
    clearTimeout(timeout);
    if (!res.ok) {
      const errText = await res.text().catch(() => 'Unknown error');
      log('error', `Chat HTTP ${res.status}:`, errText);
      onError(`HTTP ${res.status}: ${errText}`);
      onDone();
      return;
    }
    const reader = res.body?.getReader();
    if (!reader) { onError('No stream'); onDone(); return; }
    const dec = new TextDecoder();
    let buf = '';
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      buf += dec.decode(value, { stream: true });
      // Process complete SSE frames (separated by \n\n)
      const frames = buf.split('\n\n');
      buf = frames.pop() || '';
      for (const frame of frames) {
        if (!frame.trim()) continue;
        const lines = frame.split('\n');
        let eventType = '';
        let data = '';
        for (const line of lines) {
          if (line.startsWith('event:')) {
            eventType = line.slice(6).trim();
          } else if (line.startsWith('data:')) {
            data = line.slice(5).trim();
          }
        }
        if (eventType === 'done' || data === 'DONE') {
          continue; // Skip done signal
        }
        if (data) {
          onLine(data);
        }
      }
    }
    onDone();
  }).catch((err) => {
    clearTimeout(timeout);
    log('error', 'Chat catch:', err.message);
    if (err.name !== 'AbortError') onError(err.message);
    onDone();
  });
  return ctrl;
}