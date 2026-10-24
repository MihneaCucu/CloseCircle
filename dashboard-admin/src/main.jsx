import React from 'react';
import { createRoot } from 'react-dom/client';
import './style.css';

function App() {
  const apiBaseUrl = import.meta.env.VITE_API_BASE_URL;
  const [apiState, setApiState] = React.useState(apiBaseUrl ? 'checking' : 'unconfigured');

  React.useEffect(() => {
    if (!apiBaseUrl) return;
    let active = true;
    let healthUrl;
    try {
      healthUrl = new URL('/actuator/health', apiBaseUrl);
      if (healthUrl.protocol !== 'https:' && healthUrl.hostname !== 'localhost') {
        throw new Error('HTTPS required');
      }
    } catch {
      setApiState('invalid');
      return;
    }
    fetch(healthUrl, { headers: { Accept: 'application/json' } })
      .then((response) => {
        if (!response.ok) throw new Error('API unavailable');
        return response.json();
      })
      .then((health) => active && setApiState(health.status === 'UP' ? 'online' : 'unavailable'))
      .catch(() => active && setApiState('unavailable'));
    return () => { active = false; };
  }, [apiBaseUrl]);

  const stateLabel = {
    checking: 'Se verifică API-ul…',
    online: 'API-ul răspunde prin HTTPS',
    unavailable: 'API-ul este momentan indisponibil',
    invalid: 'Adresa API trebuie să folosească HTTPS',
    unconfigured: 'API-ul nu este configurat',
  }[apiState];
  return (
    <main className="shell">
      <div className="mark" aria-hidden="true">C</div>
      <p className="eyebrow">CLOSECIRCLE · INFRASTRUCTURĂ DE DEMO</p>
      <h1>Un cerc de oameni,<br />mai aproape.</h1>
      <p className="intro">Dashboard-ul React este pregătit pentru conectarea la API. Interfața de administrare și simulatorul GPS se implementează în T-25.</p>
      <div className="status"><span className={`dot ${apiState}`} /> {stateLabel}</div>
      {!apiBaseUrl && <p className="config-note">Setați VITE_API_BASE_URL în Render pentru a verifica API-ul.</p>}
    </main>
  );
}

createRoot(document.getElementById('root')).render(<App />);
