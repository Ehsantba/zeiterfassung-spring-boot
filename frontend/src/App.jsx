import { useCallback, useEffect, useState } from 'react';
import { api, ApiError } from './api.js';

const ROLE_LABEL = { EMPLOYEE: 'Mitarbeiter', SUPERVISOR: 'Vorgesetzter', ADMIN: 'Administrator' };
const STATUS_LABEL = { BOOKED: 'Gebucht', CORRECTION_PENDING: 'Korrektur offen', PENDING: 'Offen', APPROVED: 'Genehmigt', REJECTED: 'Abgelehnt', CANCELLED: 'Zurückgezogen' };
const ABSENCE_LABEL = { VACATION: 'Urlaub', SICK: 'Krank', OTHER: 'Sonstiges' };

const pad = (n) => String(n).padStart(2, '0');
const currentMonth = () => { const d = new Date(); return `${d.getFullYear()}-${pad(d.getMonth() + 1)}`; };
const monthRange = (m) => {
  const [y, mo] = m.split('-').map(Number);
  return [`${m}-01`, `${m}-${pad(new Date(y, mo, 0).getDate())}`];
};
const shiftMonth = (m, delta) => {
  const [y, mo] = m.split('-').map(Number);
  const d = new Date(y, mo - 1 + delta, 1);
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}`;
};
const fmtMinutes = (min) => `${Math.floor(Math.abs(min) / 60)}:${pad(Math.abs(min) % 60)}`;
const fmtSigned = (min) => `${min < 0 ? '−' : min > 0 ? '+' : ''}${fmtMinutes(min)}`;
const fmtDate = (iso) => new Date(`${iso}T00:00:00`).toLocaleDateString('de-DE', { weekday: 'short', day: '2-digit', month: '2-digit', year: 'numeric' });
const fmtTime = (t) => (t ? t.slice(0, 5) : '');
const fmtInstant = (iso) => new Date(iso).toLocaleString('de-DE', { dateStyle: 'short', timeStyle: 'short' });

function Message({ kind, children }) {
  if (!children) return null;
  return <div className={`msg ${kind}`} role={kind === 'error' ? 'alert' : 'status'}>{children}</div>;
}

function Badge({ status }) {
  return <span className={`badge ${status}`}>{STATUS_LABEL[status] || status}</span>;
}

// ---------------------------------------------------------------- Login

function Login({ onLoggedIn }) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    setError('');
    try {
      await api.login(email, password);
      onLoggedIn();
    } catch (err) {
      setError(err instanceof ApiError && err.status === 401 ? 'E-Mail oder Passwort ist falsch.' : err.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="login">
      <form className="card" onSubmit={submit}>
        <h1>Zeiterfassung</h1>
        <p className="muted">Bitte melden Sie sich an.</p>
        <label>E-Mail
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="username" required autoFocus />
        </label>
        <label>Passwort
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="current-password" required />
        </label>
        <Message kind="error">{error}</Message>
        <button className="primary" disabled={busy}>{busy ? 'Anmelden …' : 'Anmelden'}</button>
      </form>
    </main>
  );
}

// ---------------------------------------------------------------- Monatsnavigation

function MonthPicker({ month, setMonth }) {
  return (
    <div className="monthpicker">
      <button aria-label="Vorheriger Monat" onClick={() => setMonth(shiftMonth(month, -1))}>‹</button>
      <input type="month" value={month} onChange={(e) => e.target.value && setMonth(e.target.value)} aria-label="Monat" />
      <button aria-label="Nächster Monat" onClick={() => setMonth(shiftMonth(month, 1))}>›</button>
    </div>
  );
}

// ---------------------------------------------------------------- Meine Zeiten

function CorrectionForm({ entry, onDone, onCancel }) {
  const [form, setForm] = useState({
    newStartTime: fmtTime(entry.startTime), newEndTime: fmtTime(entry.endTime),
    newBreakMinutes: entry.breakMinutes, newNote: entry.note || '', reason: ''
  });
  const [error, setError] = useState('');
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  async function submit(e) {
    e.preventDefault();
    setError('');
    try {
      await api.requestCorrection(entry.id, { ...form, newBreakMinutes: Number(form.newBreakMinutes) });
      onDone();
    } catch (err) { setError(err.message); }
  }

  return (
    <tr className="inline-form">
      <td colSpan={7}>
        <form onSubmit={submit} className="grid">
          <label>Beginn<input type="time" value={form.newStartTime} onChange={set('newStartTime')} required /></label>
          <label>Ende<input type="time" value={form.newEndTime} onChange={set('newEndTime')} required /></label>
          <label>Pause (Min.)<input type="number" min="0" value={form.newBreakMinutes} onChange={set('newBreakMinutes')} required /></label>
          <label className="wide">Notiz<input value={form.newNote} onChange={set('newNote')} maxLength={500} /></label>
          <label className="wide">Grund der Korrektur<input value={form.reason} onChange={set('reason')} maxLength={500} required /></label>
          <div className="actions">
            <button className="primary">Antrag stellen</button>
            <button type="button" onClick={onCancel}>Abbrechen</button>
          </div>
          <Message kind="error">{error}</Message>
        </form>
      </td>
    </tr>
  );
}

function MyTimes() {
  const [month, setMonth] = useState(currentMonth());
  const [entries, setEntries] = useState([]);
  const [summary, setSummary] = useState(null);
  const [error, setError] = useState('');
  const [info, setInfo] = useState('');
  const [warnings, setWarnings] = useState([]);
  const [correcting, setCorrecting] = useState(null);
  const today = new Date().toISOString().slice(0, 10);
  const [form, setForm] = useState({ workDate: today, startTime: '08:00', endTime: '16:30', breakMinutes: 30, note: '' });

  const load = useCallback(async () => {
    try {
      const [from, to] = monthRange(month);
      const [list, sum] = await Promise.all([api.entries(from, to), api.summary(month)]);
      setEntries(list);
      setSummary(sum);
      setError('');
    } catch (err) { setError(err.message); }
  }, [month]);

  useEffect(() => { load(); }, [load]);

  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  async function book(e) {
    e.preventDefault();
    setError(''); setInfo(''); setWarnings([]);
    try {
      const res = await api.createEntry({ ...form, breakMinutes: Number(form.breakMinutes) });
      setWarnings(res.warnings || []);
      setInfo('Buchung gespeichert.');
      setForm({ ...form, note: '' });
      if (form.workDate.slice(0, 7) !== month) setMonth(form.workDate.slice(0, 7)); else load();
    } catch (err) { setError(err.message); }
  }

  const balanceClass = summary ? (summary.balanceMinutes < 0 ? 'neg' : 'pos') : '';

  return (
    <>
      <div className="toolbar">
        <MonthPicker month={month} setMonth={setMonth} />
        <div className="exports">
          <a className="button" href={`/api/export/csv?month=${month}`}>CSV</a>
          <a className="button" href={`/api/export/pdf?month=${month}`}>PDF</a>
        </div>
      </div>

      {summary && (
        <section className="stats" aria-label="Monatssummen">
          <div className="card stat"><span>Ist</span><strong>{fmtMinutes(summary.workedMinutes)} Std</strong></div>
          <div className="card stat"><span>Soll</span><strong>{fmtMinutes(summary.targetMinutes)} Std</strong></div>
          <div className={`card stat ${balanceClass}`}><span>Saldo</span><strong>{fmtSigned(summary.balanceMinutes)} Std</strong></div>
        </section>
      )}

      {summary && summary.days.some((d) => d.holiday || d.absence) && (
        <section className="card" aria-label="Feiertage und Abwesenheiten">
          <h2>Feiertage und Abwesenheiten</h2>
          <ul className="chips">
            {summary.days.filter((d) => d.holiday || d.absence).map((d) => (
              <li key={d.date} className={d.absence ? 'absence' : 'holiday'}>
                {new Date(`${d.date}T00:00:00`).toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', weekday: 'short' })}
                {' · '}{d.holiday || ABSENCE_LABEL[d.absence]}
              </li>
            ))}
          </ul>
        </section>
      )}

      <form className="card grid" onSubmit={book} aria-label="Zeit buchen">
        <h2 className="wide">Zeit buchen</h2>
        <label>Datum<input type="date" value={form.workDate} max={today} onChange={set('workDate')} required /></label>
        <label>Beginn<input type="time" value={form.startTime} onChange={set('startTime')} required /></label>
        <label>Ende<input type="time" value={form.endTime} onChange={set('endTime')} required /></label>
        <label>Pause (Min.)<input type="number" min="0" value={form.breakMinutes} onChange={set('breakMinutes')} required /></label>
        <label className="wide">Notiz (optional)<input value={form.note} onChange={set('note')} maxLength={500} /></label>
        <div className="actions"><button className="primary">Buchen</button></div>
        <Message kind="error">{error}</Message>
        <Message kind="ok">{info}</Message>
        {warnings.map((w) => <Message key={w} kind="warn">{w}</Message>)}
      </form>

      <div className="card table-wrap">
        <table>
          <thead><tr><th>Datum</th><th>Beginn</th><th>Ende</th><th>Pause</th><th>Netto</th><th>Status</th><th /></tr></thead>
          <tbody>
            {entries.length === 0 && <tr><td colSpan={7} className="muted">Keine Buchungen in diesem Monat.</td></tr>}
            {entries.map((e) => (
              <FragmentRow key={e.id}>
                <tr>
                  <td>{fmtDate(e.workDate)}{e.note && <div className="note">{e.note}</div>}</td>
                  <td>{fmtTime(e.startTime)}</td><td>{fmtTime(e.endTime)}</td>
                  <td>{e.breakMinutes} min</td><td>{fmtMinutes(e.netMinutes)}</td>
                  <td><Badge status={e.status} /></td>
                  <td>{e.status === 'BOOKED' && <button onClick={() => setCorrecting(e.id)}>Korrigieren</button>}</td>
                </tr>
                {correcting === e.id && (
                  <CorrectionForm entry={e} onCancel={() => setCorrecting(null)}
                    onDone={() => { setCorrecting(null); setInfo('Korrekturantrag gestellt.'); load(); }} />
                )}
              </FragmentRow>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}

function FragmentRow({ children }) { return <>{children}</>; }

// ---------------------------------------------------------------- Korrekturen

function CorrectionDetails({ c }) {
  return (
    <div className="diff">
      <span className="old">{fmtTime(c.oldStartTime)}–{fmtTime(c.oldEndTime)}, Pause {c.oldBreakMinutes} min</span>
      <span aria-hidden="true"> → </span>
      <span className="new">{fmtTime(c.newStartTime)}–{fmtTime(c.newEndTime)}, Pause {c.newBreakMinutes} min</span>
    </div>
  );
}

function MyCorrections() {
  const [list, setList] = useState([]);
  const [error, setError] = useState('');
  useEffect(() => { api.myCorrections().then(setList).catch((e) => setError(e.message)); }, []);
  return (
    <div className="card">
      <h2>Meine Korrekturanträge</h2>
      <Message kind="error">{error}</Message>
      {list.length === 0 && <p className="muted">Keine Anträge vorhanden.</p>}
      <ul className="list">
        {list.map((c) => (
          <li key={c.id}>
            <div><strong>{fmtDate(c.workDate)}</strong> <Badge status={c.status} /></div>
            <CorrectionDetails c={c} />
            <div className="muted">Grund: {c.reason}</div>
            {c.decisionComment && <div className="muted">Entscheidung von {c.decidedBy}: {c.decisionComment}</div>}
          </li>
        ))}
      </ul>
    </div>
  );
}

function Approvals({ onChange }) {
  const [list, setList] = useState([]);
  const [absences, setAbsences] = useState([]);
  const [comments, setComments] = useState({});
  const [error, setError] = useState('');
  const load = useCallback(() => Promise.all([api.pending(), api.pendingAbsences()])
    .then(([c, a]) => { setList(c); setAbsences(a); }).catch((e) => setError(e.message)), []);
  useEffect(() => { load(); }, [load]);

  async function decide(kind, id, approve) {
    setError('');
    const key = `${kind}-${id}`;
    try {
      await (kind === 'c' ? api.decide : api.decideAbsence)(id, approve, comments[key] || '');
      await load();
      onChange?.();
    } catch (err) { setError(err.message); }
  }

  const decision = (kind, id) => (
    <div className="decision">
      <input placeholder="Kommentar (bei Ablehnung erforderlich)" value={comments[`${kind}-${id}`] || ''}
        onChange={(e) => setComments({ ...comments, [`${kind}-${id}`]: e.target.value })} maxLength={500} aria-label="Kommentar" />
      <button className="primary" onClick={() => decide(kind, id, true)}>Genehmigen</button>
      <button className="danger" onClick={() => decide(kind, id, false)}>Ablehnen</button>
    </div>
  );

  return (
    <>
      <Message kind="error">{error}</Message>
      <div className="card">
        <h2>Offene Korrekturanträge</h2>
        {list.length === 0 && <p className="muted">Keine offenen Anträge.</p>}
        <ul className="list">
          {list.map((c) => (
            <li key={c.id}>
              <div><strong>{c.requestedBy}</strong> · {fmtDate(c.workDate)}</div>
              <CorrectionDetails c={c} />
              <div className="muted">Grund: {c.reason}</div>
              {decision('c', c.id)}
            </li>
          ))}
        </ul>
      </div>
      <div className="card">
        <h2>Offene Urlaubsanträge</h2>
        {absences.length === 0 && <p className="muted">Keine offenen Urlaubsanträge.</p>}
        <ul className="list">
          {absences.map((a) => (
            <li key={a.id}>
              <div><strong>{a.requestedBy}</strong> · {fmtDate(a.startDate)} bis {fmtDate(a.endDate)} ({a.workdays} Arbeitstage)</div>
              {a.note && <div className="muted">Notiz: {a.note}</div>}
              {decision('a', a.id)}
            </li>
          ))}
        </ul>
      </div>
    </>
  );
}

// ---------------------------------------------------------------- Abwesenheiten

function Absences() {
  const [year, setYear] = useState(new Date().getFullYear());
  const [data, setData] = useState(null);
  const [holidays, setHolidays] = useState([]);
  const [error, setError] = useState('');
  const [info, setInfo] = useState('');
  const [form, setForm] = useState({ type: 'VACATION', startDate: '', endDate: '', note: '' });
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  const load = useCallback(async () => {
    try {
      const [overview, hol] = await Promise.all([api.absences(year), api.holidays(year)]);
      setData(overview); setHolidays(hol); setError('');
    } catch (err) { setError(err.message); }
  }, [year]);
  useEffect(() => { load(); }, [load]);

  async function submit(e) {
    e.preventDefault();
    setError(''); setInfo('');
    try {
      const res = await api.createAbsence({ ...form, endDate: form.endDate || form.startDate });
      setInfo(res.status === 'PENDING' ? 'Urlaubsantrag gestellt. Er muss noch genehmigt werden.' : 'Abwesenheit eingetragen.');
      setForm({ ...form, startDate: '', endDate: '', note: '' });
      load();
    } catch (err) { setError(err.message); }
  }

  async function cancel(id) {
    setError('');
    try { await api.cancelAbsence(id); load(); } catch (err) { setError(err.message); }
  }

  const v = data?.vacation;
  return (
    <>
      <div className="toolbar">
        <div className="monthpicker">
          <button aria-label="Vorheriges Jahr" onClick={() => setYear(year - 1)}>‹</button>
          <strong>{year}</strong>
          <button aria-label="Nächstes Jahr" onClick={() => setYear(year + 1)}>›</button>
        </div>
      </div>

      {v && (
        <section className="stats four" aria-label="Urlaubskonto">
          <div className="card stat"><span>Anspruch</span><strong>{v.entitlement} Tage</strong></div>
          <div className="card stat"><span>Genehmigt</span><strong>{v.approved} Tage</strong></div>
          <div className="card stat"><span>Beantragt</span><strong>{v.pending} Tage</strong></div>
          <div className="card stat pos"><span>Verfügbar</span><strong>{v.remaining} Tage</strong></div>
        </section>
      )}

      <form className="card grid" onSubmit={submit} aria-label="Abwesenheit eintragen">
        <h2 className="wide">Abwesenheit eintragen</h2>
        <label>Art
          <select value={form.type} onChange={set('type')}>
            {Object.entries(ABSENCE_LABEL).map(([k, label]) => <option key={k} value={k}>{label}</option>)}
          </select>
        </label>
        <label>Von<input type="date" value={form.startDate} onChange={set('startDate')} required /></label>
        <label>Bis<input type="date" value={form.endDate} min={form.startDate} onChange={set('endDate')} /></label>
        <label className="wide">Notiz (optional)<input value={form.note} onChange={set('note')} maxLength={500} /></label>
        <div className="actions"><button className="primary">Eintragen</button></div>
        <Message kind="error">{error}</Message>
        <Message kind="ok">{info}</Message>
      </form>

      <div className="card table-wrap">
        <h2>Meine Abwesenheiten {year}</h2>
        <table>
          <thead><tr><th>Art</th><th>Zeitraum</th><th>Arbeitstage</th><th>Status</th><th /></tr></thead>
          <tbody>
            {(data?.absences || []).length === 0 && <tr><td colSpan={5} className="muted">Keine Einträge.</td></tr>}
            {(data?.absences || []).map((a) => (
              <tr key={a.id}>
                <td>{ABSENCE_LABEL[a.type]}{a.note && <div className="note">{a.note}</div>}</td>
                <td>{fmtDate(a.startDate)}{a.endDate !== a.startDate && <> bis {fmtDate(a.endDate)}</>}</td>
                <td>{a.workdays}</td>
                <td><Badge status={a.status} />{a.decisionComment && <div className="note">{a.decidedBy}: {a.decisionComment}</div>}</td>
                <td>{(a.status === 'PENDING' || a.status === 'APPROVED') && <button onClick={() => cancel(a.id)}>Zurückziehen</button>}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="card">
        <h2>Feiertage {year} (Nordrhein-Westfalen)</h2>
        <ul className="chips">
          {holidays.map((h) => (
            <li key={h.date} className="holiday">
              {new Date(`${h.date}T00:00:00`).toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', weekday: 'short' })} · {h.name}
            </li>
          ))}
        </ul>
      </div>
    </>
  );
}

// ---------------------------------------------------------------- Team

function Team() {
  const [members, setMembers] = useState([]);
  const [selected, setSelected] = useState('');
  const [month, setMonth] = useState(currentMonth());
  const [entries, setEntries] = useState([]);
  const [error, setError] = useState('');

  useEffect(() => { api.teamMembers().then((m) => { setMembers(m); if (m[0]) setSelected(String(m[0].id)); }).catch((e) => setError(e.message)); }, []);
  useEffect(() => {
    if (!selected) return;
    api.teamEntries(selected, month).then((l) => { setEntries(l); setError(''); }).catch((e) => setError(e.message));
  }, [selected, month]);

  return (
    <div className="card">
      <h2>Team (nur Lesezugriff)</h2>
      <div className="toolbar">
        <select value={selected} onChange={(e) => setSelected(e.target.value)} aria-label="Teammitglied">
          {members.map((m) => <option key={m.id} value={m.id}>{m.name}</option>)}
        </select>
        <MonthPicker month={month} setMonth={setMonth} />
      </div>
      <Message kind="error">{error}</Message>
      <table>
        <thead><tr><th>Datum</th><th>Beginn</th><th>Ende</th><th>Pause</th><th>Netto</th><th>Status</th></tr></thead>
        <tbody>
          {entries.length === 0 && <tr><td colSpan={6} className="muted">Keine Buchungen.</td></tr>}
          {entries.map((e) => (
            <tr key={e.id}><td>{fmtDate(e.workDate)}</td><td>{fmtTime(e.startTime)}</td><td>{fmtTime(e.endTime)}</td>
              <td>{e.breakMinutes} min</td><td>{fmtMinutes(e.netMinutes)}</td><td><Badge status={e.status} /></td></tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

// ---------------------------------------------------------------- Verwaltung

function Admin() {
  const [users, setUsers] = useState([]);
  const [audit, setAudit] = useState([]);
  const [error, setError] = useState('');
  const [info, setInfo] = useState('');
  const [form, setForm] = useState({ name: '', email: '', password: '', role: 'EMPLOYEE', supervisorId: '' });
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value });

  const load = useCallback(() => {
    Promise.all([api.users(), api.audit()]).then(([u, a]) => { setUsers(u); setAudit(a); }).catch((e) => setError(e.message));
  }, []);
  useEffect(() => { load(); }, [load]);

  async function create(e) {
    e.preventDefault();
    setError(''); setInfo('');
    try {
      await api.createUser({ ...form, supervisorId: form.supervisorId ? Number(form.supervisorId) : null });
      setInfo('Benutzer angelegt.');
      setForm({ name: '', email: '', password: '', role: 'EMPLOYEE', supervisorId: '' });
      load();
    } catch (err) { setError(err.message); }
  }

  return (
    <>
      <form className="card grid" onSubmit={create}>
        <h2 className="wide">Benutzer anlegen</h2>
        <label>Name<input value={form.name} onChange={set('name')} required /></label>
        <label>E-Mail<input type="email" value={form.email} onChange={set('email')} required /></label>
        <label>Passwort (mind. 10 Zeichen)<input type="password" minLength={10} value={form.password} onChange={set('password')} required autoComplete="new-password" /></label>
        <label>Rolle
          <select value={form.role} onChange={set('role')}>
            {Object.entries(ROLE_LABEL).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
          </select>
        </label>
        <label>Vorgesetzter
          <select value={form.supervisorId} onChange={set('supervisorId')}>
            <option value="">–</option>
            {users.filter((u) => u.role !== 'EMPLOYEE').map((u) => <option key={u.id} value={u.id}>{u.name}</option>)}
          </select>
        </label>
        <div className="actions"><button className="primary">Anlegen</button></div>
        <Message kind="error">{error}</Message>
        <Message kind="ok">{info}</Message>
      </form>

      <div className="card table-wrap">
        <h2>Benutzer</h2>
        <table>
          <thead><tr><th>Name</th><th>E-Mail</th><th>Rolle</th><th>Vorgesetzter</th></tr></thead>
          <tbody>{users.map((u) => <tr key={u.id}><td>{u.name}</td><td>{u.email}</td><td>{ROLE_LABEL[u.role]}</td><td>{u.supervisor || '–'}</td></tr>)}</tbody>
        </table>
      </div>

      <div className="card table-wrap">
        <h2>Änderungsprotokoll</h2>
        <table>
          <thead><tr><th>Zeitpunkt</th><th>Wer</th><th>Aktion</th><th>Vorher</th><th>Nachher</th></tr></thead>
          <tbody>
            {audit.map((a) => (
              <tr key={a.id}><td>{fmtInstant(a.occurredAt)}</td><td>{a.actor}</td><td>{a.objectType} {a.action}</td>
                <td className="small">{a.oldValue || '–'}</td><td className="small">{a.newValue || '–'}</td></tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}

// ---------------------------------------------------------------- App

export default function App() {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);
  const [tab, setTab] = useState('times');
  const [pendingCount, setPendingCount] = useState(0);

  const loadUser = useCallback(async () => {
    try { setUser(await api.me()); }
    catch { setUser(null); }
    finally { setLoading(false); }
  }, []);
  useEffect(() => { loadUser(); }, [loadUser]);

  const isManager = user && (user.role === 'SUPERVISOR' || user.role === 'ADMIN');
  const refreshPending = useCallback(() => {
    if (isManager) Promise.all([api.pending(), api.pendingAbsences()]).then(([c, a]) => setPendingCount(c.length + a.length)).catch(() => {});
  }, [isManager]);
  useEffect(() => { refreshPending(); }, [refreshPending, tab]);

  if (loading) return <p className="center muted">Lade …</p>;
  if (!user) return <Login onLoggedIn={() => { setLoading(true); loadUser(); }} />;

  async function logout() { await api.logout().catch(() => {}); setUser(null); setTab('times'); }

  const tabs = [
    ['times', 'Meine Zeiten'],
    ['mine', 'Meine Korrekturen'],
    ['absences', 'Abwesenheiten'],
    ...(isManager ? [['approvals', `Genehmigungen${pendingCount ? ` (${pendingCount})` : ''}`], ['team', 'Team']] : []),
    ...(user.role === 'ADMIN' ? [['admin', 'Verwaltung']] : [])
  ];

  return (
    <>
      <header className="top">
        <div className="brand"><span className="logo" aria-hidden="true" />Zeiterfassung</div>
        <div className="who">
          <span>{user.name} <small>{ROLE_LABEL[user.role]}</small></span>
          <button onClick={logout}>Abmelden</button>
        </div>
      </header>
      <nav className="tabs" aria-label="Bereiche">
        {tabs.map(([key, label]) => (
          <button key={key} className={tab === key ? 'active' : ''} aria-current={tab === key ? 'page' : undefined} onClick={() => setTab(key)}>{label}</button>
        ))}
      </nav>
      <main className="page">
        {tab === 'times' && <MyTimes />}
        {tab === 'mine' && <MyCorrections />}
        {tab === 'absences' && <Absences />}
        {tab === 'approvals' && <Approvals onChange={refreshPending} />}
        {tab === 'team' && <Team />}
        {tab === 'admin' && <Admin />}
      </main>
    </>
  );
}
