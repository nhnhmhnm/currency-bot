import { FormEvent, useState } from 'react';
import { ArrowLeft, ArrowRight, Eye, EyeOff, LoaderCircle, LockKeyhole } from 'lucide-react';
import { api, login, User } from './api';

export default function Auth({ mode, onMode, onBack, onLogin }: {
  mode: 'login' | 'signup'; onMode: (mode: 'login' | 'signup') => void; onBack: () => void; onLogin: (user: User) => void;
}) {
  const [visible, setVisible] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const device = String(data.get('device')).trim();
    const password = String(data.get('password'));
    if (mode === 'signup' && password !== data.get('confirm')) { setError('비밀번호가 일치하지 않습니다.'); return; }
    setBusy(true); setError(''); setNotice('');
    try {
      if (mode === 'signup') {
        await api('/user/signup', { method: 'POST', body: JSON.stringify({
          device, password, name: String(data.get('name')).trim(), phone: String(data.get('phone')).trim(), type: 'GENERAL',
        }) });
        onMode('login'); setNotice('회원가입이 완료되었습니다. 로그인해 주세요.');
      } else onLogin(await login(device, password));
    } catch (e) { setError((e as Error).message); }
    finally { setBusy(false); }
  }
  return <main className="auth-page">
    <button className="text-button back-button" onClick={onBack} disabled={busy}><ArrowLeft size={17} /> 환율 보드</button>
    <section className="auth-content">
      <div className="auth-mark"><LockKeyhole size={27} /></div>
      <p className="eyebrow">CURRENCY BOT ACCOUNT</p>
      <h1>{mode === 'login' ? '다시 만나 반가워요' : '새로운 시작, 더 나은 환전'}</h1>
      <p className="muted">{mode === 'login' ? '내 지갑과 오늘의 환전 한도를 확인하세요.' : 'Currency Bot 계정을 만들어 주세요.'}</p>
      <form onSubmit={submit} className="auth-form">
        {mode === 'signup' && <label>이름<input name="name" autoComplete="name" required maxLength={50} /></label>}
        <label>아이디<input name="device" autoComplete="username" required maxLength={100} autoFocus /></label>
        {mode === 'signup' && <label>휴대전화<input name="phone" type="tel" autoComplete="tel" required maxLength={20} pattern="[0-9+ ()-]{9,20}" /></label>}
        <label>비밀번호<span className="password-field"><input name="password" type={visible ? 'text' : 'password'} autoComplete={mode === 'login' ? 'current-password' : 'new-password'} required minLength={mode === 'signup' ? 8 : 1} maxLength={128} /><button type="button" className="icon-button" aria-label={visible ? '비밀번호 숨기기' : '비밀번호 보기'} title={visible ? '비밀번호 숨기기' : '비밀번호 보기'} onClick={() => setVisible(!visible)}>{visible ? <EyeOff size={18} /> : <Eye size={18} />}</button></span></label>
        {mode === 'signup' && <label>비밀번호 확인<input name="confirm" type="password" autoComplete="new-password" required minLength={8} maxLength={128} /></label>}
        {error && <p className="message error" role="alert">{error}</p>}
        {notice && <p className="message success" role="status">{notice}</p>}
        <button className="button primary wide" disabled={busy}>{busy ? <LoaderCircle className="spin" size={18} /> : <ArrowRight size={18} />}{mode === 'login' ? '로그인' : '회원가입'}</button>
      </form>
      <p className="auth-switch">{mode === 'login' ? '아직 계정이 없으신가요?' : '이미 계정이 있으신가요?'} <button className="text-button" disabled={busy} onClick={() => { setError(''); setNotice(''); onMode(mode === 'login' ? 'signup' : 'login'); }}>{mode === 'login' ? '회원가입' : '로그인'}</button></p>
    </section>
  </main>;
}
