import { useState, useEffect, useCallback } from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import {
  ShieldAlert,
  User,
  Lock,
  Eye,
  EyeOff,
  Loader2,
  Radio,
  AlertTriangle,
  MapPin,
} from 'lucide-react';
import { login, getCurrentUser, getDashboardRoute, initializeCsrf } from '../api/auth';
import './LoginPage.css';

/* ── Topographic Background Pattern ─────────────────────────── */
function TopoBackground() {
  return (
    <svg
      className="topo-svg"
      aria-hidden="true"
      viewBox="0 0 800 600"
      preserveAspectRatio="xMidYMid slice"
    >
      <defs>
        <radialGradient id="rg1" cx="25%" cy="65%">
          <stop offset="0%" stopColor="#00d4ff" stopOpacity="0.09" />
          <stop offset="100%" stopColor="transparent" stopOpacity="0" />
        </radialGradient>
        <radialGradient id="rg2" cx="75%" cy="25%">
          <stop offset="0%" stopColor="#ff4b1f" stopOpacity="0.08" />
          <stop offset="100%" stopColor="transparent" stopOpacity="0" />
        </radialGradient>
      </defs>
      <rect width="800" height="600" fill="url(#rg1)" />
      <rect width="800" height="600" fill="url(#rg2)" />

      {/* Topographic Rings (Left) */}
      {[40, 80, 120, 160, 200, 240].map((r, i) => (
        <ellipse
          key={`l${i}`}
          cx="200"
          cy="380"
          rx={r * 1.3}
          ry={r}
          fill="none"
          stroke="#00d4ff"
          strokeWidth="0.6"
          strokeOpacity={0.08 - i * 0.01}
        />
      ))}

      {/* Topographic Rings (Right) */}
      {[30, 65, 100, 140, 175, 210].map((r, i) => (
        <ellipse
          key={`r${i}`}
          cx="640"
          cy="180"
          rx={r}
          ry={r * 0.8}
          fill="none"
          stroke="#00d4ff"
          strokeWidth="0.6"
          strokeOpacity={0.07 - i * 0.009}
        />
      ))}

      {/* Grid Overlay */}
      {Array.from({ length: 10 }, (_, i) => (
        <line
          key={`hg${i}`}
          x1="0"
          y1={i * 70}
          x2="800"
          y2={i * 70}
          stroke="#0099cc"
          strokeWidth="0.3"
          strokeOpacity="0.08"
        />
      ))}
      {Array.from({ length: 14 }, (_, i) => (
        <line
          key={`vg${i}`}
          x1={i * 65}
          y1="0"
          x2={i * 65}
          y2="600"
          stroke="#0099cc"
          strokeWidth="0.3"
          strokeOpacity="0.08"
        />
      ))}
    </svg>
  );
}

function RadarRing({ size, delay }) {
  return (
    <div
      className="radar-ring"
      style={{ width: size, height: size, animationDelay: `${delay}s` }}
    />
  );
}

function PingMarker({ x, y, delay }) {
  return (
    <div
      className="ping-marker"
      style={{ left: `${x}%`, top: `${y}%`, animationDelay: `${delay}s` }}
      aria-hidden="true"
    >
      <MapPin size={14} />
      <span className="ping-pulse" />
    </div>
  );
}

export default function LoginPage() {
  const navigate = useNavigate();
  const location = useLocation();

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [checkingSession, setCheckingSession] = useState(true);
  const [fieldErrors, setFieldErrors] = useState({});
  const [errorMessage, setErrorMessage] = useState('');
  const [touched, setTouched] = useState({});

  // Check URL params for error=true from Spring Security form login failure redirect
  useEffect(() => {
    const params = new URLSearchParams(location.search);
    if (params.get('error') === 'true') {
      setErrorMessage('Invalid username or password.');
    }
  }, [location.search]);

  // Check for active session on load
  useEffect(() => {
    initializeCsrf().then(() => {
      getCurrentUser().then((user) => {
        if (user && user.roles) {
          navigate(getDashboardRoute(user.roles), { replace: true });
        }
        setCheckingSession(false);
      });
    });
  }, [navigate]);

  const validate = useCallback(() => {
    const errors = {};
    if (!username.trim()) {
      errors.username = 'Username is required';
    }
    if (!password) {
      errors.password = 'Password is required';
    }
    return errors;
  }, [username, password]);

  const handleBlur = (field) => {
    setTouched((prev) => ({ ...prev, [field]: true }));
    setFieldErrors(validate());
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    const errors = validate();
    if (Object.keys(errors).length > 0) {
      setFieldErrors(errors);
      setTouched({ username: true, password: true });
      return;
    }

    setLoading(true);
    setErrorMessage('');

    try {
      const result = await login(username, password);
      const targetRoute = result.redirect || getDashboardRoute(result.roles || []);
      navigate(targetRoute, { replace: true });
    } catch (err) {
      setErrorMessage(err.message || 'Authentication failed. Please verify credentials.');
    } finally {
      setLoading(false);
    }
  };

  if (checkingSession) {
    return (
      <div className="auth-checking" aria-label="Validating session">
        <Loader2 size={32} className="spin" />
      </div>
    );
  }

  const canSubmit = !loading && username.trim() && password;

  return (
    <div className="login-page">
      <TopoBackground />
      <div className="bg-overlay" aria-hidden="true" />

      {/* Decorative Radar Element */}
      <div className="radar-container" aria-hidden="true">
        <RadarRing size={160} delay={0} />
        <RadarRing size={280} delay={0.6} />
        <RadarRing size={400} delay={1.2} />
        <div className="radar-sweep" />
        <div className="radar-dot" />
      </div>

      {/* Operational Markers */}
      <PingMarker x={18} y={24} delay={0} />
      <PingMarker x={75} y={68} delay={1.4} />
      <PingMarker x={60} y={16} delay={2.8} />

      {/* Command Status Header */}
      <header className="status-bar" role="banner">
        <div className="status-indicator">
          <span className="status-dot" aria-hidden="true" />
          <span>NETWORK ONLINE</span>
        </div>
        <div className="status-right">
          <Radio size={13} />
          <span>RESQNET COMMAND v2.0</span>
        </div>
      </header>

      {/* Main Workspace */}
      <main className="login-main">
        {/* Brand Banner */}
        <div className="brand-section">
          <div className="brand-icon-wrap" aria-hidden="true">
            <ShieldAlert size={52} />
            <div className="brand-icon-ring" />
          </div>
          <h1 className="brand-name">ResQNet</h1>
          <p className="brand-tagline">Emergency Response System</p>
          <div className="brand-divider" aria-hidden="true" />
          <p className="brand-sub">Coordinated dispatch and rapid force deployment.</p>
        </div>

        {/* Login Card */}
        <section className="login-card" aria-label="Sign in to command network">
          <div className="card-header">
            <h2>Command Access</h2>
            <p className="card-subtitle">Authenticate with your personnel credentials</p>
          </div>

          {errorMessage && (
            <div className="alert alert-error" role="alert" aria-live="assertive">
              <AlertTriangle size={16} aria-hidden="true" />
              <span>{errorMessage}</span>
            </div>
          )}

          <form onSubmit={handleSubmit} noValidate aria-label="Login form">
            {/* Username Input */}
            <div
              className={`field-group ${
                touched.username && fieldErrors.username ? 'field-error' : ''
              }`}
            >
              <label htmlFor="username">Username</label>
              <div className="input-wrapper">
                <User size={16} className="input-icon" aria-hidden="true" />
                <input
                  id="username"
                  name="username"
                  type="text"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  onBlur={() => handleBlur('username')}
                  placeholder="Enter username"
                  autoComplete="username"
                  required
                  aria-required="true"
                  aria-invalid={!!(touched.username && fieldErrors.username)}
                  aria-describedby={
                    touched.username && fieldErrors.username ? 'username-error' : undefined
                  }
                  disabled={loading}
                />
              </div>
              {touched.username && fieldErrors.username && (
                <span className="field-error-msg" id="username-error" role="alert">
                  {fieldErrors.username}
                </span>
              )}
            </div>

            {/* Password Input */}
            <div
              className={`field-group ${
                touched.password && fieldErrors.password ? 'field-error' : ''
              }`}
            >
              <label htmlFor="password">Password</label>
              <div className="input-wrapper">
                <Lock size={16} className="input-icon" aria-hidden="true" />
                <input
                  id="password"
                  name="password"
                  type={showPassword ? 'text' : 'password'}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  onBlur={() => handleBlur('password')}
                  placeholder="Enter password"
                  autoComplete="current-password"
                  required
                  aria-required="true"
                  aria-invalid={!!(touched.password && fieldErrors.password)}
                  aria-describedby={
                    touched.password && fieldErrors.password ? 'password-error' : undefined
                  }
                  disabled={loading}
                />
                <button
                  type="button"
                  className="pw-toggle"
                  onClick={() => setShowPassword((prev) => !prev)}
                  aria-label={showPassword ? 'Hide password' : 'Show password'}
                  disabled={loading}
                >
                  {showPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                </button>
              </div>
              {touched.password && fieldErrors.password && (
                <span className="field-error-msg" id="password-error" role="alert">
                  {fieldErrors.password}
                </span>
              )}
            </div>

            <button
              type="submit"
              className="btn-primary"
              disabled={!canSubmit}
              aria-busy={loading}
            >
              {loading ? (
                <>
                  <Loader2 size={18} className="spin" aria-hidden="true" />
                  <span>Authenticating…</span>
                </>
              ) : (
                <span>Sign In</span>
              )}
            </button>
          </form>

          <div className="card-footer">
            <p>
              Require personnel registration?{' '}
              <Link to="/register">Create Civilian Account</Link>
            </p>
          </div>
        </section>
      </main>

      {/* Page Footer */}
      <footer className="page-footer" role="contentinfo">
        <span>© 2026 ResQNet Platform</span>
        <span className="footer-sep" aria-hidden="true">|</span>
        <span>Secure Response Infrastructure</span>
      </footer>
    </div>
  );
}
