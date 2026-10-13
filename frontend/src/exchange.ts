import Decimal from 'decimal.js';

Decimal.set({ precision: 40 });
export type Mode = 'buy' | 'sell' | 'arbitrage';
export type Metric = 'buyRate' | 'sellRate' | 'baseRate';
export type RatePoint = { bankId: number; bankName: string; time: string; buyRate: string | number | null; sellRate: string | number | null; baseRate: string | number | null };
export type Rates = { date: string; currency: string; unit: string | number; scale: number; points: RatePoint[] };
export type Summary = { usedUsd: string | number; limitUsd: string | number; remainingUsd: string | number; wallets: { currency: string; balance: string | number; available: string | number }[] };
export const rateColors: Record<Metric, string> = { buyRate: 'var(--buy)', sellRate: 'var(--sell)', baseRate: 'var(--base)' };
export const bankDashes = [undefined, '8 4', '2 4', '10 3 2 3'];
export const format = (value: Decimal.Value | null | undefined, digits = 2) => value == null ? '—' : new Decimal(value).toNumber().toLocaleString('ko-KR', { maximumFractionDigits: digits, minimumFractionDigits: digits });
export const seoulDate = () => new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date());
const timestamp = (time: string) => Date.parse(`${time}+09:00`);

export function banks(rates: Rates | null): RatePoint[] {
  const map = new Map<number, RatePoint>();
  for (const point of rates?.points || []) if (!map.has(point.bankId) || timestamp(point.time) >= timestamp(map.get(point.bankId)!.time)) map.set(point.bankId, point);
  return [...map.values()].sort((a, b) => a.bankId - b.bankId);
}

export function best(rates: Rates | null, metric: Metric, direction: 'min' | 'max') {
  const points = rates?.points || [];
  const latest = Math.max(...points.map(point => timestamp(point.time)));
  return points.filter(point => timestamp(point.time) >= latest - 300000 && point[metric] != null && new Decimal(point[metric]!).gt(0))
    .sort((a, b) => new Decimal(a[metric]!).cmp(b[metric]!) * (direction === 'min' ? 1 : -1))[0];
}

export function minimumOrderAmount(rates: Rates | null, mode: Mode): string {
  if (!rates || !new Decimal(rates.unit).gt(0)) return '0';
  const quote = best(rates, mode === 'arbitrage' ? 'baseRate' : mode === 'buy' ? 'buyRate' : 'sellRate', mode === 'sell' ? 'max' : 'min');
  if (!quote) return '0';
  return mode === 'sell' ? new Decimal(rates.unit).toFixed() : new Decimal(quote[mode === 'arbitrage' ? 'baseRate' : 'buyRate']!).ceil().toFixed(0);
}

export function estimate(rates: Rates | null, mode: Mode, amount: string) {
  if (!rates || !/^\d+(\.\d+)?$/.test(amount) || !new Decimal(amount).gt(0)) return null;
  const input = new Decimal(amount);
  const buy = best(rates, mode === 'arbitrage' ? 'baseRate' : 'buyRate', 'min');
  const sell = best(rates, mode === 'arbitrage' ? 'baseRate' : 'sellRate', 'max');
  const commission = (value: Decimal) => value.mul('0.00003').toDecimalPlaces(0, Decimal.ROUND_UP);
  const normalized = (value: Decimal.Value) => new Decimal(value).div(rates.unit).toDecimalPlaces(10, Decimal.ROUND_DOWN);
  if (mode === 'sell') {
    if (!sell) return null;
    const gross = input.mul(normalized(sell.sellRate!)).toDecimalPlaces(0, Decimal.ROUND_DOWN);
    const fee = commission(gross);
    return { receive: gross.minus(fee), fee, bank: sell.bankName, rate: sell.sellRate!, profit: null };
  }
  if (!buy || (mode === 'arbitrage' && !sell)) return null;
  const fee = mode === 'buy' ? commission(input) : new Decimal(0);
  const rate = mode === 'buy' ? buy.buyRate! : buy.baseRate!;
  const fx = input.minus(fee).div(normalized(rate)).toDecimalPlaces(10, Decimal.ROUND_DOWN).toDecimalPlaces(rates.scale, Decimal.ROUND_DOWN);
  const receive = mode === 'buy' ? fx : fx.mul(normalized(sell!.baseRate!)).toDecimalPlaces(0, Decimal.ROUND_DOWN);
  return { receive, fee, bank: mode === 'buy' ? buy.bankName : `${buy.bankName} → ${sell!.bankName}`, rate, profit: mode === 'arbitrage' ? receive.minus(input) : null };
}

export function orderRequest(mode: Mode, currency: string, amount: string) {
  const parameter = mode === 'buy' ? 'toCurrencyCode' : mode === 'sell' ? 'fromCurrencyCode' : 'currencyCode';
  return `/exchange/${mode}?${new URLSearchParams({ [parameter]: currency, fromAmount: new Decimal(amount).toFixed() })}`;
}

export function sampleRates(currency: string): Rates {
  const date = seoulDate();
  const names = ['하나은행', '신한은행', '우리은행', 'KB국민은행'];
  const points: RatePoint[] = [];
  names.forEach((bankName, bank) => {
    for (let step = 0; step <= 28; step++) {
      const hour = 9 + Math.floor(step / 4);
      const minute = step % 4 * 15;
      const base = (currency === 'USD' ? 1372 : 927) + Math.sin(step * .4 + bank * .7) * 2.5 + step * .12 + bank * 1.5;
      points.push({ bankId: bank + 1, bankName, time: `${date}T${String(hour).padStart(2, '0')}:${String(minute).padStart(2, '0')}:00`, baseRate: base.toFixed(2), buyRate: (base + 12).toFixed(2), sellRate: (base - 12).toFixed(2) });
    }
  });
  return { date, currency, unit: currency === 'JPY' ? 100 : 1, scale: currency === 'JPY' ? 0 : 2, points };
}
