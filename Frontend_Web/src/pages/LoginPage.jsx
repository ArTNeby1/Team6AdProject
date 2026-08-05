import React, { useState } from 'react';
import { useNavigate, useLocation, Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

const LoginPage = () => {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const from = location.state?.from?.pathname || "/";

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setIsSubmitting(true);

    try {
      await login(email, password);
      navigate(from, { replace: true });
    } catch (err) {
      setError(err.message);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="auth-page">
      <div className="auth-container">
        <div className="auth-card">
          <div className="auth-header">
            <h2>欢迎回来</h2>
            <p>登录您的沿途账号以继续规划行程</p>
          </div>

          <form onSubmit={handleSubmit} className="auth-form">
            {error && <div className="auth-error">{error}</div>}

            <div className="form-group">
              <label>电子邮箱</label>
              <input
                type="email"
                placeholder="例如: 1260892734@qq.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
              />
            </div>

            <div className="form-group">
              <label>密码</label>
              <input
                type="password"
                placeholder="请输入您的密码"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
              />
            </div>

            <button
              type="submit"
              className="btn-primary auth-submit"
              disabled={isSubmitting}
            >
              {isSubmitting ? '登录中...' : '登录'}
            </button>
          </form>

          <div className="auth-footer">
            还没有账号？ <Link to="/register">立即注册</Link>
          </div>

          <div className="auth-hint">
             <p>演示账号: 1260892734@qq.com</p>
             <p>演示密码: 123456</p>
          </div>
        </div>
      </div>
    </div>
  );
};

export default LoginPage;
