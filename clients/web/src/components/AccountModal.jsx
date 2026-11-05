import { useState, useEffect, useRef } from "react";
import { authAdapter } from "../adapters/authAdapter.js";
import { pullCloudRegistry } from "../adapters/syncAdapter.js";

export default function AccountModal({ onClose, onSelectCloudSource }) {
  const alive = useRef(true);
  const request = useRef(null);
  function nextSignal() { request.current?.abort(); request.current = new AbortController(); return request.current.signal; }
  useEffect(() => { alive.current = true; return () => { alive.current = false; request.current?.abort(); }; }, []);
  const [user, setUser] = useState(() => authAdapter.getUser());
  const [session, setSession] = useState(() => authAdapter.getSession());
  const [tab, setTab] = useState("login"); // "login" | "register"

  // Login & register fields
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [successMsg, setSuccessMsg] = useState("");

  // Cloud registry data
  const [cloudData, setCloudData] = useState(null);
  const [pulling, setPulling] = useState(false);

  useEffect(() => {
    return authAdapter.subscribe((u, s) => {
      setCloudData(null); setPulling(false);
      setUser(u);
      setSession(s);
    });
  }, []);

  async function handleLogin(e) {
    e.preventDefault();
    if (loading) return;
    setError("");
    setLoading(true);
    try {
      await authAdapter.login(username, password, nextSignal());
      setPassword("");
      setSuccessMsg("Успешный вход в аккаунт YAiPlayer");
    } catch (err) {
      if (!alive.current || request.current?.signal.aborted) return;
      setError(err.message || "Ошибка входа");
    } finally {
      setPassword("");
      if (!alive.current) return;
      setLoading(false);
    }
  }

  async function handleRegister(e) {
    e.preventDefault();
    if (loading) return;
    setError("");
    setLoading(true);
    try {
      await authAdapter.register(username, password, email, name, nextSignal());
      setPassword("");
      setSuccessMsg("Аккаунт успешно создан");
    } catch (err) {
      if (!alive.current || request.current?.signal.aborted) return;
      setError(err.message || "Ошибка регистрации");
    } finally {
      setPassword("");
      if (!alive.current) return;
      setLoading(false);
    }
  }

  async function handleLogout() {
    setError("");
    setLoading(true);
    try {
      const outcome = await authAdapter.logout();
      if (!alive.current) return;
      setCloudData(null);
      setSuccessMsg(outcome.serverConfirmed ? "Выход подтверждён" : "Локальный выход; серверный отзыв не подтверждён");
    } catch (err) {
      if (!alive.current || request.current?.signal.aborted) return;
      setError(err.message);
    } finally {
      setPassword("");
      if (!alive.current) return;
      setLoading(false);
    }
  }

  async function handlePullRegistry() {
    setError("");
    setPulling(true);
    try {
      const data = await pullCloudRegistry(nextSignal());
      if (!alive.current) return;
      setCloudData(data);
      setSuccessMsg(`Загружено источников: ${data.sources.length} (ревизия ${data.revision})`);
    } catch (err) {
      if (!alive.current || request.current?.signal.aborted) return;
      setError(err.message || "Не удалось загрузить реестр");
    } finally {
      setPulling(false);
    }
  }

  return (
    <div className="modal-ov" onClick={e => e.target === e.currentTarget && onClose()}>
      <div className="modal" style={{ maxWidth: "480px", maxHeight: "85vh", overflowY: "auto" }}>
        <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: "1rem" }}>
          <h2 style={{ fontFamily: "'Rajdhani',sans-serif", fontSize: "1.3rem", fontWeight: 700 }}>
            {user ? "Учетная запись YAiPlayer" : "Вход в YAiPlayer"}
          </h2>
          <button className="btn-sm" style={{ width: "auto" }} onClick={onClose}>✕</button>
        </div>

        {error && <div className="err" style={{ marginBottom: "1rem" }}>⚠️ {error}</div>}
        {successMsg && (
          <div style={{ background: "#00cc8815", border: "1px solid #00cc8840", borderRadius: "8px", padding: ".6rem .9rem", color: "var(--ok)", fontSize: ".82rem", marginBottom: "1rem" }}>
            ✓ {successMsg}
          </div>
        )}

        {user ? (
          <div>
            <div style={{ background: "var(--s2)", padding: "1rem", borderRadius: "10px", marginBottom: "1.2rem", border: "1px solid var(--b2)" }}>
              <div style={{ fontSize: ".9rem", fontWeight: 600 }}>Пользователь: {user.username}</div>
              <div style={{ fontSize: ".75rem", color: "var(--t2)", marginTop: ".25rem" }}>
                Роль: <span style={{ color: "var(--accent)", fontWeight: 600, textTransform: "uppercase" }}>{user.role}</span>
              </div>
              {user.email && <div style={{ fontSize: ".72rem", color: "var(--t3)", marginTop: ".2rem" }}>Email: {user.email}</div>}
              {session?.expiresAtMs && (
                <div style={{ fontSize: ".65rem", color: "var(--t3)", marginTop: ".35rem" }}>
                  Сессия активна до: {new Date(session.expiresAtMs).toLocaleTimeString()}
                </div>
              )}
            </div>

            {/* Cloud Registry Section */}
            <div style={{ marginBottom: "1.2rem" }}>
              <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: ".6rem" }}>
                <span className="fl" style={{ margin: 0 }}>Облачный реестр источников</span>
                <button className="btn-sm" onClick={handlePullRegistry} disabled={pulling}>
                  {pulling ? "Загрузка…" : "↺ Загрузить из облака"}
                </button>
              </div>

              {cloudData ? (
                <div style={{ display: "flex", flexDirection: "column", gap: ".5rem" }}>
                  {cloudData.sources.length === 0 ? (
                    <div style={{ fontSize: ".78rem", color: "var(--t3)", padding: ".8rem", textAlign: "center", background: "var(--s2)", borderRadius: "8px" }}>
                      В облачном реестре пока нет источников.
                    </div>
                  ) : (
                    cloudData.sources.map(s => (
                      <div key={s.id} style={{ background: "var(--s2)", border: "1px solid var(--b2)", borderRadius: "8px", padding: ".6rem .8rem", display: "flex", alignItems: "center", gap: ".6rem" }}>
                        <span style={{ fontSize: "1rem" }}>{s.type === "stalker" ? "📺" : s.type === "xtream" ? "📡" : "📋"}</span>
                        <div style={{ flex: 1, overflow: "hidden" }}>
                          <div style={{ fontSize: ".82rem", fontWeight: 600, whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>
                            {s.name}
                          </div>
                          <div style={{ fontSize: ".68rem", color: "var(--t3)" }}>
                            {s.type.toUpperCase()} · {s.serverUrl || s.url || "URL не указан"}
                          </div>
                          {s.needsLocalSetup && (
                            <div style={{ fontSize: ".65rem", color: "var(--accent2)", marginTop: ".15rem" }}>
                              Требуется локальный ввод пароля
                            </div>
                          )}
                        </div>
                        {onSelectCloudSource && (
                          <button className="btn-sm" style={{ width: "auto", fontSize: ".68rem" }} onClick={() => { onSelectCloudSource(s); onClose(); }}>
                            Выбрать →
                          </button>
                        )}
                      </div>
                    ))
                  )}
                  <div style={{ fontSize: ".68rem", color: "var(--t3)", marginTop: ".3rem", lineHeight: 1.4 }}>
                    Примечание: пароли провайдеров не хранятся в открытом реестре облака и вводятся локально в браузере.
                  </div>
                </div>
              ) : (
                <div style={{ fontSize: ".75rem", color: "var(--t3)", background: "var(--s2)", padding: ".7rem", borderRadius: "8px" }}>
                  Нажмите «Загрузить из облака», чтобы получить список синхронизированных источников вашего аккаунта.
                </div>
              )}
            </div>

            <button className="btn-sm danger" style={{ width: "100%", padding: ".6rem" }} onClick={handleLogout} disabled={loading}>
              {loading ? "Выход…" : "Выйти из аккаунта"}
            </button>
          </div>
        ) : (
          <div>
            <div className="tabs" style={{ marginBottom: "1rem" }}>
              <button className={`tab ${tab === "login" ? "on" : ""}`} onClick={() => { setTab("login"); setError(""); }}>
                Вход
              </button>
              <button className={`tab ${tab === "register" ? "on" : ""}`} onClick={() => { setTab("register"); setError(""); }}>
                Регистрация
              </button>
            </div>

            {tab === "login" ? (
              <form onSubmit={handleLogin}>
                <div className="fg">
                  <label className="fl">Имя пользователя (Логин)</label>
                  <input className="fi" required value={username} onChange={e => setUsername(e.target.value)} placeholder="username" />
                </div>
                <div className="fg">
                  <label className="fl">Пароль</label>
                  <input className="fi" type="password" required value={password} onChange={e => setPassword(e.target.value)} placeholder="••••••••" />
                </div>
                <button className="btn-primary" type="submit" disabled={loading}>
                  {loading ? "Вход…" : "Войти"}
                </button>
              </form>
            ) : (
              <form onSubmit={handleRegister}>
                <div className="fg">
                  <label className="fl">Имя пользователя (Логин) *</label>
                  <input className="fi" required value={username} onChange={e => setUsername(e.target.value)} placeholder="username" />
                </div>
                <div className="fg">
                  <label className="fl">Пароль *</label>
                  <input className="fi" type="password" required value={password} onChange={e => setPassword(e.target.value)} placeholder="••••••••" />
                </div>
                <div className="fg">
                  <label className="fl">Отображаемое имя (необязательно)</label>
                  <input className="fi" value={name} onChange={e => setName(e.target.value)} placeholder="Иван" />
                </div>
                <div className="fg">
                  <label className="fl">Email (необязательно)</label>
                  <input className="fi" type="email" value={email} onChange={e => setEmail(e.target.value)} placeholder="user@example.com" />
                </div>
                <button className="btn-primary" type="submit" disabled={loading}>
                  {loading ? "Создание аккаунта…" : "Зарегистрироваться"}
                </button>
              </form>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
