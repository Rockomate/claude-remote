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

// ── Sessions ──
export const fetchSessions = (projectDir = '') =>
  api.get<Session[]>('/sessions', { params: { projectDir } });

export const deleteSession = (id: string, projectDir = '') =>
  api.delete(`/sessions/${id}`, { params: { projectDir } });

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

// ── Chat SSE ──
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
  const ctrl = new AbortController();
  const timeout = setTimeout(() => {
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
    if (!res.ok) { onError('HTTP ' + res.status); onDone(); return; }
    const reader = res.body?.getReader();
    if (!reader) { onError('No stream'); onDone(); return; }
    const dec = new TextDecoder();
    let buf = '';
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      buf += dec.decode(value, { stream: true });
      const lines = buf.split('\n');
      buf = lines.pop() || '';
      for (const line of lines) {
        const trimmed = line.trim();
        if (trimmed.startsWith('event: ')) continue;
        if (trimmed.startsWith('data: ')) {
          const data = trimmed.slice(6);
          if (data === 'DONE') continue;
          onLine(data);
        }
      }
    }
    onDone();
  }).catch((err) => {
    clearTimeout(timeout);
    if (err.name !== 'AbortError') onError(err.message);
    onDone();
  });
  return ctrl;
}