import { createRoot } from 'react-dom/client';
import './style.css';

function App() {
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
