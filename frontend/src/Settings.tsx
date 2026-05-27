import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { fetchConfig, updateConfig, fetchProjects, fetchModels } from './api/client';
import type { AppConfig, ProjectInfo, Model } from './api/client';

export default function Settings() {
  const navigate = useNavigate();
  const [config, setConfig] = useState<AppConfig | null>(null);
  const [projects, setProjects] = useState<ProjectInfo[]>([]);
  const [proxyModels, setProxyModels] = useState<Model[]>([]);
  const [workDir, setWorkDir] = useState('');
  const [proxyUrl, setProxyUrl] = useState('');
  const [authToken, setAuthToken] = useState('');
  const [saving, setSaving] = useState(false);
  const [msg, setMsg] = useState('');

  useEffect(() => {
    fetchConfig().then(r => {
      setConfig(r.data);
      setWorkDir(r.data.defaultProjectDir);
      setProxyUrl(r.data.proxyBaseUrl || '');
    });
    fetchProjects().then(r => setProjects(r.data));
    fetchModels().then(r => setProxyModels(r.data));
  }, []);

  const handleSave = async () => {
    setSaving(true);
    setMsg('');
    try {
      const data: Record<string, unknown> = {};
      if (workDir !== config?.defaultProjectDir) data.defaultProjectDir = workDir;
      if (proxyUrl !== config?.proxyBaseUrl) data.proxyBaseUrl = proxyUrl;
      if (authToken) data.authToken = authToken;
      const r = await updateConfig(data);
      setMsg('Saved to ' + r.data.path);
      // Update local config so main page picks it up on next load
      if (config) {
        setConfig({ ...config, defaultProjectDir: workDir, proxyBaseUrl: proxyUrl });
      }
    } catch (e) {
      setMsg('Failed to save: ' + (e as Error).message);
    }
    setSaving(false);
  };

  const selectProject = (p: ProjectInfo) => {
    setWorkDir(p.path);
  };

  return (
    <div className="app-layout">
      <div className="main-panel">
        <div className="chat-header">
          <h1>Settings</h1>
          <button className="icon-btn" onClick={() => navigate('/')} style={{ fontSize: 13, padding: '6px 12px' }}>
            &larr; Back
          </button>
        </div>

        <div style={{ flex: 1, overflow: 'auto', padding: 16 }}>
          {/* Working Directory */}
          <div className="config-field" style={{ marginBottom: 20 }}>
            <label>Working Directory</label>
            <input
              value={workDir}
              onChange={e => setWorkDir(e.target.value)}
              placeholder="C:\Users\MR\Desktop\your-project"
              style={{ background: '#1a1a2e', border: '1px solid #2a2a4a', color: '#e0e0e0', padding: 10, borderRadius: 8, fontSize: 14, width: '100%' }}
            />
          </div>

          {/* Project Quick Select */}
          <div className="config-field" style={{ marginBottom: 20 }}>
            <label>Quick Select Project</label>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginTop: 6 }}>
              {projects.map(p => (
                <span
                  key={p.dirName}
                  onClick={() => selectProject(p)}
                  style={{
                    background: workDir === p.path ? '#e94560' : '#0f3460',
                    padding: '6px 12px', borderRadius: 16, fontSize: 12,
                    color: '#e0e0e0', cursor: 'pointer'
                  }}
                >
                  {p.name} ({p.sessionCount})
                </span>
              ))}
            </div>
          </div>

          {/* Proxy URL */}
          <div className="config-field" style={{ marginBottom: 20 }}>
            <label>Proxy API URL</label>
            <input
              value={proxyUrl}
              onChange={e => setProxyUrl(e.target.value)}
              placeholder="http://127.0.0.1:15721/claude-desktop"
              style={{ background: '#1a1a2e', border: '1px solid #2a2a4a', color: '#e0e0e0', padding: 10, borderRadius: 8, fontSize: 14, width: '100%' }}
            />
          </div>

          {/* Auth Token */}
          <div className="config-field" style={{ marginBottom: 20 }}>
            <label>Auth Token (optional, for API security)</label>
            <input
              value={authToken}
              onChange={e => setAuthToken(e.target.value)}
              type="password"
              placeholder="Leave empty to disable"
              style={{ background: '#1a1a2e', border: '1px solid #2a2a4a', color: '#e0e0e0', padding: 10, borderRadius: 8, fontSize: 14, width: '100%' }}
            />
          </div>

          {/* Available Models */}
          <div className="config-field" style={{ marginBottom: 20 }}>
            <label>Available Models (from proxy API)</label>
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginTop: 6 }}>
              {proxyModels.map(m => (
                <span key={m.id} style={{
                  background: '#0f3460', padding: '6px 12px', borderRadius: 16,
                  fontSize: 12, color: '#a0a0b0'
                }}>
                  {m.name || m.id}
                </span>
              ))}
              {proxyModels.length === 0 && (
                <span style={{ color: '#666', fontSize: 13 }}>Run backend to auto-detect models</span>
              )}
            </div>
          </div>

          {/* Save */}
          <button
            onClick={handleSave}
            disabled={saving}
            className="send-btn"
            style={{ width: '100%', padding: 12 }}
          >
            {saving ? 'Saving...' : 'Save Settings'}
          </button>

          {msg && (
            <div style={{ marginTop: 12, padding: 10, borderRadius: 8, fontSize: 13,
              background: msg.includes('Failed') ? '#3a1a1a' : '#1a3a1a',
              color: msg.includes('Failed') ? '#ff6b6b' : '#4caf50' }}>
              {msg}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}