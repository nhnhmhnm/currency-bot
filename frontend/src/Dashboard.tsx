import { FormEvent, lazy, Suspense, useEffect, useRef, useState } from 'react';
import { ArrowDownLeft, ArrowLeftRight, ArrowRight, ArrowUpRight, BadgeCheck, ChartNoAxesCombined, Check, CircleAlert, Clock3, LoaderCircle, RefreshCw, ShieldCheck, Wallet, X } from 'lucide-react';
import Decimal from 'decimal.js';
import { api, ApiError, User } from './api';
import { banks, best, colors, estimate, format, Metric, Mode, orderRequest, Rates, sampleRates, seoulDate, Summary } from './exchange';

const RateChart = lazy(() => import('./RateChart'));
const labels: Record<Mode, string> = { buy: '구매', sell: '판매', arbitrage: '재테크' };
const metricLabels: Record<Metric, string> = { buyRate: '전신환 매도율', sellRate: '전신환 매입율', baseRate: '매매기준율' };
type Confirmation = { mode: Mode; amount: string; currency: string; result: NonNullable<ReturnType<typeof estimate>>; key: string; storageKey: string };

export default function Dashboard({ user, onLogin }: { user: User | null; onLogin: () => void }) {
  const [currency, setCurrency] = useState('USD');
  const [metric, setMetric] = useState<Metric>('buyRate');
  const [mode, setMode] = useState<Mode>('buy');
  const [amount, setAmount] = useState('100000');
  const [rates, setRates] = useState<Rates | null>(null);
  const [summary, setSummary] = useState<Summary | null>(null);
  const [ratesError, setRatesError] = useState('');
  const [summaryError, setSummaryError] = useState('');
  const [loading, setLoading] = useState(true);
  const [demo, setDemo] = useState(false);
  const [refresh, setRefresh] = useState(0);
  const [message, setMessage] = useState('');
  const [formError, setFormError] = useState('');
  const [confirmation, setConfirmation] = useState<Confirmation | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState('');
  const dialog = useRef<HTMLDialogElement>(null);
  const submittingRef = useRef(false);

  useEffect(() => {
    let active = true;
    setLoading(true); setRatesError(''); setRates(null);
    if (demo) { setRates(sampleRates(currency)); setLoading(false); return; }
    api<Rates>(`/exchange/rates?currency=${currency}`).then(data => { if (active) setRates(data); })
      .catch(e => { if (active) setRatesError(e.message); }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [currency, demo, refresh]);

  useEffect(() => {
    let active = true;
    setSummary(null); setSummaryError('');
    if (!user) return;
    async function load() {
      try { const data = await api<Summary>('/exchange/summary'); if (active) { setSummary(data); setSummaryError(''); } }
      catch (e) { if (active) { setSummary(null); setSummaryError((e as Error).message); } }
    }
    void load();
    const timer = window.setInterval(() => { if (!document.hidden) void load(); }, 15000);
    return () => { active = false; clearInterval(timer); };
  }, [user, refresh]);

  useEffect(() => {
    if (confirmation && dialog.current && !dialog.current.open) dialog.current.showModal();
  }, [confirmation]);

  const bankRows = banks(rates);
  const buy = best(rates, 'buyRate', 'min');
  const sell = best(rates, 'sellRate', 'max');
  const result = estimate(rates, mode, amount);
  const source = mode === 'sell' ? currency : 'KRW';
  const destination = mode === 'buy' ? currency : 'KRW';
  const available = summary?.wallets.find(wallet => wallet.currency === source)?.available;
  const latest = rates?.points.map(point => point.time).sort().at(-1);
  const today = seoulDate();
  const dateLabel = new Intl.DateTimeFormat('ko-KR', { month: 'long', day: 'numeric', weekday: 'long', timeZone: 'Asia/Seoul' }).format(new Date());
  const currentRates = rates?.currency === currency && rates.date === today;

  function changeMode(value: Mode) {
    setMode(value); setMetric(value === 'arbitrage' ? 'baseRate' : value === 'buy' ? 'buyRate' : 'sellRate');
    setAmount(value === 'sell' ? (currency === 'JPY' ? '10000' : '100') : '100000'); setFormError('');
  }
  function prepare(event: FormEvent) {
    event.preventDefault(); setFormError(''); setMessage('');
    if (!user) { onLogin(); return; }
    if (!result || !currentRates || demo || !summary) return;
    const input = new Decimal(amount);
    const scale = mode === 'sell' ? rates!.scale : 0;
    if (input.decimalPlaces() > scale) { setFormError(`${source} 금액은 소수점 ${scale}자리까지 입력할 수 있습니다.`); return; }
    const storageKey = `pending-order:${user.id}:${mode}:${currency}:${input.toFixed()}`;
    const previousKey = sessionStorage.getItem(storageKey);
    if (!previousKey && (available == null || input.gt(available))) { setFormError('주문 가능한 잔액이 부족합니다.'); return; }
    if (!previousKey && new Decimal(summary.remainingUsd).lte(0)) { setFormError('오늘의 환전 한도를 모두 사용했습니다.'); return; }
    if (result.receive.lte(0)) { setFormError('환전 후 금액이 0보다 커야 합니다.'); return; }
    if (mode !== 'sell' && input.lt(result.rate)) { setFormError(`최소 주문 금액은 ${format(result.rate)}원입니다.`); return; }
    setSubmitError('');
    setConfirmation({ mode, currency, amount: input.toFixed(), result, key: previousKey || crypto.randomUUID(), storageKey });
  }
  async function submit() {
    if (!confirmation || submittingRef.current) return;
    submittingRef.current = true; setSubmitting(true); setSubmitError('');
    sessionStorage.setItem(confirmation.storageKey, confirmation.key);
    try {
      const response = await api<string>(orderRequest(confirmation.mode, confirmation.currency, confirmation.amount), {
        method: 'POST', headers: { 'Idempotency-Key': confirmation.key },
      });
      const orderId = response.match(/orderId=(\d+)/)?.[1];
      sessionStorage.removeItem(confirmation.storageKey);
      setMessage(`${labels[confirmation.mode]} 주문이 접수되었습니다.${orderId ? ` 주문번호 #${orderId}` : ''} 접수 금액은 오늘 한도의 예약액에 포함됩니다.`);
      setConfirmation(null); setRefresh(value => value + 1);
    } catch (e) {
      const error = e as ApiError;
      const uncertain = error.status === 0 || error.status >= 500;
      setSubmitError(uncertain ? '접수 결과를 확인하지 못했습니다. 다시 확인하면 같은 요청번호로 조회·재요청합니다.' : error.message);
    } finally { setSubmitting(false); submittingRef.current = false; }
  }

  return <main className="page dashboard">
    <div className="page-heading"><div><p className="eyebrow">EXCHANGE OVERVIEW</p><h1>오늘의 환율</h1><p className="muted">{dateLabel} <span className="dot-separator">·</span> 서울 기준</p></div><button className="button refresh-button" onClick={() => { setDemo(false); setRefresh(value => value + 1); }} disabled={loading}><RefreshCw size={15} className={loading ? 'spin' : ''} />새로고침</button></div>
    <div className="currency-bar"><div className="currency-tabs" role="tablist" aria-label="통화">
      {[{ code: 'USD', name: '미국 달러', flag: 'us' }, { code: 'JPY', name: '일본 엔', flag: 'jp' }].map(item => <button key={item.code} role="tab" aria-selected={currency === item.code} className={`currency-tab ${currency === item.code ? 'selected' : ''}`} onClick={() => { setCurrency(item.code); setFormError(''); }}><img src={`/flags/${item.flag}.png`} alt="" width="28" height="20" /><strong>{item.code}</strong><span>{item.name}</span><Check size={15} className="tab-check" /></button>)}
    </div><span className="unit-label">{rates?.unit || (currency === 'JPY' ? 100 : 1)} {currency} 기준 · KRW</span></div>

    <div className="dashboard-grid"><div className="market-column">
      <div className="rate-summary">
        <div><span className="stat-label"><ArrowDownLeft size={15} />가장 낮은 구매 환율</span><strong>{format(buy?.buyRate)}<small>원</small></strong><span className="stat-bank">{buy?.bankName || '—'}<span className="subtle-badge">전신환 매도</span></span></div>
        <div><span className="stat-label"><ArrowUpRight size={15} />가장 높은 판매 환율</span><strong>{format(sell?.sellRate)}<small>원</small></strong><span className="stat-bank">{sell?.bankName || '—'}<span className="subtle-badge">전신환 매입</span></span></div>
        <div className="bank-count"><span className="stat-label">비교 은행</span><strong>{bankRows.length || '—'}<small>곳</small></strong><span className="stat-bank">{demo ? '샘플 데이터' : latest ? `${latest.slice(11, 16)} 최근 고시` : '고시 정보 대기'}</span></div>
      </div>
      <section className="chart-section" aria-labelledby="chart-title"><div className="section-heading"><h2 id="chart-title">은행별 환율 추이</h2>{demo ? <span className="sample-label">샘플 · 실제 주문 불가</span> : <span className="live-label"><span />오늘 고시</span>}</div>
        <div className="chart-controls"><div className="segmented" aria-label="그래프 환율 기준">{(['buyRate', 'sellRate', 'baseRate'] as Metric[]).map(value => <button key={value} aria-pressed={metric === value} onClick={() => setMetric(value)} className={metric === value ? 'selected' : ''}>{metricLabels[value]}</button>)}</div><span className="muted">단위: 원</span></div>
        {loading ? <div className="chart-empty"><LoaderCircle size={26} className="spin" /><p>환율을 불러오는 중입니다.</p></div> : rates?.points.length ? <Suspense fallback={<div className="chart-empty">그래프 준비 중</div>}><RateChart rates={rates} metric={metric} /></Suspense> : <div className="chart-empty"><ChartNoAxesCombined size={36} strokeWidth={1.3} /><strong>{ratesError ? '환율을 불러오지 못했습니다' : '오늘 고시된 환율이 없습니다'}</strong><p>{ratesError || '새로운 고시가 등록되면 표시됩니다.'}</p><button className="text-button" onClick={() => setDemo(true)}>샘플 보기 <ArrowRight size={14} /></button></div>}
        <div className="chart-legend">{bankRows.map((bank, index) => <span key={bank.bankId}><i style={{ background: colors[index % colors.length] }} />{bank.bankName}</span>)}</div>
      </section>
      <section className="bank-section" aria-labelledby="bank-title"><div className="section-heading"><h2 id="bank-title">은행별 고시 환율</h2><span className="muted">최근 고시 기준</span></div><div className="table-scroll"><table><thead><tr><th>은행</th><th className={metric === 'buyRate' ? 'highlight-column' : ''}>구매 <small>전신환 매도율</small></th><th className={metric === 'sellRate' ? 'highlight-column' : ''}>판매 <small>전신환 매입율</small></th><th className={metric === 'baseRate' ? 'highlight-column' : ''}>매매기준율</th><th>고시 시각</th></tr></thead><tbody>{bankRows.map((bank, index) => <tr key={bank.bankId}><th><span className="bank-symbol" style={{ color: colors[index % colors.length], background: `${colors[index % colors.length]}12` }}>{bank.bankName.slice(0, 1)}</span>{bank.bankName}</th><td className={metric === 'buyRate' ? 'highlight-column' : ''}>{format(bank.buyRate)}</td><td className={metric === 'sellRate' ? 'highlight-column' : ''}>{format(bank.sellRate)}</td><td className={metric === 'baseRate' ? 'highlight-column' : ''}>{format(bank.baseRate)}</td><td className="time-cell">{bank.time.slice(11, 16)}</td></tr>)}{!bankRows.length && <tr><td colSpan={5} className="table-empty">{loading ? '조회 중' : '표시할 고시가 없습니다.'}</td></tr>}</tbody></table></div></section>
      <p className="market-footnote"><Clock3 size={13} />고시 시각에 따라 은행별 환율이 다를 수 있으며, 주문 시점의 적용 환율은 달라질 수 있습니다.</p>
    </div>

    <aside className="trade-column"><div className="trade-tool">
      <section className="limit-band"><div className="section-heading"><span>오늘의 환전 한도</span><ShieldCheck size={18} /></div><p className="limit-number"><strong>{summary ? format(summary.usedUsd, 2) : '—'}</strong><span>/ {format(summary?.limitUsd || 4000, 0)} USD</span></p><progress aria-label="오늘 사용 및 예약한 환전 한도" max={Number(summary?.limitUsd || 4000)} value={Number(summary?.usedUsd || 0)} /><div className="limit-caption"><span>사용·예약 포함</span><span>{summary ? `${format(summary.remainingUsd)} USD 남음` : summaryError ? '한도 확인 불가' : user ? '한도 조회 중' : '로그인 후 확인'}</span></div></section>
      <div className="trade-body"><div className="section-heading"><h2>환전하기</h2><ArrowLeftRight size={18} color="#8c859f" /></div>
        <div className="trade-tabs" role="group" aria-label="주문 종류"><button className={mode === 'buy' ? 'selected' : ''} aria-pressed={mode === 'buy'} onClick={() => changeMode('buy')}><ArrowDownLeft size={17} />구매</button><button className={mode === 'sell' ? 'selected' : ''} aria-pressed={mode === 'sell'} onClick={() => changeMode('sell')}><ArrowUpRight size={17} />판매</button></div>
        {mode === 'arbitrage' && <div className="strategy-active"><ChartNoAxesCombined size={16} />매매기준율 구매 + 판매</div>}
        <form onSubmit={prepare}>
          <label className="amount-label" htmlFor="amount">{mode === 'sell' ? '판매할 금액' : '사용할 금액'}</label><div className="amount-input"><input id="amount" aria-label="주문 금액" value={amount} onChange={event => { if (/^\d*\.?\d*$/.test(event.target.value)) { setAmount(event.target.value); setFormError(''); } }} inputMode="decimal" required maxLength={16} placeholder="0" /><span>{source}</span></div>
          <p className="available"><Wallet size={13} />주문 가능 <strong>{user && summary ? format(available || 0, source === 'KRW' ? 0 : rates?.scale ?? 2) : '—'} {source}</strong></p>
          <div className="conversion-line"><span /><ArrowDownLeft size={17} /><span /></div>
          <div className="receive-line"><span>예상 {mode === 'arbitrage' ? '최종 수령액' : '수령액'}</span><strong>{format(result?.receive, destination === 'KRW' ? 0 : rates?.scale ?? 2)} <small>{destination}</small></strong></div>
          <dl className="quote-details"><div><dt>적용 기준</dt><dd>{mode === 'arbitrage' ? '매매기준율' : mode === 'buy' ? '전신환 매도율' : '전신환 매입율'}</dd></div><div><dt>예상 거래 은행</dt><dd>{result?.bank || '—'}</dd></div><div><dt>서비스 수수료</dt><dd>{result ? `${format(result.fee, 0)} KRW` : '—'}</dd></div>{result?.profit != null && <div><dt>예상 차익</dt><dd className={result.profit.gte(0) ? 'positive' : 'negative'}>{format(result.profit, 0)} KRW</dd></div>}</dl>
          {summaryError && <p className="message error" role="alert">한도를 확인하지 못했습니다. 새로고침 후 다시 시도해 주세요.</p>}
          {formError && <p className="message error" role="alert">{formError}</p>}
          <button className="button primary wide order-button" disabled={demo || (Boolean(user) && (!result || !summary || !currentRates || loading))}>{user ? `${labels[mode]} 주문 확인` : '로그인하고 환전하기'}<ArrowRight size={17} /></button>
        </form>
        <p className="order-note">예상 금액이며 실제 체결 금액과 다를 수 있습니다.</p>
      </div>
      <div className="strategy"><span className="strategy-icon"><ChartNoAxesCombined size={20} /></span><div><strong>매매기준율 재테크</strong><p>구매와 판매를 한 번에</p></div><button className="icon-button" title="매매기준율 재테크 주문" aria-label="매매기준율 재테크 주문" onClick={() => changeMode('arbitrage')}><ArrowRight size={20} /></button></div>
      {mode === 'arbitrage' && <p className="risk-note"><CircleAlert size={14} />환율 변동과 반올림으로 손실이 발생할 수 있습니다.</p>}
    </div><div className="security-note"><BadgeCheck size={17} /><span>일일 한도는 서버 기준으로 확인합니다.</span></div></aside></div>
    {message && <div className="order-feedback message success" role="status"><Check size={18} /><span>{message}</span><button className="icon-button" aria-label="주문 접수 알림 닫기" onClick={() => setMessage('')}><X size={17} /></button></div>}
    {confirmation && <dialog ref={dialog} className="confirmation" onCancel={event => { event.preventDefault(); if (!submitting) setConfirmation(null); }}><div className="section-heading"><h2>{labels[confirmation.mode]} 주문 확인</h2><button className="icon-button" aria-label="주문 확인 닫기" disabled={submitting} onClick={() => setConfirmation(null)}><X size={20} /></button></div><p className="muted confirmation-subtitle">{confirmation.currency} · {confirmation.mode === 'arbitrage' ? '매매기준율 구매 + 판매' : confirmation.mode === 'buy' ? '전신환 매도율' : '전신환 매입율'}</p><dl className="quote-details"><div><dt>주문 금액</dt><dd>{format(confirmation.amount, confirmation.mode === 'sell' ? 2 : 0)} {confirmation.mode === 'sell' ? confirmation.currency : 'KRW'}</dd></div><div><dt>예상 수령액</dt><dd>{format(confirmation.result.receive, confirmation.mode === 'buy' ? rates?.scale ?? 2 : 0)} {confirmation.mode === 'buy' ? confirmation.currency : 'KRW'}</dd></div><div><dt>예상 수수료</dt><dd>{format(confirmation.result.fee, 0)} KRW</dd></div></dl><p className="confirmation-warning">접수 후 서버에서 정산합니다. 적용 환율과 최종 수령액은 달라질 수 있습니다.{confirmation.mode === 'arbitrage' ? ' 재테크 주문은 수익을 보장하지 않습니다.' : ''}</p>{submitError && <p className="message error" role="alert">{submitError}</p>}<button className="button primary wide" disabled={submitting} onClick={submit}>{submitting ? <LoaderCircle className="spin" size={18} /> : <Check size={18} />}{submitting ? '접수 중' : submitError ? '같은 요청으로 다시 확인' : '주문 접수'}</button></dialog>}
  </main>;
}
