import { useState, useEffect, useRef, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  ShieldAlert, CheckCircle2, XCircle, AlertTriangle, Activity,
  Clock, MapPin, Eye, RefreshCw, Loader2, Layers, Cpu,
  Award, ShieldBan, AlertOctagon, Edit3, X, BarChart3
} from 'lucide-react';
import DashboardHeader from '../components/DashboardHeader';
import { getCurrentUser } from '../api/auth';
import { fetchAdminDisasters, approveDisaster, rejectDisaster, updateDisaster } from '../api/admin';
import { createStompClient } from '../api/websocket';
import './AdminDashboard.css';

const ALL_DISASTER_TYPES = [
  'FIRE',
  'LANDSLIDE',
  'BUILDING_COLLAPSE',
  'EARTHQUAKE',
  'ROAD_ACCIDENT',
  'ELECTRICAL_HAZARD',
];

const ALL_FORCES = [
  'AMBULANCE',
  'SDRF',
  'NDRF',
  'FIRE_DEPARTMENT',
  'POLICE_STATION',
  'ELECTRICITY_DEPARTMENT',
];

export default function AdminDashboard() {
  const navigate = useNavigate();
  const [user, setUser] = useState(null);
  const [authChecking, setAuthChecking] = useState(true);

  // Dashboard Data
  const [stats, setStats] = useState({
    totalAssigned: 0,
    completed: 0,
    markedFalse: 0,
    falseReports: 0,
  });
  const [disasters, setDisasters] = useState([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [actionInProgress, setActionInProgress] = useState({});
  const [toastMessage, setToastMessage] = useState('');
  const [wsConnected, setWsConnected] = useState(false);
  const [editModalData, setEditModalData] = useState(null);
  const [editSaving, setEditSaving] = useState(false);

  const stompClientRef = useRef(null);

  // Load Dashboard Data
  const loadData = useCallback(async (isSilent = false) => {
    if (!isSilent) setRefreshing(true);
    try {
      const data = await fetchAdminDisasters();
      if (data) {
        if (data.stats) setStats(data.stats);
        if (data.disasters) setDisasters(data.disasters);
      }
    } catch (err) {
      console.error('Failed to load admin disasters', err);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, []);

  // Auth Verification
  useEffect(() => {
    getCurrentUser().then((u) => {
      if (!u) {
        navigate('/login', { replace: true });
      } else {
        setUser(u);
        loadData();
      }
      setAuthChecking(false);
    });
  }, [navigate, loadData]);

  // STOMP WebSocket Connection (/user/topic/disaster)
  useEffect(() => {
    if (!user) return;

    const client = createStompClient({
      onConnect: () => {
        setWsConnected(true);
        const handleDisasterEvent = (msg) => {
          try {
            const data = JSON.parse(msg.body);
            loadData(true);
            if (data && data.forces && (data.status === 'DISPATCH_FAILED' || data.status === 'BACKUP_FAILED' || (Array.isArray(data.forces) ? data.forces.length === 1 : !Array.isArray(data.forces)))) {
              const forceRaw = Array.isArray(data.forces) ? data.forces[0] : (data.forces?.length ? Array.from(data.forces)[0] : data.forces);
              const forceName = forceRaw ? String(forceRaw).replace(/_/g, ' ') : 'Required force';
              const dispId = data.disasterId ? `Incident #${String(data.disasterId).slice(-6).toUpperCase()}` : 'Incident';
              setToastMessage(`No available station found for ${forceName} (${dispId}). Dispatch flagged.`);
            } else {
              setToastMessage('New incident stream incoming from field reporters.');
            }
            setTimeout(() => setToastMessage(''), 5000);
          } catch (e) {
            console.error('Error handling /topic/disaster event', e);
          }
        };

        client.subscribe('/user/topic/disaster', handleDisasterEvent);
        client.subscribe('/topic/disaster', handleDisasterEvent);
      },
      onDisconnect: () => setWsConnected(false),
      onError: (err) => console.warn('Admin STOMP error:', err),
    });

    stompClientRef.current = client;

    return () => {
      if (stompClientRef.current) stompClientRef.current.deactivate();
    };
  }, [user, loadData]);

  // Actions
  const handleApprove = async (disasterId) => {
    setActionInProgress((prev) => ({ ...prev, [disasterId]: 'approving' }));
    try {
      await approveDisaster(disasterId);
      setToastMessage('Incident approved. Forces dispatched to stations.');
      setTimeout(() => setToastMessage(''), 4000);
      await loadData(true);
    } catch (err) {
      alert(`Approval error: ${err.message}`);
    } finally {
      setActionInProgress((prev) => ({ ...prev, [disasterId]: null }));
    }
  };

  const handleReject = async (disasterId) => {
    if (!window.confirm('Are you sure you want to reject this incident as a false alarm?')) return;
    setActionInProgress((prev) => ({ ...prev, [disasterId]: 'rejecting' }));
    try {
      await rejectDisaster(disasterId);
      setToastMessage('Incident marked as false alarm and discarded.');
      setTimeout(() => setToastMessage(''), 4000);
      await loadData(true);
    } catch (err) {
      alert(`Rejection error: ${err.message}`);
    } finally {
      setActionInProgress((prev) => ({ ...prev, [disasterId]: null }));
    }
  };

  const openEditModal = (item) => {
    let types = [];
    if (Array.isArray(item.disasterType)) {
      types = item.disasterType.map(String);
    } else if (item.disasterType) {
      types = [String(item.disasterType)];
    }

    let initialForces = [];
    if (Array.isArray(item.forces)) {
      initialForces = item.forces.map(String);
    }

    setEditModalData({
      disasterId: item.disasterId,
      userReport: item.userReport || '',
      disasterType: types,
      severity: item.severity || 'MEDIUM',
      suspicious: !!item.suspicious,
      forces: initialForces,
    });
  };

  const toggleForce = (forceName) => {
    setEditModalData((prev) => {
      const exists = prev.forces.includes(forceName);
      return {
        ...prev,
        forces: exists
          ? prev.forces.filter((f) => f !== forceName)
          : [...prev.forces, forceName],
      };
    });
  };

  const toggleDisasterType = (typeName) => {
    setEditModalData((prev) => {
      const exists = prev.disasterType.includes(typeName);
      return {
        ...prev,
        disasterType: exists
          ? prev.disasterType.filter((t) => t !== typeName)
          : [...prev.disasterType, typeName],
      };
    });
  };

  const handleSaveEdit = async (e) => {
    e.preventDefault();
    if (!editModalData) return;
    setEditSaving(true);
    try {
      const payload = {
        severity: editModalData.severity,
        forces: editModalData.forces,
        disasterType: editModalData.disasterType,
        suspicious: Boolean(editModalData.suspicious),
        userReport: editModalData.userReport.trim(),
      };
      const updated = await updateDisaster(editModalData.disasterId, payload);
      setDisasters((prev) =>
        prev.map((d) => (d.disasterId === editModalData.disasterId ? { ...d, ...updated } : d))
      );
      setToastMessage('Incident details updated successfully.');
      setTimeout(() => setToastMessage(''), 4000);
      setEditModalData(null);
    } catch (err) {
      alert(`Update failed: ${err.message}`);
    } finally {
      setEditSaving(false);
    }
  };

  if (authChecking || loading) {
    return (
      <div className="dash-loading" aria-label="Loading Admin Dashboard">
        <Loader2 size={32} className="spin text-cyan" />
      </div>
    );
  }

  return (
    <div className="admin-dash-page">
      <DashboardHeader user={user} roleTitle="System Administrator" />

      <main className="admin-dash-main">
        {/* Top Control Bar */}
        <div className="admin-toolbar">
          <div>
            <h1 className="admin-heading">Incident Verification Command</h1>
            <p className="admin-subheading">Review AI-analyzed emergency signals and dispatch response forces</p>
          </div>

          <div className="toolbar-actions">
            <button
              type="button"
              className="btn-refresh"
              onClick={() => loadData(false)}
              disabled={refreshing}
            >
              <RefreshCw size={15} className={refreshing ? 'spin' : ''} />
              <span>Refresh Feed</span>
            </button>
          </div>
        </div>

        {/* Real Metrics Banner */}
        <section className="stats-grid" aria-label="Incident Statistics">
          <div className="stat-card">
            <div className="stat-icon-wrap bg-cyan-dim">
              <Layers size={22} className="text-cyan" />
            </div>
            <div className="stat-info">
              <span className="stat-label">Total Assigned</span>
              <span className="stat-value">{stats.totalAssigned ?? 0}</span>
            </div>
          </div>

          <div className="stat-card">
            <div className="stat-icon-wrap bg-green-dim">
              <CheckCircle2 size={22} className="text-green" />
            </div>
            <div className="stat-info">
              <span className="stat-label">Completed</span>
              <span className="stat-value">{stats.completed ?? 0}</span>
            </div>
          </div>

          <div className="stat-card">
            <div className="stat-icon-wrap bg-yellow-dim">
              <AlertTriangle size={22} className="text-yellow" />
            </div>
            <div className="stat-info">
              <span className="stat-label">Marked False</span>
              <span className="stat-value">{stats.markedFalse ?? 0}</span>
            </div>
          </div>

          <div className="stat-card">
            <div className="stat-icon-wrap bg-red-dim">
              <ShieldBan size={22} className="text-red" />
            </div>
            <div className="stat-info">
              <span className="stat-label">False Reports</span>
              <span className="stat-value">{stats.falseReports ?? 0}</span>
            </div>
          </div>
        </section>

        {/* Toast Notification */}
        {toastMessage && (
          <div className="admin-toast animate-slide-in" role="status">
            <Activity size={16} className="text-cyan" />
            <span>{toastMessage}</span>
          </div>
        )}

        {/* Disasters Review List */}
        <section className="disasters-section">
          <div className="section-header">
            <h2>Pending Incident Queue ({disasters.length})</h2>
            <div className="queue-status">
              <span className={`status-radar-dot ${wsConnected ? 'active' : ''}`} />
              <span>{wsConnected ? 'LIVE DISPATCH STREAM' : 'STATIC CACHE'}</span>
            </div>
          </div>

          {disasters.length === 0 ? (
            <div className="empty-queue-box">
              <Award size={48} className="empty-icon" />
              <h3>All Emergency Reports Handled</h3>
              <p>No unverified disaster reports are pending in your jurisdiction.</p>
            </div>
          ) : (
            <div className="disasters-card-grid">
              {disasters.map((item) => {
                const id = item.disasterId;
                const isWorking = actionInProgress[id];
                const friendlyId = id ? `INC-${id.slice(-6).toUpperCase()}` : 'INCIDENT';

                return (
                  <article key={id} className="disaster-review-card">
                    {/* Incident Visual Imagery */}
                    <div className="card-image-wrapper">
                      {item.image ? (
                        <img
                          src={`/fetch/image/${item.image}`}
                          alt={`Incident imagery for ${item.userReport || 'Disaster'}`}
                          className="disaster-img"
                          loading="lazy"
                        />
                      ) : (
                        <div className="no-image-placeholder">
                          <Eye size={32} />
                          <span>No imagery available</span>
                        </div>
                      )}

                      <span className={`badge-severity sev-${(item.severity || 'medium').toLowerCase()}`}>
                        {item.severity || 'MEDIUM'}
                      </span>
                    </div>

                    {/* Incident Details */}
                    <div className="card-content">
                      <div className="card-top-row">
                        <span className="incident-id">{friendlyId}</span>
                        {item.suspicious && (
                          <span className="badge-suspicious">
                            <AlertOctagon size={12} />
                            <span>SUSPICIOUS</span>
                          </span>
                        )}
                      </div>

                      <h3 className="incident-title">
                        {item.userReport || (Array.isArray(item.disasterType) ? item.disasterType.join(', ') : item.disasterType) || 'Unclassified Event'}
                      </h3>

                      {/* AI & Signal Metrics Grid */}
                      <div className="incident-metrics-grid">
                        <div className="metric-box">
                          <span className="metric-box-label">AI CONF</span>
                          <span className="metric-box-val">
                            {item.aiConfidence != null ? `${Math.round(item.aiConfidence * (item.aiConfidence <= 1 ? 100 : 1))}%` : 'N/A'}
                          </span>
                        </div>
                        <div className="metric-box">
                          <span className="metric-box-label">FINAL CONF</span>
                          <span className="metric-box-val">
                            {item.finalConfidence != null ? `${Math.round(item.finalConfidence * (item.finalConfidence <= 1 ? 100 : 1))}%` : 'N/A'}
                          </span>
                        </div>
                        <div className="metric-box">
                          <span className="metric-box-label">REPORTS</span>
                          <span className="metric-box-val">{item.reportCount ?? 1}</span>
                        </div>
                        <div className="metric-box">
                          <span className="metric-box-label">KEYWORD</span>
                          <span
                            className="metric-box-val keyword-val"
                            title={Array.isArray(item.disasterType) ? item.disasterType.join(', ') : (item.disasterType || 'Unassigned')}
                          >
                            {Array.isArray(item.disasterType) ? (item.disasterType[0] || 'Unassigned') : (item.disasterType || 'Unassigned')}
                          </span>
                        </div>
                      </div>

                      {/* AI & Signal Metadata */}
                      <div className="metadata-pills">
                        {item.forces && item.forces.length > 0 && (
                          <div className="force-chips">
                            {item.forces.map((f) => (
                              <span key={f} className="force-chip">
                                {f.replace(/_/g, ' ')}
                              </span>
                            ))}
                          </div>
                        )}
                      </div>

                      {/* Action Buttons */}
                      <div className="card-actions">
                        <button
                          type="button"
                          className="btn-edit"
                          onClick={() => openEditModal(item)}
                          disabled={!!isWorking}
                          title="Edit incident report details"
                        >
                          <Edit3 size={15} />
                          <span>Edit</span>
                        </button>

                        <button
                          type="button"
                          className="btn-approve"
                          onClick={() => handleApprove(id)}
                          disabled={!!isWorking}
                          aria-busy={isWorking === 'approving'}
                        >
                          {isWorking === 'approving' ? (
                            <Loader2 size={16} className="spin" />
                          ) : (
                            <CheckCircle2 size={16} />
                          )}
                          <span>Approve & Dispatch</span>
                        </button>

                        <button
                          type="button"
                          className="btn-reject"
                          onClick={() => handleReject(id)}
                          disabled={!!isWorking}
                          aria-busy={isWorking === 'rejecting'}
                        >
                          {isWorking === 'rejecting' ? (
                            <Loader2 size={16} className="spin" />
                          ) : (
                            <XCircle size={16} />
                          )}
                          <span>Reject</span>
                        </button>
                      </div>
                    </div>
                  </article>
                );
              })}
            </div>
          )}
        </section>
      </main>

      {/* Edit Incident Details Modal */}
      {editModalData && (
        <div className="modal-backdrop" onClick={() => setEditModalData(null)}>
          <div className="edit-modal-card" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <div className="modal-title-row">
                <Edit3 size={18} className="text-cyan" />
                <h3>Edit Incident Details</h3>
              </div>
              <button
                type="button"
                className="btn-close-modal"
                onClick={() => setEditModalData(null)}
              >
                <X size={18} />
              </button>
            </div>

            <form onSubmit={handleSaveEdit} className="edit-modal-form">
              {/* 1. userReport */}
              <div className="form-group">
                <label className="form-label">Report Narrative / User Report</label>
                <textarea
                  rows={3}
                  value={editModalData.userReport}
                  onChange={(e) =>
                    setEditModalData({ ...editModalData, userReport: e.target.value })
                  }
                  required
                  className="admin-modal-input"
                  placeholder="Incident summary and details..."
                />
              </div>

              <div className="form-row">
                {/* 2. severity */}
                <div className="form-group">
                  <label className="form-label">Severity Level</label>
                  <select
                    value={editModalData.severity}
                    onChange={(e) =>
                      setEditModalData({ ...editModalData, severity: e.target.value })
                    }
                    className="admin-modal-input"
                  >
                    <option value="LOW">LOW</option>
                    <option value="MEDIUM">MEDIUM</option>
                    <option value="HIGH">HIGH</option>
                    <option value="CRITICAL">CRITICAL</option>
                  </select>
                </div>

                {/* 3. suspicious */}
                <div className="form-group">
                  <label className="form-label">Classification Flag</label>
                  <label className="suspicious-toggle-label">
                    <input
                      type="checkbox"
                      checked={editModalData.suspicious}
                      onChange={(e) =>
                        setEditModalData({ ...editModalData, suspicious: e.target.checked })
                      }
                      className="admin-modal-checkbox"
                    />
                    <span className={editModalData.suspicious ? "text-red-highlight" : "text-subtle"}>
                      <AlertOctagon size={14} />
                      <span>{editModalData.suspicious ? "Flagged Suspicious" : "Mark Suspicious"}</span>
                    </span>
                  </label>
                </div>
              </div>

              {/* 4. disasterType */}
              <div className="form-group">
                <label className="form-label">Disaster Type / Category ({editModalData.disasterType.length} selected)</label>
                <div className="toggle-chip-group">
                  {ALL_DISASTER_TYPES.map((type) => {
                    const isSelected = editModalData.disasterType.includes(type);
                    return (
                      <button
                        type="button"
                        key={type}
                        className={`chip-toggle ${isSelected ? 'chip-active-type' : ''}`}
                        onClick={() => toggleDisasterType(type)}
                      >
                        {type.replace(/_/g, ' ')}
                      </button>
                    );
                  })}
                </div>
              </div>

              {/* 5. forces */}
              <div className="form-group">
                <label className="form-label">Assigned Emergency Forces ({editModalData.forces.length} selected)</label>
                <div className="toggle-chip-group">
                  {ALL_FORCES.map((force) => {
                    const isSelected = editModalData.forces.includes(force);
                    return (
                      <button
                        type="button"
                        key={force}
                        className={`chip-toggle ${isSelected ? 'chip-active-force' : ''}`}
                        onClick={() => toggleForce(force)}
                      >
                        {force.replace(/_/g, ' ')}
                      </button>
                    );
                  })}
                </div>
              </div>

              <div className="modal-actions">
                <button
                  type="button"
                  className="btn-modal-cancel"
                  onClick={() => setEditModalData(null)}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="btn-modal-save"
                  disabled={editSaving}
                >
                  {editSaving ? <Loader2 size={16} className="spin" /> : <CheckCircle2 size={16} />}
                  <span>{editSaving ? 'Saving...' : 'Save Changes'}</span>
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
