import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { bankDashes, banks, Metric, rateColors, Rates } from './exchange';

const rateNames: Record<Metric, string> = { buyRate: '전신환 매도', sellRate: '전신환 매입', baseRate: '매매기준' };

export default function RateChart({ rates, metrics }: { rates: Rates; metrics: Metric[] }) {
  const series = banks(rates);
  const rows = new Map<string, Record<string, string | number>>();
  for (const point of rates.points) {
    const time = point.time.slice(11, 16);
    if (!rows.has(time)) rows.set(time, { time });
    for (const metric of metrics) {
      if (point[metric] != null) rows.get(time)![`${point.bankId}:${metric}`] = Number(point[metric]);
    }
  }
  const data = [...rows.values()].sort((a, b) => String(a.time).localeCompare(String(b.time)));
  return <div className="chart" role="img" aria-label={`${rates.currency} 오늘의 은행별 환율 추이 그래프`}>
    <ResponsiveContainer width="100%" height="100%" minWidth={0}>
      <LineChart data={data} margin={{ top: 14, right: 14, left: 0, bottom: 0 }} accessibilityLayer>
        <CartesianGrid stroke="var(--chart-grid)" strokeDasharray="3 4" vertical={false} />
        <XAxis dataKey="time" tickLine={false} axisLine={false} minTickGap={38} tick={{ fill: 'var(--muted)', fontSize: 11 }} dy={10} />
        <YAxis domain={['auto', 'auto']} width={64} tickLine={false} axisLine={false} tick={{ fill: 'var(--muted)', fontSize: 11 }} tickFormatter={value => Number(value).toLocaleString('ko-KR', { maximumFractionDigits: 1 })} />
        <Tooltip contentStyle={{ background: 'var(--surface)', border: '1px solid var(--line)', color: 'var(--text)', borderRadius: 6, fontSize: 12 }} formatter={value => `${Number(value).toLocaleString('ko-KR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}원`} />
        {series.flatMap((bank, index) => metrics.map(metric => <Line key={`${bank.bankId}:${metric}`} dataKey={`${bank.bankId}:${metric}`} name={`${bank.bankName} · ${rateNames[metric]}`} type="linear" stroke={rateColors[metric]} strokeDasharray={bankDashes[index % bankDashes.length]} strokeWidth={2.2} dot={data.length <= 2 ? { r: 3 } : false} activeDot={{ r: 4 }} connectNulls isAnimationActive={false} />))}
      </LineChart>
    </ResponsiveContainer>
  </div>;
}
