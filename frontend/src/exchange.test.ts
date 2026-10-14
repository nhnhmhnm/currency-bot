import { describe, expect, it } from 'vitest';
import { best, estimate, minimumOrderAmount, orderRequest, Rates, sampleRates } from './exchange';

const rates: Rates = { date: '2026-10-08', currency: 'USD', unit: 1, scale: 2, points: [
  { bankId: 1, bankName: 'A', time: '2026-10-08T10:00:00', buyRate: 1400, sellRate: 1380, baseRate: 1390 },
  { bankId: 2, bankName: 'B', time: '2026-10-08T10:01:00', buyRate: 1410, sellRate: 1390, baseRate: 1400 },
] };
describe('exchange quote previews', () => {
  it('uses customer buy TTS and customer sell TTB', () => {
    expect(estimate(rates, 'buy', '100000')?.receive.toFixed()).toBe('71.42');
    expect(estimate(rates, 'buy', '100000')?.fee.toFixed()).toBe('3');
    expect(estimate(rates, 'sell', '100')?.receive.toFixed()).toBe('138995');
    expect(estimate(rates, 'sell', '100')?.bank).toBe('B');
  });
  it('honors JPY 100-unit quotation and currency scale', () => {
    const jpy = { ...rates, currency: 'JPY', unit: 100, scale: 0, points: [{ ...rates.points[0], buyRate: 950, sellRate: 930 }] };
    expect(estimate(jpy, 'buy', '100000')?.receive.toFixed()).toBe('10526');
    expect(estimate(jpy, 'sell', '100')?.receive.toFixed()).toBe('929');
  });
  it('defaults to one quoted unit and zero without a usable rate', () => {
    const fractional = { ...rates, points: [{ ...rates.points[0], buyRate: 1400.25, baseRate: 1390.75 }] };
    expect(minimumOrderAmount(fractional, 'buy')).toBe('1401');
    expect(minimumOrderAmount(fractional, 'sell')).toBe('1');
    expect(minimumOrderAmount(fractional, 'arbitrage')).toBe('1000000');
    expect(minimumOrderAmount(fractional, 'arbitrage', { currency: 'USD', amountKrw: 1000000, buyRate: 1390.75, sellRate: 1390.75, foreignAmount: 719, feeKrw: 30, estimatedReceiveKrw: 999970, estimatedProfitKrw: -30 })).toBe('1000000');
    const jpy = { ...fractional, currency: 'JPY', unit: 100, scale: 0, points: [{ ...fractional.points[0], buyRate: 950.12 }] };
    expect(minimumOrderAmount(jpy, 'buy')).toBe('951');
    expect(minimumOrderAmount(jpy, 'sell')).toBe('100');
    expect(minimumOrderAmount(null, 'buy')).toBe('0');
    expect(minimumOrderAmount({ ...rates, points: [] }, 'sell')).toBe('0');
  });
  it('uses base rates for both arbitrage legs without a guaranteed profit', () => {
    const quote = estimate(rates, 'arbitrage', '100000');
    expect(quote?.bank).toBe('A → B');
    expect(quote?.receive.toFixed()).toBe('100712');
    expect(quote?.fee.toFixed()).toBe('4');
  });
  it('excludes stale rates and handles empty or invalid values', () => {
    const old = { ...rates.points[0], time: '2026-10-08T09:00:00', buyRate: 100 };
    expect(best({ ...rates, points: [old, ...rates.points] }, 'buyRate', 'min')?.buyRate).toBe(1400);
    for (const amount of ['', '0', '-1', '1e3', 'abc']) expect(estimate(rates, 'buy', amount)).toBeNull();
    expect(estimate({ ...rates, points: [] }, 'buy', '100')).toBeNull();
  });
  it('builds each endpoint with correct currency and amount semantics', () => {
    expect(orderRequest('buy', 'USD', '100000')).toBe('/exchange/buy?toCurrencyCode=USD&fromAmount=100000');
    expect(orderRequest('sell', 'JPY', '100.00')).toBe('/exchange/sell?fromCurrencyCode=JPY&fromAmount=100');
    expect(orderRequest('arbitrage', 'USD', '100000')).toContain('currencyCode=USD');
    expect(orderRequest('buy', 'USD', '100000', '1400.00')).toContain('expectedRate=1400');
    expect(orderRequest('arbitrage', 'USD', '100000', '1390', '1400')).toContain('expectedCounterRate=1400');
    expect(sampleRates('JPY').unit).toBe(100);
  });
});
