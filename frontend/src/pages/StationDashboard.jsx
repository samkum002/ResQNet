import { useState, useEffect, useRef, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  ShieldAlert, CheckCircle2, XCircle, AlertTriangle, Truck,
  Users, RefreshCw, Loader2, Award, Clock, ArrowRight,
  ShieldBan, Activity, CornerUpLeft, User, MessageSquare
} from 'lucide-react';
import DashboardHeader from '../components/DashboardHeader';
import { getCurrentUser } from '../api/auth';
import {
  fetchStationMissions, approveDispatch, rejectDispatch,
  fetchPendingRecalls, approveRecall, rejectRecall,
  confirmCompleteDispatch, confirmFakeDispatch
} from '../api/station';
import { createStompClient } from '../api/websocket';
import './StationDashboard.css';

export default function StationDashboard() {
  const navigate = useNavigate();
  const [user, setUser] = useState(null);
  const [authChecking, setAuthChecking] = useState(true);

  // Stats & Lists
  const [stats, setStats] = useState({
    totalAssigned: 0,
    completed: 0,
    markedFalse: 0,
    falseReports: 0,
  });
  const [dispatches, setDispatches] = useState([]);
  const [recalls, setRecalls] = useState([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [actionInProgress, setActionInProgress] = useState({});
  const [toastMessage, setToastMessage] = useState('');
  const [wsConnected, setWsConnected] = useState(false);

  const stompClientRef = useRef(null);

  // Load Station Data
  const loadData = useCallback(async (isSilent = false) => {
    if (!isSilent) setRefreshing(true);
    try {
      const [missionData, recallData] = await Promise.all([
        fetchStationMissions(),
        fetchPendingRecalls(),
      ]);

      if (missionData) {
        if (missionData.stats) setStats(missionData.stats);
        if (missionData.dispatches) setDispatches(missionData.dispatches);
      }
      if (recallData && Array.isArray(recallData)) {
        setRecalls(recallData);
      }
    } catch (err) {
      console.error('Failed to load station data', err);
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

  // STOMP WebSocket Connection (/user/update/mission)
  useEffect(() => {
    if (!user) return;

    const client = createStompClient({
      onConnect: () => {
        setWsConnected(true);
        // Station Manager receives updates on /user/update/mission and /update/mission
        const handleMissionUpdate = (msg) => {
          try {
            const data = JSON.parse(msg.body);
            loadData(true);
            setToastMessage('Station tasking updated in real time.');
            setTimeout(() => setToastMessage(''), 4000);
          } catch (e) {
            console.error('Error handling /update/mission update', e);
          }
        };

        client.subscribe('/user/update/mission', handleMissionUpdate);
        client.subscribe('/update/mission', handleMissionUpdate);
      },
      onDisconnect: () => setWsConnected(false),
      onError: (err) => console.warn('Station STOMP error:', err),
    });

    stompClientRef.current = client;

    return () => {
      if (stompClientRef.current) stompClientRef.current.deactivate();
    };
  }, [user, loadData]);

  // Dispatch Approvals & Rejections
  const handleApproveDispatch = async (dispatchId) => {
    setActionInProgress((prev) => ({ ...prev, [dispatchId]: 'approving' }));
    try {
      await approveDispatch(dispatchId);
      setToastMessage('Dispatch authorized. Field unit assigned.');
      setTimeout(() => setToastMessage(''), 4000);
      await loadData(true);
    } catch (err) {
      alert(`Approval error: ${err.message}`);
    } finally {
      setActionInProgress((prev) => ({ ...prev, [dispatchId]: null }));
    }
  };

  const handleRejectDispatch = async (dispatchId) => {
    if (!window.confirm('Reject this dispatch and re-route to next station?')) return;
    setActionInProgress((prev) => ({ ...prev, [dispatchId]: 'rejecting' }));
    try {
      await rejectDispatch(dispatchId);
      setToastMessage('Dispatch rejected. Re-routed to nearest active station.');
      setTimeout(() => setToastMessage(''), 4000);
      await loadData(true);
    } catch (err) {
      alert(`Rejection error: ${err.message}`);
    } finally {
      setActionInProgress((prev) => ({ ...prev, [dispatchId]: null }));
    }
  };

  // Recall Approvals & Rejections
  const handleApproveRecall = async (recallId) => {
    setActionInProgress((prev) => ({ ...prev, [`rec-${recallId}`]: 'approving' }));
    try {
      await approveRecall(recallId);
      setToastMessage('Recall approved. Force returned and resources restored.');
      setTimeout(() => setToastMessage(''), 4000);
      await loadData(true);
    } catch (err) {
      alert(`Recall approval error: ${err.message}`);
    } finally {
      setActionInProgress((prev) => ({ ...prev, [`rec-${recallId}`]: null }));
    }
  };

  const handleRejectRecall = async (recallId) => {
    setActionInProgress((prev) => ({ ...prev, [`rec-${recallId}`]: 'rejecting' }));
    try {
      await rejectRecall(recallId);
      setToastMessage('Recall rejected. Field unit ordered to continue mission.');
      setTimeout(() => setToastMessage(''), 4000);
      await loadData(true);
    } catch (err) {
      alert(`Recall rejection error: ${err.message}`);
    } finally {
      setActionInProgress((prev) => ({ ...prev, [`rec-${recallId}`]: null }));
    }
  };

  const handleConfirmComplete = async (dispatchId) => {
    setActionInProgress((prev) => ({ ...prev, [dispatchId]: 'confirming-complete' }));
    try {
      await confirmCompleteDispatch(dispatchId);
      setToastMessage('Mission completion confirmed. Station resources restored.');
      setTimeout(() => setToastMessage(''), 4000);
      await loadData(true);
    } catch (err) {
      alert(`Confirmation error: ${err.message}`);
    } finally {
      setActionInProgress((prev) => ({ ...prev, [dispatchId]: null }));
    }
  };

  const handleConfirmFake = async (dispatchId) => {
    if (!window.confirm('Confirm this mission was a false alarm?')) return;
    setActionInProgress((prev) => ({ ...prev, [dispatchId]: 'confirming-fake' }));
    try {
      await confirmFakeDispatch(dispatchId);
      setToastMessage('False alarm confirmed. Station resources restored.');
      setTimeout(() => setToastMessage(''), 4000);
      await loadData(true);
    } catch (err) {
      alert(`Confirmation error: ${err.message}`);
    } finally {
      setActionInProgress((prev) => ({ ...prev, [dispatchId]: null }));
    }
  };

  if (authChecking || loading) {
    return (
      <div className="dash-loading" aria-label="Loading Station Dashboard">
        <Loader2 size={32} className="spin text-cyan" />
      </div>
    );
  }

  return (
    <div className="station-dash-page">
      <DashboardHeader user={user} roleTitle="Station Commander" />

      <main className="station-dash-main">
        {/* Top Control Bar */}
        <div className="station-toolbar">
          <div>
            <h1 className="station-heading">Station Operations & Resource Command</h1>
            <p className="station-subheading">
              Manage unit readiness, authorize dispatches, and review force recall requests
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
              <span>Refresh Operations</span>
            </button>
          </div>
        </div>

        {/* Real Metrics Banner */}
        <section className="stats-grid" aria-label="Station Metrics">
          <div className="stat-card">
            <div className="stat-icon-wrap bg-cyan-dim">
              <Truck size={22} className="text-cyan" />
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

        {/* Pending Force Recall Requests Section */}
        {recalls.length > 0 && (
          <section className="recall-section" aria-label="Pending Recall Requests">
            <div className="section-header">
              <div className="recall-header-tag">
                <CornerUpLeft size={18} className="text-orange" />
                <h2>Pending Force Recall Requests ({recalls.length})</h2>
              </div>
              <span className="badge-urgent">ACTION REQUIRED</span>
            </div>

            <div className="recalls-grid">
              {recalls.map((rec) => {
                const recWorking = actionInProgress[`rec-${rec.recallId}`];
                const friendlyDispId = rec.dispatchId ? `DISP-${rec.dispatchId.slice(-6).toUpperCase()}` : 'UNIT';

                return (
                  <div key={rec.recallId} className="recall-card">
                    <div className="recall-card-header">
                      <div className="recall-unit-info">
                        <span className="recall-badge">{rec.forceType?.replace(/_/g, ' ') || 'FORCE'}</span>
                        <span className="recall-disp-id">{friendlyDispId}</span>
                      </div>
                      <span className="recall-timestamp">
                        {rec.requestedAt ? new Date(rec.requestedAt).toLocaleTimeString() : 'Recent'}
                      </span>
                    </div>

                    <div className="recall-card-body">
                      <div className="worker-info-row">
                        <User size={14} className="text-cyan" />
                        <span>Requested by worker: <strong>{rec.requestingWorkerUsername || 'Field Agent'}</strong></span>
                      </div>

                      <div className="reason-bubble">
                        <MessageSquare size={13} className="text-muted" />
                        <p>{rec.reason || 'Assistance no longer required at disaster coordinates.'}</p>
                      </div>
                    </div>

                    <div className="recall-card-actions">
                      <button
                        type="button"
                        className="btn-recall-approve"
                        onClick={() => handleApproveRecall(rec.recallId)}
                        disabled={!!recWorking}
                        aria-busy={recWorking === 'approving'}
                      >
                        {recWorking === 'approving' ? <Loader2 size={15} className="spin" /> : <CheckCircle2 size={15} />}
                        <span>Approve Recall</span>
                      </button>

                      <button
                        type="button"
                        className="btn-recall-reject"
                        onClick={() => handleRejectRecall(rec.recallId)}
                        disabled={!!recWorking}
                        aria-busy={recWorking === 'rejecting'}
                      >
                        {recWorking === 'rejecting' ? <Loader2 size={15} className="spin" /> : <XCircle size={15} />}
                        <span>Reject</span>
                      </button>
                    </div>
                  </div>
                );
              })}
            </div>
          </section>
        )}

        {/* Station Missions Queue */}
        <section className="missions-section">
          <div className="section-header">
            <h2>Station Dispatch Missions ({dispatches.length})</h2>
            <div className="queue-status">
              <span className={`status-radar-dot ${wsConnected ? 'active' : ''}`} />
              <span>{wsConnected ? 'LIVE MISSION SYNC' : 'OFFLINE'}</span>
            </div>
          </div>

          {dispatches.length === 0 ? (
            <div className="empty-queue-box">
              <Award size={48} className="empty-icon" />
              <h3>Station Fleet On Standby</h3>
              <p>No active missions are pending approval for this station.</p>
            </div>
          ) : (
            <div className="missions-grid">
              {dispatches.map((disp) => {
                const id = disp.dispatchId;
                const isWorking = actionInProgress[id];
                const friendlyId = id ? `DISP-${id.slice(-6).toUpperCase()}` : 'DISPATCH';
                const isBackup = disp.status === 'BACKUP_REQUEST';
                const isPendingApproval = disp.status === 'VERIFIED' || isBackup;
                const isCompletedByWorker = disp.status === 'DISPATCH_COMPLETED' || disp.status === 'BACKUP_DISPATCH_COMPLETED';
                const isFalseAlarmByWorker = disp.status === 'DISPATCH_FALSE';

                let statusBadgeClass = 'status-active';
                if (isPendingApproval) statusBadgeClass = 'status-pending';
                else if (isCompletedByWorker) statusBadgeClass = 'status-completed';
                else if (isFalseAlarmByWorker) statusBadgeClass = 'status-rejected';

                return (
                  <article key={id} className="mission-card">
                    <div className="mission-card-top">
                      <div className="mission-top-left">
                        <span className="mission-id">{friendlyId}</span>
                        <span className="mission-force-badge">
                          {disp.forceType?.replace(/_/g, ' ') || 'RESCUE FORCE'}
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
                      <span className="status-label">Operational Status:</span>
                      <span className={`status-pill ${statusBadgeClass}`}>
                        {disp.status || 'READY'}
                      </span>
                    </div>

                    {isPendingApproval ? (
                      <div className="mission-actions">
                        <button
                          type="button"
                          className="btn-approve"
                          onClick={() => handleApproveDispatch(id)}
                          disabled={!!isWorking}
                          aria-busy={isWorking === 'approving'}
                        >
                          {isWorking === 'approving' ? (
                            <Loader2 size={16} className="spin" />
                          ) : (
                            <CheckCircle2 size={16} />
                          )}
                          <span>{isBackup ? 'Authorize Backup' : 'Authorize Unit'}</span>
                        </button>

                        <button
                          type="button"
                          className="btn-reject"
                          onClick={() => handleRejectDispatch(id)}
                          disabled={!!isWorking}
                          aria-busy={isWorking === 'rejecting'}
                        >
                          {isWorking === 'rejecting' ? (
                            <Loader2 size={16} className="spin" />
                          ) : (
                            <XCircle size={16} />
                          )}
                          <span>Decline</span>
                        </button>
                      </div>
                    ) : isCompletedByWorker ? (
                      <div className="mission-actions mission-actions-single">
                        <button
                          type="button"
                          className="btn-approve"
                          onClick={() => handleConfirmComplete(id)}
                          disabled={!!isWorking}
                          aria-busy={isWorking === 'confirming-complete'}
                        >
                          {isWorking === 'confirming-complete' ? (
                            <Loader2 size={16} className="spin" />
                          ) : (
                            <CheckCircle2 size={16} />
                          )}
                          <span>Confirm Completion</span>
                        </button>
                      </div>
                    ) : isFalseAlarmByWorker ? (
                      <div className="mission-actions mission-actions-single">
                        <button
                          type="button"
                          className="btn-reject"
                          onClick={() => handleConfirmFake(id)}
                          disabled={!!isWorking}
                          aria-busy={isWorking === 'confirming-fake'}
                        >
                          {isWorking === 'confirming-fake' ? (
                            <Loader2 size={16} className="spin" />
                          ) : (
                            <AlertTriangle size={16} />
                          )}
                          <span>Confirm False Alarm</span>
                        </button>
                      </div>
                    ) : (
                      <div className="mission-active-badge">
                        <Activity size={15} className="text-cyan" />
                        <span>Unit in field operations</span>
                      </div>
                    )}
                  </article>
                );
              })}
            </div>
          )}
        </section>
      </main>
    </div>
  );
}
