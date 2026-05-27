import { useState, useEffect, useRef, useCallback } from 'react';
import { Link } from 'react-router-dom';
import Markdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { fetchSessions, deleteSession, fetchModels, fetchConfig, connectChat, fetchProjects } from './api/client';
import type { Session, Model, ProjectInfo } from './api/client';

interface Message {
  role: 'user' | 'assistant' | 'error';
  content: string;
  streaming?: boolean;
}

function ProjectSwitcher({ projects, currentDir, onSwitch }: {
  projects: ProjectInfo[]; currentDir: string; onSwitch: (dir: string) => void;
}) {
  const [open, setOpen] = useState(false);
  const name = currentDir.slice(currentDir.lastIndexOf('\\') + 1);
  return (
    <div style={{ position: 'relative' }}>
      <button className="icon-btn" onClick={() => setOpen(!open)} title="Switch project"
        style={{ fontSize: 12, padding: '4px 8px', background: '#0f3460', borderRadius: 6 }}>
        {name.length > 12 ? name.slice(0, 12) + '...' : name} &#9660;
      </button>
      {open && <>
        <div style={{ position: 'fixed', inset: 0, zIndex: 49 }} onClick={() => setOpen(false)} />
        <div style={{ position: 'absolute', top: '100%', right: 0, background: '#16213e', border: '1px solid #2a2a4a',
          borderRadius: 8, zIndex: 50, minWidth: 200, maxHeight: 300, overflow: 'auto', marginTop: 4 }}>
          {projects.map(p => (
            <div key={p.dirName} onClick={() => { onSwitch(p.path); setOpen(false); }}
              style={{ padding: '8px 12px', cursor: 'pointer', fontSize: 13,
                background: p.path === currentDir ? '#0f3460' : 'transparent',
                borderBottom: '1px solid #2a2a4a' }}>
              {p.name} <span style={{ color: '#666', fontSize: 11 }}>({p.sessionCount})</span>
            </div>
          ))}
        </div>
      </>}
    </div>
  );
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
        {sessions.length === 0 && <div style={{ color: '#666', textAlign: 'center', padding: 24, fontSize: 13 }}>No sessions</div>}
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

function Toast({ msg, onClose }: { msg: string; onClose: () => void }) {
  useEffect(() => { const t = setTimeout(onClose, 4000); return () => clearTimeout(t); }, []);
  return (
    <div style={{ position: 'fixed', bottom: 80, left: 16, right: 16, background: '#3a1a1a', color: '#ff6b6b',
      padding: '12px 16px', borderRadius: 8, zIndex: 300, fontSize: 13, textAlign: 'center' }}>
      {msg}
    </div>
  );
}

export default function App() {
  const [sessions, setSessions] = useState<Session[]>([]);
  const [activeSessionId, setActiveSessionId] = useState<string | null>(null);
  const [models, setModels] = useState<Model[]>([]);
  const [selectedModel, setSelectedModel] = useState('');
  const [projectDir, setProjectDir] = useState('');
  const [projects, setProjects] = useState<ProjectInfo[]>([]);
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [showCancel, setShowCancel] = useState(false);
  const [errorToast, setErrorToast] = useState('');
  const abortRef = useRef<AbortController | null>(null);
  const bottomRef = useRef<HTMLDivElement>(null);

  const scrollDown = () => bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  useEffect(() => { scrollDown(); }, [messages]);

  const init = useCallback(() => {
    fetchConfig().then(r => setProjectDir(r.data.defaultProjectDir));
    fetchModels().then(r => { setModels(r.data); if (r.data.length > 0) setSelectedModel(r.data[0].id); });
    fetchProjects().then(r => setProjects(r.data)).catch(() => setErrorToast('Failed to load projects'));
  }, []);

  useEffect(() => { init(); }, [init]);

  const loadSessions = useCallback(() => {
    if (!projectDir) return;
    fetchSessions(projectDir).then(r => setSessions(r.data)).catch(() => setErrorToast('Failed to load sessions'));
  }, [projectDir]);

  useEffect(() => { loadSessions(); }, [loadSessions]);

  const switchProject = (dir: string) => {
    setProjectDir(dir);
    setActiveSessionId(null);
    setMessages([]);
    fetchSessions(dir).then(r => setSessions(r.data));
  };

  const handleSend = () => {
    const text = input.trim();
    if (!text || loading) return;
    setInput('');
    setLoading(true);
    setShowCancel(true);
    const modelToUse = selectedModel || models[0]?.id;
    setMessages(prev => [...prev, { role: 'user', content: text }, { role: 'assistant', content: '', streaming: true }]);
    let fullContent = '';
    const controller = connectChat(text, activeSessionId, modelToUse, projectDir,
      (line) => { fullContent += line + '\n'; setMessages(prev => { const u = [...prev]; if (u.length > 0) u[u.length - 1] = { role: 'assistant', content: fullContent, streaming: true }; return u; }); },
      (err) => { setMessages(prev => [...prev, { role: 'error', content: err }]); setLoading(false); setShowCancel(false); },
      () => { setMessages(prev => { const u = [...prev]; if (u.length > 0) u[u.length - 1] = { role: 'assistant', content: fullContent, streaming: false }; return u; }); setLoading(false); setShowCancel(false); loadSessions(); }
    );
    abortRef.current = controller;
  };

  const handleCancel = () => {
    if (abortRef.current) { abortRef.current.abort(); fetch('/api/chat/cancel', { method: 'POST' }).catch(() => {}); }
    setMessages(prev => { const u = [...prev]; if (u.length > 0 && u[u.length - 1].streaming) u[u.length - 1] = { ...u[u.length - 1], content: u[u.length - 1].content + '\n[Cancelled]', streaming: false }; return u; });
    setLoading(false); setShowCancel(false);
  };

  const handleNewSession = () => { setActiveSessionId(null); setMessages([]); };
  const handleDeleteSession = async (id: string) => {
    try { await deleteSession(id, projectDir); loadSessions(); if (activeSessionId === id) { setActiveSessionId(null); setMessages([]); } } catch (e) { setErrorToast('Failed to delete session'); }
  };
  const handleKeyDown = (e: React.KeyboardEvent) => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); handleSend(); } };

  const copyMsg = (content: string) => navigator.clipboard.writeText(content);

  return (
    <div className="app-layout">
      <Sidebar sessions={sessions} activeSession={activeSessionId} onSelect={setActiveSessionId}
        onNew={handleNewSession} onDelete={handleDeleteSession} open={sidebarOpen} onClose={() => setSidebarOpen(false)} />
      <div className="main-panel">
        <div className="chat-header">
          <button className="menu-btn" onClick={() => setSidebarOpen(true)}>&#9776;</button>
          <h1 style={{ fontSize: 13, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
            {activeSessionId ? `Session ${activeSessionId.slice(0, 8)}` : 'New Session'}
          </h1>
          {models.length > 0 && (
            <select className="model-select" value={selectedModel} onChange={e => setSelectedModel(e.target.value)}>
              {models.map(m => <option key={m.id} value={m.id}>{m.name || m.id}</option>)}
            </select>
          )}
          <ProjectSwitcher projects={projects} currentDir={projectDir} onSwitch={switchProject} />
          <button className="icon-btn" onClick={() => setMessages([])} title="Clear chat" style={{ fontSize: 14 }}>&#128465;</button>
          <Link to="/settings" className="icon-btn" style={{ textDecoration: 'none' }} title="Settings">&#9881;</Link>
        </div>
        <div className="message-list">
          {messages.length === 0 ? (
            <div className="empty-state">
              <div style={{ fontSize: 40, marginBottom: 8 }}>&#9670;</div>
              <div>Claude Remote</div>
              <div style={{ fontSize: 12 }}>Type a message to start</div>
            </div>
          ) : messages.map((m, i) => (
            <div key={i} className={`message ${m.role} ${m.streaming ? 'streaming' : ''}`}>
              {m.role === 'assistant' && !m.streaming ? (
                <Markdown remarkPlugins={[remarkGfm]}>{m.content}</Markdown>
              ) : (
                m.content.split('\n').map((ln, j) => <span key={j}>{ln}<br /></span>)
              )}
              {!m.streaming && m.role === 'assistant' && m.content && (
                <div style={{ marginTop: 6 }}>
                  <button onClick={() => copyMsg(m.content)} className="icon-btn" style={{ fontSize: 11, padding: '2px 8px', background: '#0f3460', borderRadius: 4 }}>Copy</button>
                </div>
              )}
              {m.streaming && <div className="typing-indicator"><span /><span /><span /></div>}
            </div>
          ))}
          <div ref={bottomRef} />
        </div>
        <div className="input-area">
          <div className="input-wrapper">
            <textarea placeholder="Type a message..." value={input} onChange={e => setInput(e.target.value)} onKeyDown={handleKeyDown} rows={1} />
          </div>
          {showCancel ? (
            <button className="send-btn" onClick={handleCancel} style={{ background: '#666' }}>Cancel</button>
          ) : (
            <button className="send-btn" onClick={handleSend} disabled={!input.trim()}>Send</button>
          )}
        </div>
      </div>
      {errorToast && <Toast msg={errorToast} onClose={() => setErrorToast('')} />}
    </div>
  );
}