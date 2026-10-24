import React from 'react';
import { createRoot } from 'react-dom/client';
import './style.css';

function App() {
  const apiBaseUrl = import.meta.env.VITE_API_BASE_URL;

  React.useEffect(() => {
    if (!apiBaseUrl) return;
    let healthUrl;
    try {
      healthUrl = new URL('/actuator/health', apiBaseUrl);
      if (healthUrl.protocol !== 'https:' && healthUrl.hostname !== 'localhost') return;
    } catch {
      return;
    }
    fetch(healthUrl, { headers: { Accept: 'application/json' } }).catch(() => {});
  }, [apiBaseUrl]);

  return (
    <main className="shell">
      <div className="mark" aria-hidden="true">C</div>
      <p className="eyebrow">CLOSECIRCLE</p>
      <h1>Welcome to<br />CloseCircle.</h1>
      <p className="intro">Stay close to the people who matter.</p>
    </main>
  );
}

createRoot(document.getElementById('root')).render(<App />);
