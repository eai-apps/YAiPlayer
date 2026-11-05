import { useState, useEffect, useRef } from "react";
import { authAdapter } from "../adapters/authAdapter.js";
import { claimDevicePairing } from "../adapters/pairAdapter.js";

export default function PairingView({ onBack }) {
  const request = useRef(null);
  function nextSignal() { request.current?.abort(); request.current = new AbortController(); return request.current.signal; }
  useEffect(() => () => request.current?.abort(), []);
  const [pin, setPin] = useState("");
  const [deviceName, setDeviceName] = useState("Smart TV STB");
  const [user, setUser] = useState(() => authAdapter.getUser());

  // In-line auth for unauthenticated users
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [authLoading, setAuthLoading] = useState(false);

  // Claim states
  const [claiming, setClaiming] = useState(false);
  const [claimedDevice, setClaimedDevice] = useState(null);
  const [error, setError] = useState("");

  useEffect(() => {
    // Parse query params: code and device
    const params = new URLSearchParams(window.location.search);
    const codeParam = params.get("code");
    const devParam = params.get("device");
    if (codeParam && /^\d{6}$/.test(codeParam.trim())) {
      setPin(codeParam.trim());
    }
    if (devParam && devParam.trim()) {
      setDeviceName(devParam.trim().slice(0, 128));
    }

    return authAdapter.subscribe(u => setUser(u));
  }, []);

  async function handleLogin(e) {
    e.preventDefault();
    setError("");
    setAuthLoading(true);
    try {
      await authAdapter.login(username, password, nextSignal());
      setPassword("");
    } catch (err) {
      if (request.current?.signal.aborted) return;
      setError(err.message || "Ошибка входа");
    } finally {
      setPassword("");
      setAuthLoading(false);
    }
  }

  async function handleClaim() {
    if (claiming) return;
    if (!pin || !/^\d{6}$/.test(pin.trim())) {
      setError("Введите корректный 6-значный PIN-код");
      return;
    }
    setError("");
    setClaiming(true);
    try {
      const res = await claimDevicePairing(pin.trim(), nextSignal());
      setClaimedDevice(res.deviceName || deviceName);
    } catch (err) {
      if (request.current?.signal.aborted) return;
      setError(err.message || "Не удалось привязать устройство");
    } finally {
      setClaiming(false);
    }
  }

  return (
    <div className="setup" style={{ minHeight: "100%", padding: "2rem 1rem" }}>
      <div className="card" style={{ maxWidth: "460px" }}>
        <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: ".5rem" }}>
          <div className="logo" style={{ fontSize: "1.6rem" }}>ПРИВЯЗКА TV</div>
          {onBack && (
            <button className="btn-sm" style={{ width: "auto" }} onClick={onBack}>← В плеер</button>
          )}
        </div>
        <div className="tagline">Подтверждение входа на телевизоре или ТВ-приставке</div>

        {error && <div className="err">⚠️ {error}</div>}

        {claimedDevice ? (
          <div style={{ textAlign: "center", padding: "1.5rem 0" }}>
            <div style={{ fontSize: "2.8rem", marginBottom: ".8rem" }}>📺✓</div>
            <div style={{ fontSize: "1.1rem", fontWeight: 700, color: "var(--ok)", marginBottom: ".5rem" }}>
              Устройство успешно привязано!
            </div>
            <div style={{ fontSize: ".85rem", color: "var(--t2)", lineHeight: 1.5 }}>
              Телевизор <strong>{claimedDevice}</strong> авторизован в вашей учетной записи. На экране ТВ сейчас начнется загрузка.
            </div>
            {onBack && (
              <button className="btn-primary" style={{ marginTop: "1.5rem" }} onClick={onBack}>
                Вернуться к плееру
              </button>
            )}
          </div>
        ) : !user ? (
          <div>
            <div style={{ background: "var(--s2)", padding: ".8rem 1rem", borderRadius: "8px", fontSize: ".82rem", color: "var(--t2)", marginBottom: "1.2rem", border: "1px solid var(--b2)" }}>
              Для подтверждения привязки телевизора войдите в свой аккаунт YAiPlayer:
            </div>
            <form onSubmit={handleLogin}>
              <div className="fg">
                <label className="fl">Имя пользователя (Логин)</label>
                <input className="fi" required value={username} onChange={e => setUsername(e.target.value)} placeholder="username" />
              </div>
              <div className="fg">
                <label className="fl">Пароль</label>
                <input className="fi" type="password" required value={password} onChange={e => setPassword(e.target.value)} placeholder="••••••••" />
              </div>
              <button className="btn-primary" type="submit" disabled={authLoading}>
                {authLoading ? "Вход…" : "Войти и продолжить"}
              </button>
            </form>
          </div>
        ) : (
          <div>
            <div style={{ background: "var(--s2)", padding: ".75rem 1rem", borderRadius: "8px", marginBottom: "1.2rem", border: "1px solid var(--b2)", display: "flex", alignItems: "center", justifyContent: "space-between" }}>
              <div style={{ fontSize: ".82rem" }}>
                Вход выполнен: <strong>{user.username}</strong>
              </div>
              <button className="btn-sm" style={{ width: "auto", fontSize: ".68rem" }} onClick={() => authAdapter.logout()}>
                Сменить
              </button>
            </div>

            <div className="fg">
              <label className="fl">Код с экрана телевизора (6 цифр)</label>
              <input
                className="fi"
                style={{ fontSize: "1.4rem", letterSpacing: ".3em", textAlign: "center", fontFamily: "monospace" }}
                maxLength={6}
                value={pin}
                onChange={e => setPin(e.target.value.replace(/\D/g, ""))}
                placeholder="000000"
              />
              <div className="fhint">Введите PIN, который отображается в YAiPlayer на экране ТВ</div>
            </div>

            <div className="fg">
              <label className="fl">Целевое устройство</label>
              <input className="fi" disabled value={deviceName} style={{ opacity: 0.7 }} />
            </div>

            <button className="btn-primary" onClick={handleClaim} disabled={claiming || pin.length !== 6}>
              {claiming ? "Подтверждение…" : "Подтвердить привязку TV →"}
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
