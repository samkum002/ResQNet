import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ShieldAlert, LogOut, Loader2, Activity } from 'lucide-react';
import { logout } from '../api/auth';
import './DashboardHeader.css';

export default function DashboardHeader({ user, roleTitle }) {
  const navigate = useNavigate();
  const [loggingOut, setLoggingOut] = useState(false);

  const handleLogout = async () => {
    if (loggingOut) return;
    setLoggingOut(true);
    try {
      await logout();
    } finally {
      navigate('/login', { replace: true });
    }
  };

  const roleLabel = roleTitle || (user?.roles?.[0] ? user.roles[0].replace(/_/g, ' ') : 'User');

  return (
    <header className="dash-header" role="banner">
      <div className="dash-brand">
        <div className="brand-glow-wrap">
          <ShieldAlert size={22} className="brand-icon" />
        </div>
        <span className="brand-title">ResQNet</span>
        <span className="brand-tag">Ops Center</span>
      </div>

      <div className="dash-center-status">
        <span className="radar-status-dot" aria-hidden="true" />
        <span className="radar-status-text">NETWORK SECURE</span>
      </div>

      <div className="dash-actions">
        <div className="dash-user-badge">
          <Activity size={14} className="user-online-icon" />
          <span className="dash-username">{user?.username || 'Authenticated User'}</span>
          <span className="dash-role-tag">{roleLabel}</span>
        </div>

        <button
          type="button"
          className="btn-logout"
          onClick={handleLogout}
          disabled={loggingOut}
          aria-label="Log out"
          aria-busy={loggingOut}
        >
          {loggingOut ? (
            <>
              <Loader2 size={15} className="spin" />
              <span>Logging out…</span>
            </>
          ) : (
            <>
              <LogOut size={15} />
              <span>Log Out</span>
            </>
          )}
        </button>
      </div>
    </header>
  );
}
