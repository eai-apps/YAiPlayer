export default function InfoView() {
  return (
    <div style={{ padding: "1.5rem 2rem", maxWidth: "800px", margin: "0 auto", overflowY: "auto", height: "100%" }}>
      <div style={{ marginBottom: "2rem" }}>
        <h1 style={{ fontFamily: "'Rajdhani',sans-serif", fontSize: "2rem", fontWeight: 700, letterSpacing: ".08em", background: "linear-gradient(135deg,var(--accent),var(--accent2))", WebkitBackgroundClip: "text", WebkitTextFillColor: "transparent", marginBottom: ".5rem" }}>
          YAiPlayer Web
        </h1>
        <p style={{ color: "var(--t2)", fontSize: ".9rem", lineHeight: 1.6 }}>
          Персональный IPTV Web-клиент и компаньон экосистемы YAiPlayer.
        </p>
      </div>

      {/* Applications & Downloads */}
      <div style={{ marginBottom: "2rem" }}>
        <h2 style={{ fontSize: "1.1rem", fontWeight: 700, marginBottom: "1rem", color: "var(--t1)" }}>
          Приложения YAiPlayer
        </h2>
        <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))", gap: "1rem" }}>
          {/* TV */}
          <div style={{ background: "var(--s1)", border: "1px solid var(--b2)", borderRadius: "12px", padding: "1.2rem" }}>
            <div style={{ fontSize: "1.8rem", marginBottom: ".5rem" }}>📺</div>
            <div style={{ fontWeight: 700, fontSize: "1rem", marginBottom: ".3rem" }}>YAiPlayer Android TV</div>
            <div style={{ fontSize: ".78rem", color: "var(--t2)", marginBottom: "1rem", lineHeight: 1.4 }}>
              Оптимизировано для пультов ДУ телевизоров и ТВ-приставок на Android TV.
            </div>
            <span style={{ display: "inline-block", background: "var(--s2)", border: "1px solid var(--b2)", borderRadius: "6px", padding: ".35rem .7rem", fontSize: ".72rem", color: "var(--t3)" }}>
              Релизная сборка формируется
            </span>
          </div>

          {/* Mobile */}
          <div style={{ background: "var(--s1)", border: "1px solid var(--b2)", borderRadius: "12px", padding: "1.2rem" }}>
            <div style={{ fontSize: "1.8rem", marginBottom: ".5rem" }}>📱</div>
            <div style={{ fontWeight: 700, fontSize: "1rem", marginBottom: ".3rem" }}>YAiPlayer Android Mobile</div>
            <div style={{ fontSize: ".78rem", color: "var(--t2)", marginBottom: "1rem", lineHeight: 1.4 }}>
              Версия для смартфонов и планшетов с сенсорным управлением.
            </div>
            <span style={{ display: "inline-block", background: "var(--s2)", border: "1px solid var(--b2)", borderRadius: "6px", padding: ".35rem .7rem", fontSize: ".72rem", color: "var(--t3)" }}>
              Релизная сборка формируется
            </span>
          </div>

          {/* iOS / PWA */}
          <div style={{ background: "var(--s1)", border: "1px solid var(--b2)", borderRadius: "12px", padding: "1.2rem" }}>
            <div style={{ fontSize: "1.8rem", marginBottom: ".5rem" }}>🍎</div>
            <div style={{ fontWeight: 700, fontSize: "1rem", marginBottom: ".3rem" }}>iOS / Safari Web App</div>
            <div style={{ fontSize: ".78rem", color: "var(--t2)", marginBottom: "1rem", lineHeight: 1.4 }}>
              Работает непосредственно в браузере Safari на iPhone и iPad.
            </div>
            <div style={{ fontSize: ".72rem", color: "var(--accent)", lineHeight: 1.4 }}>
              Для установки: нажмите <strong>«Поделиться»</strong> (квадрат со стрелкой) → <strong>«На экран Домой»</strong>.
            </div>
          </div>
        </div>
      </div>

      {/* Rules & Privacy */}
      <div style={{ marginBottom: "2rem", background: "var(--s1)", border: "1px solid var(--b1)", borderRadius: "12px", padding: "1.2rem 1.4rem" }}>
        <h2 style={{ fontSize: "1rem", fontWeight: 700, marginBottom: ".8rem" }}>Конфиденциальность и безопасность</h2>
        <ul style={{ fontSize: ".82rem", color: "var(--t2)", lineHeight: 1.7, paddingLeft: "1.2rem" }}>
          <li>YAiPlayer является персональным плеером и не предоставляет никаких встроенных телеканалов, видеоархивов или пиратского контента.</li>
          <li>Пользователь подключает исключительно собственные легальные плейлисты (M3U/M3U8), учетные записи Xtream Codes или Stalker-порталы.</li>
          <li>Пароли провайдеров и личные токены доступа не отправляются в открытом виде на сервер и хранятся локально в памяти браузера.</li>
          <li>Защищённое проксирование HTTP/CORS потоков и каталогов подключено через Worker с использованием непрозрачных зашифрованных дескрипторов (opaque media handles) при авторизации в аккаунте YAiPlayer. Хранилище сессии и источников работает в оперативной памяти (memory-only mode).</li>
        </ul>
      </div>

      {/* Attribution & Licenses */}
      <div style={{ background: "var(--s1)", border: "1px solid var(--b1)", borderRadius: "12px", padding: "1.2rem 1.4rem" }}>
        <h2 style={{ fontSize: "1rem", fontWeight: 700, marginBottom: ".8rem" }}>Лицензии и исходный код</h2>
        <div style={{ fontSize: ".82rem", color: "var(--t2)", lineHeight: 1.6 }}>
          <p style={{ marginBottom: ".5rem" }}>
            <strong>YAiPlayer Web</strong> разработан с использованием компонентов интерфейса <strong>StreamVault</strong>.
          </p>
          <p style={{ marginBottom: ".5rem" }}>
            <strong>Источник Web-основы:</strong> StreamVault. Исходная лицензия сохранена в LICENSE.
          </p>
          <p style={{ marginBottom: ".5rem" }}>
            Исходный код фронтенд-донора распространяется под лицензией <strong>PolyForm Noncommercial License 1.0.0</strong>. Коммерческое развертывание регулируется условиями правообладателей.
          </p>
          <p style={{ color: "var(--t3)", fontSize: ".75rem" }}>
            Android-приложения YAiPlayer развиваются отдельно под лицензией GNU GPL-3.0 и не содержат кода Web-клиента.
          </p>
        </div>
      </div>
    </div>
  );
}
