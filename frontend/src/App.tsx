import { useState, useEffect, useRef, useCallback } from 'react';
import {
  fetchSessions, deleteSession, fetchModels,
  fetchFileTree, fetchConfig, readFile, connectChat
} from './api/client';
import type { Session, Model, AppConfig, FileTreeNode } from './api/client';

interface Message {
  role: 'user' | 'assistant' | 'error';
  content: string;
  streaming?: boolean;
}

function Sidebar({ sessions, activeSession, onSelect, onNew, onDelete, open, onClose }: {
  sessions: Session[]; activeSession: string | null;
  onSelect: (id: string) => void; onNew: () => void; onDelete: (id: string) => void;
  open: boolean; onClose: () => void;
}) {
  return (<>
    <div className={`sidebar-overlay ${open ? 'open' : ''}`} onClick={onClose} />
    <div className={`sidebar ${open ? 'mobile-open' : ''}`}>
      <div className="sidebar-header">
        <h2>Sessions</h2>
        <button className="close-btn" onClick={onClose}>&times;</button>
      </div>
      <button className="new-session-btn" onClick={() => { onNew(); onClose(); }}>+ New Session</button>
      <div className="sidebar-sessions">
        {sessions.length === 0 && <div style={{ color: '#666', textAlign: 'center', padding: 24, fontSize: 13 }}>No sessions yet</div>}
        {sessions.map(s => (
          <div key={s.id} className={`session-item ${s.id === activeSession ? 'active' : ''}`}
            onClick={() => { onSelect(s.id); onClose(); }}
            onContextMenu={(e) => { e.preventDefault(); if (confirm('Delete this session?')) onDelete(s.id); }}>
            <div className="session-name">{s.name}</div>
            <div className="session-meta">{s.messageCount} msgs &middot; {s.updatedAt?.slice(0, 10)}</div>
            {s.preview && <div className="session-preview">{s.preview}</div>}
          </div>
        ))}
      </div>
    </div>
  </>);
}

function MessageList({ messages }: { messages: Message[] }) {
  const bottomRef = useRef<HTMLDivElement>(null);
  useEffect(() => { bottomRef.current?.scrollIntoView({ behavior: 'smooth' }); }, [messages]);
  if (messages.length === 0) return (
    <div className="empty-state">
      <div style={{ fontSize: 40, marginBottom: 8 }}>&#9670;</div>
      <div>Connect to Claude Code</div>
      <div style={{ fontSize: 12 }}>Type a message to start</div>
    </div>
  );
  return (
    <div className="message-list">
      {messages.map((m, i) => (
        <div key={i} className={`message ${m.role} ${m.streaming ? 'streaming' : ''}`}>
          {m.content}
          {m.streaming && <div className="typing-indicator"><span /><span /><span /></div>}
        </div>
      ))}
      <div ref={bottomRef} />
    </div>
  );
}

function FileBrowser({ onClose }: { onClose: () => void }) {
  const [tree, setTree] = useState<FileTreeNode | null>(null);
  const [expanded, setExpanded] = useState<Set<string>>(new Set());
  const [fileContent, setFileContent] = useState<string | null>(null);
  useEffect(() => { fetchFileTree('', 2).then(r => setTree(r.data)).catch(() => {}); }, []);
  const toggle = (path: string) => setExpanded(prev => { const n = new Set(prev); n.has(path) ? n.delete(path) : n.add(path); return n; });
  const openFile = async (path: string) => { try { const r = await readFile(path); setFileContent(r.data.content); } catch {} };
  const renderNode = (node: FileTreeNode, depth: number) => (
    <div key={node.path}>
      <div className="file-item" style={{ paddingLeft: 12 + depth * 16 }}
        onClick={() => node.directory ? toggle(node.path) : openFile(node.path)}>
        <span className="icon">{node.directory ? (expanded.has(node.path) ? '▾' : '▸') : '📄'}</span>
        <span className="file-name">{node.name}</span>
        {!node.directory && <span className="file-size">{node.size > 1024 ? `${(node.size / 1024).toFixed(1)}KB` : `${node.size}B`}</span>}
      </div>
      {node.directory && expanded.has(node.path) && node.children?.map(c => renderNode(c, depth + 1))}
    </div>
  );
  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal" onClick={e => e.stopPropagation()}>
        <div className="modal-header">
          <h3>{fileContent !== null ? 'File Preview' : 'File Browser'}</h3>
          <div>
            {fileContent && <button className="icon-btn" onClick={() => setFileContent(null)} style={{ fontSize: 12, marginRight: 8 }}>Back</button>}
            <button className="icon-btn" onClick={onClose}>&times;</button>
          </div>
        </div>
        <div className="modal-content">
          {fileContent !== null ? <pre style={{ fontSize: 12, lineHeight: 1.5, whiteSpace: 'pre-wrap', padding: 12 }}>{fileContent}</pre>
            : tree ? renderNode(tree, 0) : <div style={{ color: '#666', textAlign: 'center', padding: 24 }}>Loading...</div>}
        </div>
      </div>
    </div>
  );
}

function ConfigPanel({ onClose }: { onClose: () => void }) {
  const [config, setConfig] = useState<AppConfig | null>(null);
  useEffect(() => { fetchConfig().then(r => setConfig(r.data)).catch(() => {}); }, []);
  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal" style={{ maxWidth: 450 }} onClick={e => e.stopPropagation()}>
        <div className="modal-header"><h3>Configuration</h3><button className="icon-btn" onClick={onClose}>&times;</button></div>
        <div className="config-form">
          <div className="config-field"><label>Claude CLI Path</label><input value={config?.claudePath || ''} readOnly /></div>
          <div className="config-field"><label>Default Project Directory</label><input value={config?.defaultProjectDir || ''} readOnly /></div>
          <div className="config-field"><label>Available Models</label>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
              {config?.models.map(m => <span key={m.id} style={{ background: '#0f3460', padding: '4px 10px', borderRadius: 16, fontSize: 12, color: '#a0a0b0' }}>{m.name}</span>)}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

export default function App() {
  const [sessions, setSessions] = useState<Session[]>([]);
  const [activeSessionId, setActiveSessionId] = useState<string | null>(null);
  const [models, setModels] = useState<Model[]>([]);
  const [selectedModel, setSelectedModel] = useState('opus');
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [showFileBrowser, setShowFileBrowser] = useState(false);
  const [showConfig, setShowConfig] = useState(false);
  const abortRef = useRef<AbortController | null>(null);

  useEffect(() => {
    fetchModels().then(r => setModels(r.data));
    fetchSessions().then(r => setSessions(r.data));
    fetchConfig().then(r => {});
  }, []);

  const loadSessions = useCallback(() => { fetchSessions().then(r => setSessions(r.data)); }, []);

  const handleSend = () => {
    const text = input.trim();
    if (!text || loading) return;
    setInput('');
    setLoading(true);
    setMessages(prev => [...prev, { role: 'user', content: text }, { role: 'assistant', content: '', streaming: true }]);
    let collected = '';
    const controller = connectChat(text, activeSessionId, selectedModel, '',
      (line) => {
        collected += line;
        setMessages(prev => { const u = [...prev]; if (u.length > 0) u[u.length - 1] = { role: 'assistant', content: collected, streaming: true }; return u; });
      },
      (err) => { setMessages(prev => [...prev, { role: 'error', content: err }]); setLoading(false); },
      () => {
        setMessages(prev => { const u = [...prev]; if (u.length > 0) u[u.length - 1] = { role: 'assistant', content: collected, streaming: false }; return u; });
        setLoading(false);
        loadSessions();
      }
    );
    abortRef.current = controller;
  };

  const handleNewSession = () => { setActiveSessionId(null); setMessages([]); };

  const handleDeleteSession = async (id: string) => {
    try { await deleteSession(id); loadSessions(); if (activeSessionId === id) { setActiveSessionId(null); setMessages([]); } } catch {}
  };

  const handleKeyDown = (e: React.KeyboardEvent) => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); handleSend(); } };

  return (
    <div className="app-layout">
      <Sidebar sessions={sessions} activeSession={activeSessionId} onSelect={setActiveSessionId}
        onNew={handleNewSession} onDelete={handleDeleteSession} open={sidebarOpen} onClose={() => setSidebarOpen(false)} />
      <div className="main-panel">
        <div className="chat-header">
          <button className="menu-btn" onClick={() => setSidebarOpen(true)}>&#9776;</button>
          <h1>{activeSessionId ? `Session ${activeSessionId.slice(0, 8)}` : 'New Session'}</h1>
          <select className="model-select" value={selectedModel} onChange={e => setSelectedModel(e.target.value)}>
            {models.map(m => <option key={m.id} value={m.id}>{m.name}</option>)}
          </select>
          <button className="config-btn" onClick={() => setShowConfig(true)} title="Config">&#9881;</button>
        </div>
        <MessageList messages={messages} />
        <div className="input-area">
          <div className="input-wrapper">
            <button className="icon-btn" onClick={() => setShowFileBrowser(true)} title="Files">&#128193;</button>
            <textarea placeholder="Type a message..." value={input} onChange={e => setInput(e.target.value)} onKeyDown={handleKeyDown} rows={1} />
          </div>
          <button className="send-btn" onClick={handleSend} disabled={loading || !input.trim()}>{loading ? '...' : 'Send'}</button>
        </div>
      </div>
      {showFileBrowser && <FileBrowser onClose={() => setShowFileBrowser(false)} />}
      {showConfig && <ConfigPanel onClose={() => setShowConfig(false)} />}
    </div>
  );
}