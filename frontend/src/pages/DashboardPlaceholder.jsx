import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ShieldAlert, LogOut, Loader2, Activity } from 'lucide-react';
import { getCurrentUser, logout } from '../api/auth';
import './DashboardPlaceholder.css';

export default function DashboardPlaceholder({ role }) {
  const navigate = useNavigate();
  const [user, setUser] = useState(null);
  const [checking, setChecking] = useState(true);
  const [loggingOut, setLoggingOut] = useState(false);

  useEffect(() => {
    getCurrentUser().then((u) => {
      if (!u) {
        navigate('/login', { replace: true });
      } else {
        setUser(u);
      }
      setChecking(false);
    });
  }, [navigate]);

  const handleLogout = async () => {
    setLoggingOut(true);
    try {
      await logout();
    } finally {
      navigate('/login', { replace: true });
    }
  };

  if (checking) {
    return (
      <div className="dash-loading" aria-label="Loading dashboard">
        <Loader2 size={28} className="spin" />
      </div>
    );
  }

  const displayRole = user?.roles?.[0]
    ? user.roles[0].replace(/_/g, ' ')
    : role || 'User';

  return (
    <div className="dash-page">
      <header className="dash-header">
        <div className="dash-brand">
          <ShieldAlert size={22} />
          <span>ResQNet Command</span>
        </div>
        <div className="dash-user-info">
          <Activity size={14} className="dash-online" />
          <span className="dash-username">{user?.username}</span>
          <span className="dash-role-badge">{displayRole}</span>
        </div>
        <button
          className="btn-logout"
          onClick={handleLogout}
          disabled={loggingOut}
          aria-busy={loggingOut}
          aria-label="Log out"
        >
          {loggingOut ? (
            <Loader2 size={16} className="spin" />
          ) : (
            <LogOut size={16} />
          )}
          <span>{loggingOut ? 'Logging out…' : 'Log Out'}</span>
        </button>
      </header>

      <main className="dash-main">
        <div className="dash-coming-soon">
          <div className="dash-icon-wrap">
            <ShieldAlert size={56} />
          </div>
          <h1>{displayRole} Dashboard</h1>
          <p>
            Authentication successful. The <strong>{displayRole}</strong> operational module
            is initialized and awaiting Phase 2 dispatch activation.
          </p>
          <div className="dash-meta">
            <span>Signed in as <strong>{user?.username}</strong></span>
            <span className="dash-dot" aria-hidden="true">·</span>
            <span>User ID: {user?.userId ? String(user.userId) : 'Assigned'}</span>
          </div>
        </div>
      </main>
    </div>
  );
}
