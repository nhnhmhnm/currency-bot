import { useEffect, useState } from 'react';
import { ArrowLeftRight, LogOut } from 'lucide-react';
import Auth from './Auth';
import Dashboard from './Dashboard';
import { api, hasSession, setSession, User } from './api';

export default function App() {
  const [view, setView] = useState<'main' | 'login' | 'signup'>('main');
  const [user, setUser] = useState<User | null>(null);
  const [notice, setNotice] = useState('');
  const [ready, setReady] = useState(!hasSession());
  useEffect(() => {
    if (hasSession()) api<User>('/user/me').then(setUser).catch(() => { setSession(null); setNotice('로그인 정보를 확인할 수 없습니다. 다시 로그인해 주세요.'); }).finally(() => setReady(true));
    const expired = () => { setUser(null); setNotice('로그인이 만료되었습니다. 다시 로그인해 주세요.'); };
    window.addEventListener('session-expired', expired);
    return () => window.removeEventListener('session-expired', expired);
  }, []);
  async function logout() {
    try { await api('/auth/logout', { method: 'POST' }); }
    catch { setNotice('이 브라우저에서 로그아웃했습니다. 서버 연결 상태를 확인해 주세요.'); }
    finally { setSession(null); setUser(null); }
  }
  return <>
    <header className="site-header"><div className="header-inner">
      <button className="brand" onClick={() => setView('main')} aria-label="Currency Bot 홈"><span className="brand-mark"><ArrowLeftRight size={23} /></span><span>currency<span className="brand-light">bot</span><span className="brand-dot">.</span></span></button>
      <nav aria-label="주 메뉴"><button className={`nav-link ${view === 'main' ? 'active' : ''}`} onClick={() => setView('main')}>환율 보드</button></nav>
      <div className="account-nav">{!ready ? <span className="muted">확인 중</span> : user ? <><span className="user-name">{user.name} 님</span><button className="icon-button" onClick={logout} title="로그아웃" aria-label="로그아웃"><LogOut size={19} /></button></> : <><button className="text-button" onClick={() => setView('login')}>로그인</button><button className="button small primary" onClick={() => setView('signup')}>회원가입</button></>}</div>
    </div></header>
    {notice && <div className="global-notice" role="status">{notice}<button className="text-button" onClick={() => setNotice('')}>닫기</button></div>}
    {view !== 'main' ? <Auth mode={view} onMode={setView} onBack={() => setView('main')} onLogin={value => { setUser(value); setView('main'); setNotice(''); }} /> : <Dashboard user={user} onLogin={() => setView('login')} />}
    <footer className="site-footer"><span>currencybot.</span><span>환율의 기준을 더 명확하게.</span><span>KRW · Asia/Seoul</span></footer>
  </>;
}
