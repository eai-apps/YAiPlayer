// YAiPlayer contributors, 2026-11-04: memory-only verified sessions.
import { safeFetch, API_BASE_URL } from './apiConfig.js';
class AuthAdapter {
  constructor() {
    this.epoch = 0; this.currentUser = null; this.currentSession = null; this.listeners = new Set(); this.expiryTimer = null;
    try { sessionStorage.removeItem(`yai_auth_web:${encodeURIComponent(API_BASE_URL || 'default')}`); } catch {}
  }
  subscribe(listener) { this.listeners.add(listener); return () => this.listeners.delete(listener); }
  notify() { for (const listener of this.listeners) { try { listener(this.getUser(), this.getSession()); } catch {} } }
  getUser() { return this.currentSession?.expiresAtMs > Date.now() ? this.currentUser : null; }
  getSession() { const s = this.currentSession; return s?.expiresAtMs > Date.now() ? { expiresAtMs: s.expiresAtMs, userId: s.userId } : null; }
  getToken() { return this.currentSession?.expiresAtMs > Date.now() ? this.currentSession.token : null; }
  isAdmin() { return this.getUser()?.role === 'admin'; }
  getEpoch() { return this.epoch; }
  clear() { clearTimeout(this.expiryTimer); this.currentUser = null; this.currentSession = null; }
  invalidateIfCurrent(epoch, token) { if (this.epoch === epoch && this.currentSession?.token === token) { ++this.epoch; this.clear(); this.notify(); } }
  valid(user, session) {
    return user && typeof user.id === 'string' && user.id.length > 0 && user.id.length <= 256 &&
      typeof user.username === 'string' && user.username.length <= 128 && ['admin', 'user'].includes(user.role) &&
      session && session.userId === user.id && /^[a-f0-9]{64}$/i.test(session.token) &&
      Number.isSafeInteger(session.expiresAtMs) && session.expiresAtMs > Date.now();
  }
  async authenticate(kind, username, password, email, name, signal) {
    if (!username?.trim() || username.length > 128 || !password || password.length > 1024) throw new Error('Проверьте логин и пароль');
    const epoch = ++this.epoch;
    const res = await safeFetch(`auth/${kind}`, { method: 'POST', body: { username: username.trim(), password,
      ...(kind === 'register' ? { email: email?.trim() || null, name: name?.trim() || undefined } : {}),
      deviceType: 'web_browser', deviceName: 'YAiPlayer Web' }, signal });
    if (!this.valid(res?.user, res?.session)) throw new Error('Некорректный ответ авторизации');
    const token = res.session.token;
    try {
      const me = await safeFetch('auth/me', { token, signal });
      if (epoch !== this.epoch || signal?.aborted) throw new Error('Операция отменена');
      if (!this.valid(me?.user, res.session)) throw new Error('Пользователь сессии не подтверждён');
      this.currentUser = me.user; this.currentSession = res.session;
      const expire = () => {
        if (this.currentSession?.token !== token) return;
        const left = res.session.expiresAtMs - Date.now();
        if (left <= 0) this.invalidateIfCurrent(this.epoch, token);
        else this.expiryTimer = setTimeout(expire, Math.min(left, 2147483647));
      };
      clearTimeout(this.expiryTimer); expire(); this.notify();
      return { user: this.getUser() };
    } catch (failure) {
      void safeFetch('auth/logout', { method: 'POST', body: {}, token }).catch(() => {});
      throw failure;
    }
  }
  login(username, password, signal) { return this.authenticate('login', username, password, null, null, signal); }
  register(username, password, email, name, signal) { return this.authenticate('register', username, password, email, name, signal); }
  async verifyMe(signal) {
    const token = this.getToken(), epoch = this.epoch; if (!token) return null;
    try {
      const me = await safeFetch('auth/me', { token, signal });
      if (epoch !== this.epoch || signal?.aborted) return null;
      if (!this.valid(me?.user, this.currentSession)) throw new Error('Сессия не подтверждена');
      this.currentUser = me.user; this.notify(); return me.user;
    } catch (error) { if (error.status === 401) this.invalidateIfCurrent(epoch, token); return null; }
  }
  async logout() {
    const token = this.getToken(); ++this.epoch; this.clear(); this.notify();
    if (!token) return { serverConfirmed: false };
    try { const ack = await safeFetch('auth/logout', { method: 'POST', body: {}, token }); return { serverConfirmed: ack?.success === true }; }
    catch { return { serverConfirmed: false }; }
  }
}
export const authAdapter = new AuthAdapter();
