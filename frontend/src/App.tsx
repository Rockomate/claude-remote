import { useState, useEffect, useRef, useCallback } from 'react';
import { Link } from 'react-router-dom';
import Markdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { fetchSessions, deleteSession, fetchModels, fetchConfig, connectChat, fetchProjects, fetchSessionMessages } from './api/client';
import type { Session, Model, ProjectInfo, ChatMessageItem } from './api/client';

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

function Sidebar({ sessions, activeSession, search, onSearchChange, onSelect, onNew, onDelete, open, onClose }: {
  sessions: Session[]; activeSession: string | null; search: string; onSearchChange: (v: string) => void;
  onSelect: (id: string) => void; onNew: () => void; onDelete: (id: string) => void;
  open: boolean; onClose: () => void;
}) {
  const [showCount, setShowCount] = useState(50);
  const filtered = search ? sessions.filter(s => s.name.toLowerCase().includes(search.toLowerCase()) || s.preview?.toLowerCase().includes(search.toLowerCase())) : sessions;
  const displaySessions = filtered.slice(0, showCount);
  return (<>
    <div className={`sidebar-overlay ${open ? 'open' : ''}`} onClick={onClose} />
    <div className={`sidebar ${open ? 'mobile-open' : ''}`}>
      <div className="sidebar-header">
        <h2>Sessions ({filtered.length})</h2>
        <button className="close-btn" onClick={onClose}>&times;</button>
      </div>
      <div style={{ padding: '8px' }}>
        <input placeholder="Search sessions..." value={search} onChange={e => onSearchChange(e.target.value)}
          style={{ width: '100%', padding: '8px 10px', borderRadius: 8, border: '1px solid #2a2a4a', background: '#1a1a2e', color: '#e0e0e0', fontSize: 13, outline: 'none' }} />
      </div>
      <button className="new-session-btn" onClick={() => { onNew(); onClose(); }}>+ New Session</button>
      <div className="sidebar-sessions">
        {displaySessions.length === 0 && <div style={{ color: '#666', textAlign: 'center', padding: 24, fontSize: 13 }}>{search ? 'No matching sessions' : 'No sessions'}</div>}
        {displaySessions.map(s => (
          <div key={s.id} className={`session-item ${s.id === activeSession ? 'active' : ''}`}
            onClick={() => { onSelect(s.id); onClose(); }}
            onContextMenu={(e) => { e.preventDefault(); if (confirm('Delete this session?')) onDelete(s.id); }}>
            <div className="session-name">{s.name}</div>
            <div className="session-meta">{s.messageCount} msgs &middot; {s.updatedAt?.slice(0, 10)}</div>
            {s.preview && <div className="session-preview">{s.preview}</div>}
            <button onClick={(e) => { e.stopPropagation(); if (confirm('Delete this session?')) onDelete(s.id); }} style={{ position: 'absolute', right: 8, top: 8, background: 'none', border: 'none', color: '#666', cursor: 'pointer', fontSize: 14, padding: '2px 6px', borderRadius: 4 }} title="Delete">&#128465;</button>
          </div>
        ))}
        {filtered.length > showCount && (
          <button onClick={() => setShowCount(prev => prev + 50)} style={{ width: '100%', padding: '10px', background: 'transparent', border: '1px dashed #2a2a4a', color: '#a0a0b0', borderRadius: 8, cursor: 'pointer', marginTop: 4, fontSize: 12 }}>
            Show more ({filtered.length - showCount} remaining)
          </button>
        )}
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

function ErrorBoundary({ children }: { children: React.ReactNode }) {
  const [error, setError] = useState<string | null>(null);
  useEffect(() => {
    const handler = (event: ErrorEvent) => setError(event.message);
    window.addEventListener('error', handler);
    return () => window.removeEventListener('error', handler);
  }, []);
  if (error) {
    return (
      <div style={{ padding: 32, textAlign: 'center', color: '#ff6b6b' }}>
        <h3>Something went wrong</h3>
        <p style={{ fontSize: 13, color: '#a0a0b0' }}>{error}</p>
        <button onClick={() => { setError(null); window.location.reload(); }}
          className="send-btn" style={{ marginTop: 16 }}>Reload</button>
      </div>
    );
  }
  return <>{children}</>;
}

function LoadingSpinner() {
  return (
    <div style={{ display: 'flex', justifyContent: 'center', padding: 32 }}>
      <div className="typing-indicator"><span /><span /><span /></div>
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
  const [searchQuery, setSearchQuery] = useState('');
  const [loadingHistory, setLoadingHistory] = useState(false);
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
    if (messages.length > 0 && !confirm('Switch project? Current chat will be cleared.')) return;
    setProjectDir(dir);
    setActiveSessionId(null);
    setMessages([]);
    setSearchQuery('');
    fetchSessions(dir).then(r => setSessions(r.data));
  };

  const handleSelectSession = async (id: string) => {
    setActiveSessionId(id);
    setLoadingHistory(true);
    try {
      const res = await fetchSessionMessages(id, projectDir);
      const chatMessages: Message[] = [];
      for (const m of res.data) {
        chatMessages.push({ role: m.role as 'user' | 'assistant', content: m.content });
      }
      setMessages(chatMessages);
    } catch {
      setErrorToast('Failed to load session history');
    }
    setLoadingHistory(false);
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
      (err) => {
        // Enhanced error handling with retry option
        const isNetworkError = err.includes('fetch') || err.includes('network') || err.includes('Failed');
        const errorContent = isNetworkError
          ? err + '\n\n[Network issue - check Tailscale connection and try again]'
          : err;
        setMessages(prev => [...prev, { role: 'error', content: errorContent }]);
        setLoading(false); setShowCancel(false);
      },
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

  // Network health indicator
  const [networkOk, setNetworkOk] = useState(true);
  useEffect(() => {
    const check = () => fetch('/api/config').then(() => setNetworkOk(true)).catch(() => setNetworkOk(false));
    check();
    const interval = setInterval(check, 30000); // Check every 30s
    return () => clearInterval(interval);
  }, []);

  const handleRetry = () => {
    // Re-send the last user message
    const lastUserMsg = [...messages].reverse().find((m: Message) => m.role === 'user');
    if (lastUserMsg && !loading) {
      setInput(lastUserMsg.content);
    }
  };

  // Keyboard shortcuts
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      // Ctrl+Enter or Cmd+Enter to send
      if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') {
        e.preventDefault();
        handleSend();
      }
      // Escape to cancel or close sidebar
      if (e.key === 'Escape') {
        if (sidebarOpen) setSidebarOpen(false);
        else if (loading) handleCancel();
      }
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [loading, sidebarOpen, handleSend, handleCancel]);

  // Auto-grow textarea
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  useEffect(() => {
    const ta = textareaRef.current;
    if (ta) { ta.style.height = 'auto'; ta.style.height = Math.min(ta.scrollHeight, 120) + 'px'; }
  }, [input]);

  return (
    <div className="app-layout">
      <Sidebar sessions={sessions} activeSession={activeSessionId} search={searchQuery} onSearchChange={setSearchQuery}
        onSelect={handleSelectSession} onNew={handleNewSession} onDelete={handleDeleteSession} open={sidebarOpen} onClose={() => setSidebarOpen(false)} />
      <div className="main-panel">
        <div className="chat-header">
          <button className="menu-btn" onClick={() => setSidebarOpen(true)}>&#9776;</button>
          <h1 style={{ fontSize: 13, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
            {activeSessionId ? `Session ${activeSessionId.slice(0, 8)}` : 'New Session'}
            {!networkOk && <span style={{ color: '#ff6b6b', marginLeft: 8, fontSize: 11 }}>[Offline]</span>}
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
          {loadingHistory ? (
            <div style={{ textAlign: 'center', padding: 32, color: '#666' }}>Loading history...</div>
          ) : messages.length === 0 ? (
            <div className="empty-state">
              <div style={{ fontSize: 40, marginBottom: 8 }}>&#9670;</div>
              <div>Claude Remote</div>
              <div style={{ fontSize: 12 }}>Type a message or select a session</div>
            </div>
          ) : messages.map((m, i) =>
              <div key={i} className={`message ${m.role} ${m.streaming ? 'streaming' : ''}`}>
                {m.role === 'assistant' && !m.streaming ? (
                  <Markdown remarkPlugins={[remarkGfm]}>{m.content}</Markdown>
                ) : (
                  m.content.split('\n').map((ln, j) => <span key={j}>{ln}<br /></span>)
                )}
                {!m.streaming && m.role === 'assistant' && m.content && (
                  <div style={{ marginTop: 6, display: 'flex', gap: 6 }}>
                    <CopyButton content={m.content} label="Copy MD" />
                    <CopyButton content={m.content.replace(/\*\*(.+?)\*\*/g, '$1').replace(/```[\s\S]*?```/g, '').replace(/`([^`]+)`/g, '$1')} label="Copy Text" />
                  </div>
                )}
                {m.streaming && <div className="typing-indicator"><span /><span /><span /></div>}
              </div>
            )}
          <div ref={bottomRef} />
        </div>
        <div className="input-area">
          <div className="input-wrapper">
            <textarea ref={textareaRef} placeholder="Type a message..." value={input} onChange={e => setInput(e.target.value)} onKeyDown={handleKeyDown} rows={1} style={{ maxHeight: 120 }} />
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

function CopyButton({ content, label }: { content: string; label: string }) {
  const [copied, setCopied] = useState(false);
  return (
    <button onClick={() => { navigator.clipboard.writeText(content); setCopied(true); setTimeout(() => setCopied(false), 1500); }}
      className="icon-btn" style={{ fontSize: 11, padding: '2px 8px', background: '#0f3460', borderRadius: 4 }}>
      {copied ? 'Copied!' : label}
    </button>
  );
}