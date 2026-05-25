import axios from 'axios';

const API_BASE = '/api';

export const api = axios.create({
  baseURL: API_BASE,
  timeout: 10000,
});

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
  models: Model[];
}

// Sessions
export const fetchSessions = (projectDir = '') =>
  api.get<Session[]>('/sessions', { params: { projectDir } });

export const deleteSession = (id: string, projectDir = '') =>
  api.delete(`/sessions/${id}`, { params: { projectDir } });

// Models
export const fetchModels = () =>
  api.get<Model[]>('/models');

// Config
export const fetchConfig = () =>
  api.get<AppConfig>('/config');

// Files
export const fetchFileTree = (dir = '', depth = 2) =>
  api.get<FileTreeNode>('/files/tree', { params: { dir, depth } });

export const readFile = (path: string) =>
  api.get<{ content: string; path: string }>('/files/read', { params: { path } });

export const uploadFile = (file: File, dir = '') => {
  const formData = new FormData();
  formData.append('file', file);
  if (dir) formData.append('dir', dir);
  return api.post('/files/upload', formData);
};

// Chat SSE
export function connectChat(
  prompt: string,
  sessionId: string | null,
  model: string | null,
  projectDir: string | null,
  onLine: (text: string) => void,
  onError: (err: string) => void,
  onDone: () => void
): AbortController {
  const controller = new AbortController();

  fetch(`${API_BASE}/chat`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ prompt, sessionId, model, projectDir }),
    signal: controller.signal,
  }).then(async (response) => {
    const reader = response.body?.getReader();
    if (!reader) { onError('No response stream'); onDone(); return; }

    const decoder = new TextDecoder();
    let buffer = '';

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;

      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split('\n');
      buffer = lines.pop() || '';

      for (const line of lines) {
        if (line.startsWith('event: ')) {
          const eventType = line.slice(7).trim();
          // Next line will be data
          continue;
        }
        if (line.startsWith('data: ')) {
          const data = line.slice(6).trim();
          // Look back for event type
          continue;
        }
        // Try to parse as JSON SSE format
        if (line.startsWith('{"')) {
          try {
            const parsed = JSON.parse(line);
            if (parsed.type === 'content_block_delta') {
              onLine(parsed.delta?.text || '');
            } else if (parsed.type === 'message') {
              onLine(parsed.content || '');
            }
          } catch { /* not JSON */ }
        } else if (line.trim()) {
          // Plain text line from Claude
          onLine(line);
        }
      }
    }
    onDone();
  }).catch((err) => {
    if (err.name !== 'AbortError') {
      onError(err.message);
    }
    onDone();
  });

  return controller;
}