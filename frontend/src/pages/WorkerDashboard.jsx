import { useState, useEffect, useRef, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  ShieldAlert, Play, CheckCircle2, AlertTriangle, Truck,
  Users, RefreshCw, Loader2, Award, CornerUpLeft, Plus,
  ShieldBan, Activity, X, Flag, MessageSquare, Radio
} from 'lucide-react';
import DashboardHeader from '../components/DashboardHeader';
import { getCurrentUser } from '../api/auth';
import {
  fetchWorkerDispatches, startDispatch, requestBackup,
  requestRecall, fetchActiveRecalls, completeDispatch,
  markFalseDispatch
} from '../api/worker';
import { createStompClient } from '../api/websocket';
import './WorkerDashboard.css';

export default function WorkerDashboard() {
  const navigate = useNavigate();
  const [user, setUser] = useState(null);
  const [authChecking, setAuthChecking] = useState(true);

  // Missions & Recalls
  const [dispatches, setDispatches] = useState([]);
  const [arrivedDispatchIds, setArrivedDispatchIds] = useState(new Set());
  const [backupMissions, setBackupMissions] = useState([]);
  const [activeRecalls, setActiveRecalls] = useState([]);
  const [recallDecisionNotice, setRecallDecisionNotice] = useState(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [actionInProgress, setActionInProgress] = useState({});
  const [toastMessage, setToastMessage] = useState('');
  const [wsConnected, setWsConnected] = useState(false);

  // Modals State
  const [backupModalDispatch, setBackupModalDispatch] = useState(null);
  const [backupForm, setBackupForm] = useState({ reqVehicles: 1, reqPersonnel: 4 });
  const [backupSubmitting, setBackupSubmitting] = useState(false);

  const [recallModalDispatch, setRecallModalDispatch] = useState(null);
  const [recallReason, setRecallReason] = useState('');
  const [recallSubmitting, setRecallSubmitting] = useState(false);

  const stompClientRef = useRef(null);

  // Load Worker Data
  const loadData = useCallback(async (isSilent = false) => {
    if (!isSilent) setRefreshing(true);
    try {
      const [dispatchData, recallData] = await Promise.all([
        fetchWorkerDispatches(),
        fetchActiveRecalls(),
      ]);

      if (dispatchData) {
        if (dispatchData.dispatch && Array.isArray(dispatchData.dispatch)) {
          setDispatches(dispatchData.dispatch);
          const alreadyArrived = dispatchData.dispatch
            .filter((d) => d.arrived)
            .map((d) => d.dispatchId);
          if (alreadyArrived.length > 0) {
            setArrivedDispatchIds((prev) => new Set([...prev, ...alreadyArrived]));
          }
        }
        if (dispatchData.backup) setBackupMissions(dispatchData.backup);
      }
      if (recallData && Array.isArray(recallData)) {
        setActiveRecalls(recallData);
      }
    } catch (err) {
      console.error('Failed to load worker dispatches', err);
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

  // STOMP WebSocket Connection (/user/new/mission)
  useEffect(() => {
    if (!user) return;

    const client = createStompClient({
      onConnect: () => {
        setWsConnected(true);
        // Field worker receives recall decisions and mission updates at /user/new/mission
        client.subscribe('/user/new/mission', (msg) => {
          try {
            const data = JSON.parse(msg.body);
            // If it's a recall decision (APPROVED or REJECTED)
            if (data && (data.status === 'APPROVED' || data.status === 'REJECTED')) {
              setRecallDecisionNotice(data);
            }
            loadData(true);
            setToastMessage('Mission alert updated from command center.');
            setTimeout(() => setToastMessage(''), 4000);
          } catch (e) {
            console.error('Error handling /new/mission update', e);
          }
        });

        // Field worker receives simulation arrival updates on /user/new/location
        client.subscribe('/user/new/location', (msg) => {
          try {
            const loc = JSON.parse(msg.body);
            if (loc && loc.dispatchId && loc.arrived) {
              setArrivedDispatchIds((prev) => new Set([...prev, loc.dispatchId]));
              setToastMessage('Unit arrived at incident scene. Field operations unlocked.');
              setTimeout(() => setToastMessage(''), 4000);
            }
          } catch (e) {
            console.error('Error handling /new/location message', e);
          }
        });
      },
      onDisconnect: () => setWsConnected(false),
      onError: (err) => console.warn('Worker STOMP error:', err),
    });

    stompClientRef.current = client;

    return () => {
      if (stompClientRef.current) stompClientRef.current.deactivate();
    };
  }, [user, loadData]);

  // Mission Actions
  const handleStart = async (dispatchId) => {
    setActionInProgress((prev) => ({ ...prev, [dispatchId]: 'starting' }));
    try {
      await startDispatch(dispatchId);
      setToastMessage('Unit deployed. Route simulation running.');
      setTimeout(() => setToastMessage(''), 4000);
      await loadData(true);
    } catch (err) {
      alert(`Start mission error: ${err.message}`);
    } finally {
      setActionInProgress((prev) => ({ ...prev, [dispatchId]: null }));
    }
  };

  const handleComplete = async (dispatchId) => {
    setActionInProgress((prev) => ({ ...prev, [dispatchId]: 'completing' }));
    try {
      await completeDispatch(dispatchId);
      setToastMessage('Mission reported as completed. Field cleared.');
      setTimeout(() => setToastMessage(''), 4000);
      await loadData(true);
    } catch (err) {
      alert(`Complete mission error: ${err.message}`);
    } finally {
      setActionInProgress((prev) => ({ ...prev, [dispatchId]: null }));
    }
  };

  const handleMarkFalse = async (dispatchId) => {
    if (!window.confirm('Mark this incident location as a false alarm?')) return;
    setActionInProgress((prev) => ({ ...prev, [dispatchId]: 'falsing' }));
    try {
      await markFalseDispatch(dispatchId);
      setToastMessage('Marked as false alarm.');
      setTimeout(() => setToastMessage(''), 4000);
      await loadData(true);
    } catch (err) {
      alert(`False alarm report error: ${err.message}`);
    } finally {
      setActionInProgress((prev) => ({ ...prev, [dispatchId]: null }));
    }
  };

  // Submit Backup Request
  const handleSubmitBackup = async (e) => {
    e.preventDefault();
    if (!backupModalDispatch) return;

    setBackupSubmitting(true);
    try {
      await requestBackup(backupModalDispatch.dispatchId, backupForm);
      setToastMessage('Backup request sent to nearby stations.');
      setTimeout(() => setToastMessage(''), 4000);
      setBackupModalDispatch(null);
      await loadData(true);
    } catch (err) {
      alert(`Backup request error: ${err.message}`);
    } finally {
      setBackupSubmitting(false);
    }
  };

  // Submit Recall Request
  const handleSubmitRecall = async (e) => {
    e.preventDefault();
    if (!recallModalDispatch) return;

    setRecallSubmitting(true);
    try {
      await requestRecall(recallModalDispatch.dispatchId, recallReason);
      setToastMessage('Recall request routed to your station commander.');
      setTimeout(() => setToastMessage(''), 4000);
      setRecallModalDispatch(null);
      setRecallReason('');
      await loadData(true);
    } catch (err) {
      alert(`Recall request error: ${err.message}`);
    } finally {
      setRecallSubmitting(false);
    }
  };

  if (authChecking || loading) {
    return (
      <div className="dash-loading" aria-label="Loading Worker Dashboard">
        <Loader2 size={32} className="spin text-cyan" />
      </div>
    );
  }

  return (
    <div className="worker-dash-page">
      <DashboardHeader user={user} roleTitle="Field Response Worker" />

      <main className="worker-dash-main">
        {/* Top Control Bar */}
        <div className="worker-toolbar">
          <div>
            <h1 className="worker-heading">Tactical Response Terminal</h1>
            <p className="worker-subheading">
              Active dispatch operations, simulation controls, and station communication
            </p>
          </div>

          <div className="toolbar-actions">
            <button
              type="button"
              className="btn-refresh"
              onClick={() => loadData(false)}
              disabled={refreshing}
            >
              <RefreshCw size={15} className={refreshing ? 'spin' : ''} />
              <span>Sync Missions</span>
            </button>
          </div>
        </div>

        {/* Dismissible Recall Decision Banner */}
        {recallDecisionNotice && (
          <div
            className={`decision-banner ${
              recallDecisionNotice.status === 'APPROVED' ? 'decision-approved' : 'decision-rejected'
            } animate-slide-in`}
            role="alert"
          >
            <div className="decision-content">
              {recallDecisionNotice.status === 'APPROVED' ? (
                <CheckCircle2 size={22} className="text-green" />
              ) : (
                <AlertTriangle size={22} className="text-orange" />
              )}
              <div className="decision-text">
                <h4>
                  Station Commander Recall Decision:{' '}
                  <strong>{recallDecisionNotice.status}</strong>
                </h4>
                <p>
                  Recall request for unit {recallDecisionNotice.forceType?.replace(/_/g, ' ') || 'FORCE'} (
                  {recallDecisionNotice.dispatchId ? `DISP-${recallDecisionNotice.dispatchId.slice(-6).toUpperCase()}` : ''})
                  was {recallDecisionNotice.status.toLowerCase()} by station leadership.
                  {recallDecisionNotice.status === 'APPROVED'
                    ? ' Return unit to station; resources have been restored.'
                    : ' Continue standard mission protocol.'}
                </p>
              </div>
            </div>

            <button
              type="button"
              className="btn-decision-ok"
              onClick={() => setRecallDecisionNotice(null)}
              aria-label="Acknowledge and dismiss recall notice"
            >
              OK, Dismiss
            </button>
          </div>
        )}

        {/* Toast Notification */}
        {toastMessage && (
          <div className="admin-toast animate-slide-in" role="status">
            <Activity size={16} className="text-cyan" />
            <span>{toastMessage}</span>
          </div>
        )}

        {/* Assigned Dispatches Section */}
        <section className="missions-section">
          <div className="section-header">
            <h2>Assigned Field Missions ({dispatches.length})</h2>
            <div className="queue-status">
              <span className={`status-radar-dot ${wsConnected ? 'active' : ''}`} />
              <span>{wsConnected ? 'LIVE DISPATCH LINK' : 'OFFLINE'}</span>
            </div>
          </div>

          {dispatches.length === 0 ? (
            <div className="empty-queue-box">
              <Award size={48} className="empty-icon" />
              <h3>No Active Missions Assigned</h3>
              <p>You have no active emergency deployments assigned at this time.</p>
            </div>
          ) : (
            <div className="worker-missions-grid">
              {dispatches.map((disp) => {
                const id = disp.dispatchId;
                const isWorking = actionInProgress[id];
                const friendlyId = id ? `DISP-${id.slice(-6).toUpperCase()}` : 'MISSION';
                const isBackupRequested = disp.status === 'BACKUP_REQUEST';
                const isDispatched = disp.status === 'DISPATCHED' || disp.status === 'BACKUP_DISPATCHED' || isBackupRequested;
                const hasArrived = Boolean(disp.arrived || arrivedDispatchIds.has(id));
                const isRecalled = disp.status === 'RECALLED';
                const isCompleted = disp.status === 'DISPATCH_COMPLETED' || disp.status === 'BACKUP_DISPATCH_COMPLETED';

                // Check if this dispatch has a pending recall
                const pendingRecall = activeRecalls.find(
                  (r) => r.dispatchId === id && r.status === 'PENDING'
                );

                return (
                  <article key={id} className="worker-mission-card">
                    <div className="mission-card-top">
                      <div className="mission-top-left">
                        <span className="mission-id">{friendlyId}</span>
                        <span className="mission-force-badge">
                          {disp.forceType?.replace(/_/g, ' ') || 'FORCE'}
                        </span>
                      </div>
                      <span className={`badge-severity sev-${(disp.severity || 'medium').toLowerCase()}`}>
                        {disp.severity || 'MEDIUM'}
                      </span>
                    </div>

                    <div className="mission-resource-row">
                      <div className="resource-item">
                        <Truck size={16} className="text-cyan" />
                        <span><strong>{disp.assignedVehicle ?? 1}</strong> Vehicles</span>
                      </div>
                      <div className="resource-item">
                        <Users size={16} className="text-cyan" />
                        <span><strong>{disp.assignedPersonnel ?? 4}</strong> Personnel</span>
                      </div>
                    </div>

                    <div className="mission-status-row">
                      <span className="status-label">Mission Phase:</span>
                      <span className={`status-pill status-${(disp.status || 'READY').toLowerCase()}`}>
                        {disp.status || 'READY'}
                      </span>
                    </div>

                    {/* Pending Recall Alert Tag */}
                    {pendingRecall && (
                      <div className="pending-recall-alert">
                        <CornerUpLeft size={14} className="text-orange" />
                        <span>Recall request pending commander review</span>
                      </div>
                    )}

                    {/* Pending Backup Alert Tag */}
                    {isBackupRequested && (
                      <div className="pending-recall-alert" style={{ borderColor: 'rgba(255, 75, 31, 0.4)', background: 'rgba(255, 75, 31, 0.08)' }}>
                        <Plus size={14} className="text-orange" />
                        <span>Backup requested • Reinforcements pending station approval</span>
                      </div>
                    )}

                    {/* Mission Operational Controls */}
                    {!isRecalled && !isCompleted && (
                      <div className="worker-actions-panel">
                        {/* Start Mission Button (Before Start) or Simulation Running Indicator (After Start) */}
                        {!isDispatched ? (
                          <button
                            type="button"
                            className="btn-start-mission"
                            onClick={() => handleStart(id)}
                            disabled={!!isWorking}
                            aria-busy={isWorking === 'starting'}
                          >
                            {isWorking === 'starting' ? (
                              <Loader2 size={16} className="spin" />
                            ) : (
                              <Play size={16} />
                            )}
                            <span>Start Mission (Simulate Route)</span>
                          </button>
                        ) : !hasArrived ? (
                          <div className="mission-active-indicator">
                            <Activity size={14} className="spin text-cyan" />
                            <span>Unit En Route • Actions Unlock Upon Arrival</span>
                          </div>
                        ) : (
                          <div className="mission-active-indicator" style={{ borderColor: 'rgba(16, 185, 129, 0.4)', background: 'rgba(16, 185, 129, 0.08)', color: '#10b981' }}>
                            <CheckCircle2 size={14} className="text-green" />
                            <span>Unit On Scene • Tactical Controls Unlocked</span>
                          </div>
                        )}

                        {/* Mid-Mission Actions: Visible always, enabled ONLY once vehicle has arrived at incident */}
                        <div className="in-progress-actions">
                          <div className="action-row-split">
                            <button
                              type="button"
                              className="btn-worker-complete"
                              onClick={() => handleComplete(id)}
                              disabled={!isDispatched || !hasArrived || !!isWorking}
                              title={!isDispatched ? 'Mission must be started before completing' : !hasArrived ? 'Unit is en route; actions unlock upon arrival' : 'Complete mission'}
                            >
                              <CheckCircle2 size={15} />
                              <span>Complete</span>
                            </button>

                            <button
                              type="button"
                              className="btn-worker-false"
                              onClick={() => handleMarkFalse(id)}
                              disabled={!isDispatched || !hasArrived || !!isWorking}
                              title={!isDispatched ? 'Mission must be started before reporting false alarm' : !hasArrived ? 'Unit is en route; actions unlock upon arrival' : 'Report false alarm'}
                            >
                              <Flag size={15} />
                              <span>False Alarm</span>
                            </button>
                          </div>

                          <div className="action-row-split">
                            <button
                              type="button"
                              className="btn-worker-backup"
                              onClick={() => setBackupModalDispatch(disp)}
                              disabled={!isDispatched || !hasArrived || !!isWorking || isBackupRequested}
                              title={!isDispatched ? 'Mission must be started before requesting backup' : !hasArrived ? 'Unit is en route; actions unlock upon arrival' : isBackupRequested ? 'Backup already requested for this mission' : 'Request reinforcement backup'}
                            >
                              <Plus size={15} />
                              <span>{isBackupRequested ? 'Backup Requested' : 'Request Backup'}</span>
                            </button>

                            <button
                              type="button"
                              className="btn-worker-recall"
                              onClick={() => setRecallModalDispatch(disp)}
                              disabled={!isDispatched || !hasArrived || !!isWorking || !!pendingRecall}
                              title={!isDispatched ? 'Mission must be started before requesting recall' : !hasArrived ? 'Unit is en route; actions unlock upon arrival' : pendingRecall ? 'Recall request pending commander review' : 'Request recall to station'}
                            >
                              <CornerUpLeft size={15} />
                              <span>{pendingRecall ? 'Recall Pending' : 'Request Recall'}</span>
                            </button>
                          </div>
                        </div>
                      </div>
                    )}
                  </article>
                );
              })}
            </div>
          )}
        </section>

        {/* Backup Missions Section */}
        {backupMissions && backupMissions.length > 0 && (
          <section className="backup-missions-section">
            <div className="section-header">
              <h2>Reinforcement & Backup Deployments ({backupMissions.length})</h2>
            </div>
            <div className="backup-grid">
              {backupMissions.map((b, idx) => (
                <div key={b.backupId || idx} className="backup-card">
                  <div className="backup-card-header">
                    <span className="backup-tag">BACKUP UNIT</span>
                    <span className="status-pill status-active">{b.status || 'PENDING'}</span>
                  </div>
                  <div className="backup-body">
                    <span>Vehicles: <strong>{b.reqVehicles || 1}</strong></span>
                    <span>Personnel: <strong>{b.reqPersonnel || 4}</strong></span>
                  </div>
                </div>
              ))}
            </div>
          </section>
        )}
      </main>

      {/* ── Modal: Request Backup ── */}
      {backupModalDispatch && (
        <div className="modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="backup-title">
          <div className="modal-card">
            <div className="modal-header">
              <h3 id="backup-title">Request Reinforcements (Backup)</h3>
              <button
                type="button"
                className="btn-modal-close"
                onClick={() => setBackupModalDispatch(null)}
                aria-label="Close modal"
              >
                <X size={16} />
              </button>
            </div>

            <form onSubmit={handleSubmitBackup} className="modal-form">
              <p className="modal-desc">
                Specify reinforcements required for dispatch #{backupModalDispatch.dispatchId?.slice(-6).toUpperCase()}.
                ResQNet will route to the nearest available stations.
              </p>

              <div className="form-group">
                <label htmlFor="reqVehicles">Required Emergency Vehicles</label>
                <input
                  id="reqVehicles"
                  type="number"
                  min="1"
                  max="10"
                  value={backupForm.reqVehicles}
                  onChange={(e) => setBackupForm({ ...backupForm, reqVehicles: parseInt(e.target.value) || 1 })}
                  required
                />
              </div>

              <div className="form-group">
                <label htmlFor="reqPersonnel">Required Personnel</label>
                <input
                  id="reqPersonnel"
                  type="number"
                  min="1"
                  max="30"
                  value={backupForm.reqPersonnel}
                  onChange={(e) => setBackupForm({ ...backupForm, reqPersonnel: parseInt(e.target.value) || 1 })}
                  required
                />
              </div>

              <div className="modal-footer">
                <button
                  type="button"
                  className="btn-modal-cancel"
                  onClick={() => setBackupModalDispatch(null)}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="btn-modal-submit"
                  disabled={backupSubmitting}
                >
                  {backupSubmitting ? <Loader2 size={16} className="spin" /> : <span>Transmit Backup Request</span>}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* ── Modal: Request Force Recall ── */}
      {recallModalDispatch && (
        <div className="modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="recall-title">
          <div className="modal-card">
            <div className="modal-header">
              <h3 id="recall-title">Request Force Recall</h3>
              <button
                type="button"
                className="btn-modal-close"
                onClick={() => setRecallModalDispatch(null)}
                aria-label="Close modal"
              >
                <X size={16} />
              </button>
            </div>

            <form onSubmit={handleSubmitRecall} className="modal-form">
              <p className="modal-desc">
                Submit a recall request for force{' '}
                <strong>{recallModalDispatch.forceType?.replace(/_/g, ' ') || 'FORCE'}</strong>.
                This notification will be transmitted directly to your Station Commander for decision.
              </p>

              <div className="form-group">
                <label htmlFor="recallReason">Reason for Recall</label>
                <textarea
                  id="recallReason"
                  rows="3"
                  value={recallReason}
                  onChange={(e) => setRecallReason(e.target.value)}
                  placeholder="e.g. Fire contained by local brigade; specialized equipment not needed."
                  required
                />
              </div>

              <div className="modal-footer">
                <button
                  type="button"
                  className="btn-modal-cancel"
                  onClick={() => setRecallModalDispatch(null)}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="btn-modal-submit btn-submit-recall"
                  disabled={recallSubmitting || !recallReason.trim()}
                >
                  {recallSubmitting ? <Loader2 size={16} className="spin" /> : <span>Submit Recall Request</span>}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
