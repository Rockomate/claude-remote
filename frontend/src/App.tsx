import { useState, useEffect, useRef, useCallback } from 'react';
import { Link } from 'react-router-dom';
import { fetchSessions, deleteSession, fetchModels, fetchConfig, connectChat } from './api/client';
import type { Session, Model } from './api/client';

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
      <div>Claude Remote</div>
      <div style={{ fontSize: 12 }}>Type a message to start</div>
    </div>
  );
  return (
    <div className="message-list">
      {messages.map((m, i) => (
        <div key={i} className={`message ${m.role} ${m.streaming ? 'streaming' : ''}`}>
          {m.content.split('\n').map((ln, j) => <span key={j}>{ln}<br /></span>)}
          {m.streaming && <div className="typing-indicator"><span /><span /><span /></div>}
        </div>
      ))}
      <div ref={bottomRef} />
    </div>
  );
}

export default function App() {
  const [sessions, setSessions] = useState<Session[]>([]);
  const [activeSessionId, setActiveSessionId] = useState<string | null>(null);
  const [models, setModels] = useState<Model[]>([]);
  const [selectedModel, setSelectedModel] = useState('');
  const [projectDir, setProjectDir] = useState('');
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const abortRef = useRef<AbortController | null>(null);

  useEffect(() => {
    fetchConfig().then(r => {
      setProjectDir(r.data.defaultProjectDir);
    });
    fetchModels().then(r => {
      setModels(r.data);
      if (r.data.length > 0) setSelectedModel(r.data[0].id);
    });
  }, []);

  const loadSessions = useCallback(() => {
    fetchSessions(projectDir).then(r => setSessions(r.data));
  }, [projectDir]);

  useEffect(() => {
    loadSessions();
  }, [loadSessions]);

  const handleSend = () => {
    const text = input.trim();
    if (!text || loading) return;
    setInput('');
    setLoading(true);
    setMessages(prev => [...prev, { role: 'user', content: text }, { role: 'assistant', content: '', streaming: true }]);
    let fullContent = '';
    const controller = connectChat(text, activeSessionId, selectedModel, projectDir,
      (line) => {
        fullContent += line + '\n';
        setMessages(prev => { const u = [...prev]; if (u.length > 0) u[u.length - 1] = { role: 'assistant', content: fullContent, streaming: true }; return u; });
      },
      (err) => { setMessages(prev => [...prev, { role: 'error', content: err }]); setLoading(false); },
      () => {
        setMessages(prev => { const u = [...prev]; if (u.length > 0) u[u.length - 1] = { role: 'assistant', content: fullContent, streaming: false }; return u; });
        setLoading(false);
        loadSessions();
      }
    );
    abortRef.current = controller;
  };

  const handleNewSession = () => { setActiveSessionId(null); setMessages([]); };
  const handleDeleteSession = async (id: string) => {
    try { await deleteSession(id, projectDir); loadSessions(); if (activeSessionId === id) { setActiveSessionId(null); setMessages([]); } } catch {}
  };
  const handleKeyDown = (e: React.KeyboardEvent) => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); handleSend(); } };

  return (
    <div className="app-layout">
      <Sidebar sessions={sessions} activeSession={activeSessionId} onSelect={setActiveSessionId}
        onNew={handleNewSession} onDelete={handleDeleteSession} open={sidebarOpen} onClose={() => setSidebarOpen(false)} />
      <div className="main-panel">
        <div className="chat-header">
          <button className="menu-btn" onClick={() => setSidebarOpen(true)}>&#9776;</button>
          <h1 style={{ fontSize: 13, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
            {activeSessionId ? `Session ${activeSessionId.slice(0, 8)}` : 'New Session'}
            <span style={{ color: '#666', marginLeft: 8, fontSize: 11 }}>{projectDir.slice(projectDir.lastIndexOf('\\') + 1)}</span>
          </h1>
          {models.length > 0 && (
            <select className="model-select" value={selectedModel} onChange={e => setSelectedModel(e.target.value)}>
              {models.map(m => <option key={m.id} value={m.id}>{m.name || m.id}</option>)}
            </select>
          )}
          <Link to="/settings" className="icon-btn" style={{ textDecoration: 'none' }} title="Settings">&#9881;</Link>
        </div>
        <MessageList messages={messages} />
        <div className="input-area">
          <div className="input-wrapper">
            <textarea placeholder="Type a message..." value={input} onChange={e => setInput(e.target.value)} onKeyDown={handleKeyDown} rows={1} />
          </div>
          <button className="send-btn" onClick={handleSend} disabled={loading || !input.trim()}>{loading ? '...' : 'Send'}</button>
        </div>
      </div>
    </div>
  );
}