import { useState, useEffect, useCallback } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import {
  ShieldAlert,
  User,
  Mail,
  Lock,
  Eye,
  EyeOff,
  Loader2,
  CheckCircle2,
  AlertTriangle,
  ChevronLeft,
} from 'lucide-react';
import { register, getCurrentUser, getDashboardRoute, initializeCsrf } from '../api/auth';
import './RegisterPage.css';

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

function validateFields(fields) {
  const errors = {};
  if (!fields.email.trim()) {
    errors.email = 'Email address is required.';
  } else if (!EMAIL_REGEX.test(fields.email.trim())) {
    errors.email = 'Enter a valid email address.';
  }

  if (!fields.username.trim()) {
    errors.username = 'Username is required.';
  } else if (fields.username.trim().length < 3) {
    errors.username = 'Username must be at least 3 characters.';
  }

  if (!fields.password) {
    errors.password = 'Password is required.';
  } else if (fields.password.length < 6) {
    errors.password = 'Password must be between 6 and 30 characters.';
  } else if (fields.password.length > 30) {
    errors.password = 'Password must be between 6 and 30 characters.';
  }

  if (!fields.confirmPassword) {
    errors.confirmPassword = 'Confirmation password is required.';
  } else if (fields.password !== fields.confirmPassword) {
    errors.confirmPassword = 'Passwords do not match.';
  }

  return errors;
}

function PasswordStrength({ password }) {
  if (!password) return null;
  const len = password.length;
  let score = 0;
  if (len >= 6) score++;
  if (len >= 10) score++;
  if (/[A-Z]/.test(password)) score++;
  if (/[0-9]/.test(password)) score++;
  if (/[^A-Za-z0-9]/.test(password)) score++;

  const labels = ['', 'Weak', 'Fair', 'Good', 'Strong', 'Very Strong'];
  const colors = ['', '#ff3d3d', '#ffb347', '#ffd700', '#00e5a0', '#00d4ff'];

  return (
    <div
      className="pw-strength"
      aria-live="polite"
      aria-label={`Password strength: ${labels[score]}`}
    >
      <div className="pw-bars">
        {[1, 2, 3, 4, 5].map((i) => (
          <div
            key={i}
            className="pw-bar"
            style={{ background: i <= score ? colors[score] : undefined }}
          />
        ))}
      </div>
      {score > 0 && (
        <span className="pw-label" style={{ color: colors[score] }}>
          {labels[score]}
        </span>
      )}
    </div>
  );
}

function TopoBackground() {
  return (
    <svg
      className="topo-svg"
      aria-hidden="true"
      viewBox="0 0 800 600"
      preserveAspectRatio="xMidYMid slice"
    >
      <defs>
        <radialGradient id="rg-reg1" cx="70%" cy="35%">
          <stop offset="0%" stopColor="#00d4ff" stopOpacity="0.08" />
          <stop offset="100%" stopColor="transparent" stopOpacity="0" />
        </radialGradient>
        <radialGradient id="rg-reg2" cx="15%" cy="80%">
          <stop offset="0%" stopColor="#ff4b1f" stopOpacity="0.07" />
          <stop offset="100%" stopColor="transparent" stopOpacity="0" />
        </radialGradient>
      </defs>
      <rect width="800" height="600" fill="url(#rg-reg1)" />
      <rect width="800" height="600" fill="url(#rg-reg2)" />
      {[50, 100, 150, 200, 250, 295].map((r, i) => (
        <ellipse
          key={i}
          cx="600"
          cy="200"
          rx={r}
          ry={r * 0.75}
          fill="none"
          stroke="#00d4ff"
          strokeWidth="0.5"
          strokeOpacity={0.07 - i * 0.009}
        />
      ))}
      {[40, 80, 120, 165, 200].map((r, i) => (
        <ellipse
          key={`b${i}`}
          cx="150"
          cy="480"
          rx={r * 1.2}
          ry={r}
          fill="none"
          stroke="#00d4ff"
          strokeWidth="0.4"
          strokeOpacity={0.06 - i * 0.008}
        />
      ))}
      {Array.from({ length: 10 }, (_, i) => (
        <line
          key={`h${i}`}
          x1="0"
          y1={i * 70}
          x2="800"
          y2={i * 70}
          stroke="#0099cc"
          strokeWidth="0.3"
          strokeOpacity="0.07"
        />
      ))}
      {Array.from({ length: 14 }, (_, i) => (
        <line
          key={`v${i}`}
          x1={i * 65}
          y1="0"
          x2={i * 65}
          y2="600"
          stroke="#0099cc"
          strokeWidth="0.3"
          strokeOpacity="0.07"
        />
      ))}
    </svg>
  );
}

export default function RegisterPage() {
  const navigate = useNavigate();

  const [formData, setFormData] = useState({
    email: '',
    username: '',
    password: '',
    confirmPassword: '',
  });

  const [showPassword, setShowPassword] = useState(false);
  const [showConfirmPassword, setShowConfirmPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [fieldErrors, setFieldErrors] = useState({});
  const [touched, setTouched] = useState({});
  const [globalError, setGlobalError] = useState('');
  const [successMessage, setSuccessMessage] = useState('');

  useEffect(() => {
    initializeCsrf().then(() => {
      getCurrentUser().then((user) => {
        if (user && user.roles) {
          navigate(getDashboardRoute(user.roles), { replace: true });
        }
      });
    });
  }, [navigate]);

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData((prev) => ({ ...prev, [name]: value }));
    if (touched[name]) {
      const errors = validateFields({ ...formData, [name]: value });
      setFieldErrors((prev) => ({ ...prev, [name]: errors[name] }));
    }
  };

  const handleBlur = (name) => {
    setTouched((prev) => ({ ...prev, [name]: true }));
    const errors = validateFields(formData);
    setFieldErrors((prev) => ({ ...prev, [name]: errors[name] }));
  };

  const handleSubmit = useCallback(
    async (e) => {
      e.preventDefault();
      const errors = validateFields(formData);
      if (Object.keys(errors).length > 0) {
        setFieldErrors(errors);
        setTouched({
          email: true,
          username: true,
          password: true,
          confirmPassword: true,
        });
        return;
      }

      setLoading(true);
      setGlobalError('');
      setSuccessMessage('');

      try {
        const responseText = await register(
          formData.email.trim(),
          formData.username.trim(),
          formData.password
        );
        setSuccessMessage(
          responseText || 'Account created successfully! Redirecting to sign in…'
        );
        setFormData({
          email: '',
          username: '',
          password: '',
          confirmPassword: '',
        });
        setTouched({});
        setTimeout(() => navigate('/login'), 2200);
      } catch (err) {
        setGlobalError(err.message || 'Registration failed. Please try again.');
      } finally {
        setLoading(false);
      }
    },
    [formData, navigate]
  );

  const canSubmit =
    !loading &&
    formData.email &&
    formData.username &&
    formData.password &&
    formData.confirmPassword;

  return (
    <div className="register-page">
      <TopoBackground />
      <div className="bg-overlay" aria-hidden="true" />

      {/* Navigation Header */}
      <header className="reg-header" role="banner">
        <Link to="/login" className="back-link" aria-label="Back to Login">
          <ChevronLeft size={16} />
          <span>Sign In</span>
        </Link>
        <div className="reg-brand">
          <ShieldAlert size={20} />
          <span>ResQNet</span>
        </div>
      </header>

      {/* Main Container */}
      <main className="register-main">
        <div className="register-card">
          <div className="card-accent" aria-hidden="true" />

          <div className="card-header">
            <h1>Create Account</h1>
            <p className="card-subtitle">Register for the ResQNet emergency response network</p>

            <div className="role-note" role="note">
              <AlertTriangle size={14} aria-hidden="true" />
              <span>
                Standard registration provides civilian reporter access. Station manager and
                worker assignments require administrative authorization.
              </span>
            </div>
          </div>

          {globalError && (
            <div className="alert alert-error" role="alert" aria-live="assertive">
              <AlertTriangle size={15} aria-hidden="true" />
              <span>{globalError}</span>
            </div>
          )}

          {successMessage && (
            <div className="alert alert-success" role="status" aria-live="polite">
              <CheckCircle2 size={15} aria-hidden="true" />
              <span>{successMessage}</span>
            </div>
          )}

          <form onSubmit={handleSubmit} noValidate aria-label="Registration form">
            {/* Email Field */}
            <div
              className={`field-group ${
                touched.email && fieldErrors.email ? 'field-error' : ''
              }`}
            >
              <label htmlFor="reg-email">Email Address</label>
              <div className="input-wrapper">
                <Mail size={16} className="input-icon" aria-hidden="true" />
                <input
                  id="reg-email"
                  name="email"
                  type="email"
                  value={formData.email}
                  onChange={handleChange}
                  onBlur={() => handleBlur('email')}
                  autoComplete="email"
                  placeholder="name@example.com"
                  required
                  aria-required="true"
                  disabled={loading}
                  aria-invalid={!!(touched.email && fieldErrors.email)}
                  aria-describedby={
                    touched.email && fieldErrors.email ? 'email-err' : undefined
                  }
                />
              </div>
              {touched.email && fieldErrors.email && (
                <span className="field-error-msg" id="email-err" role="alert">
                  {fieldErrors.email}
                </span>
              )}
            </div>

            {/* Username Field */}
            <div
              className={`field-group ${
                touched.username && fieldErrors.username ? 'field-error' : ''
              }`}
            >
              <label htmlFor="reg-username">Username</label>
              <div className="input-wrapper">
                <User size={16} className="input-icon" aria-hidden="true" />
                <input
                  id="reg-username"
                  name="username"
                  type="text"
                  value={formData.username}
                  onChange={handleChange}
                  onBlur={() => handleBlur('username')}
                  autoComplete="username"
                  placeholder="Choose username"
                  required
                  aria-required="true"
                  disabled={loading}
                  aria-invalid={!!(touched.username && fieldErrors.username)}
                  aria-describedby={
                    touched.username && fieldErrors.username ? 'username-err' : undefined
                  }
                />
              </div>
              {touched.username && fieldErrors.username && (
                <span className="field-error-msg" id="username-err" role="alert">
                  {fieldErrors.username}
                </span>
              )}
            </div>

            {/* Password Field */}
            <div
              className={`field-group ${
                touched.password && fieldErrors.password ? 'field-error' : ''
              }`}
            >
              <label htmlFor="reg-password">Password</label>
              <div className="input-wrapper">
                <Lock size={16} className="input-icon" aria-hidden="true" />
                <input
                  id="reg-password"
                  name="password"
                  type={showPassword ? 'text' : 'password'}
                  value={formData.password}
                  onChange={handleChange}
                  onBlur={() => handleBlur('password')}
                  autoComplete="new-password"
                  placeholder="6 to 30 characters"
                  required
                  aria-required="true"
                  disabled={loading}
                  aria-invalid={!!(touched.password && fieldErrors.password)}
                  aria-describedby={
                    touched.password && fieldErrors.password ? 'password-err' : undefined
                  }
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
                <span className="field-error-msg" id="password-err" role="alert">
                  {fieldErrors.password}
                </span>
              )}
              <PasswordStrength password={formData.password} />
            </div>

            {/* Confirm Password Field */}
            <div
              className={`field-group ${
                touched.confirmPassword && fieldErrors.confirmPassword
                  ? 'field-error'
                  : ''
              }`}
            >
              <label htmlFor="reg-confirm">Confirm Password</label>
              <div className="input-wrapper">
                <Lock size={16} className="input-icon" aria-hidden="true" />
                <input
                  id="reg-confirm"
                  name="confirmPassword"
                  type={showConfirmPassword ? 'text' : 'password'}
                  value={formData.confirmPassword}
                  onChange={handleChange}
                  onBlur={() => handleBlur('confirmPassword')}
                  autoComplete="new-password"
                  placeholder="Repeat your password"
                  required
                  aria-required="true"
                  disabled={loading}
                  aria-invalid={
                    !!(touched.confirmPassword && fieldErrors.confirmPassword)
                  }
                  aria-describedby={
                    touched.confirmPassword && fieldErrors.confirmPassword
                      ? 'confirm-err'
                      : undefined
                  }
                />
                <button
                  type="button"
                  className="pw-toggle"
                  onClick={() => setShowConfirmPassword((prev) => !prev)}
                  aria-label={
                    showConfirmPassword
                      ? 'Hide confirmation password'
                      : 'Show confirmation password'
                  }
                  disabled={loading}
                >
                  {showConfirmPassword ? <EyeOff size={16} /> : <Eye size={16} />}
                </button>
              </div>
              {touched.confirmPassword && fieldErrors.confirmPassword && (
                <span className="field-error-msg" id="confirm-err" role="alert">
                  {fieldErrors.confirmPassword}
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
                  <span>Registering Account…</span>
                </>
              ) : (
                <span>Register</span>
              )}
            </button>
          </form>

          <div className="card-footer">
            <p>
              Already registered? <Link to="/login">Sign in to ResQNet</Link>
            </p>
          </div>
        </div>
      </main>
    </div>
  );
}
