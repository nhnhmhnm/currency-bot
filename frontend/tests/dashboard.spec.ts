import { test, expect, Page } from '@playwright/test';
import { sampleRates } from '../src/exchange';

async function dashboard(page: Page, options: { authenticated?: boolean; failRates?: boolean } = {}) {
  if (options.authenticated) await page.addInitScript(() => sessionStorage.setItem('currency-session', JSON.stringify({ accessToken: 'access', refreshToken: 'refresh' })));
  await page.route('**/api/**', async route => {
    const url = new URL(route.request().url());
    if (url.pathname.endsWith('/rates')) return options.failRates ? route.fulfill({ status: 503 }) : route.fulfill({ json: sampleRates(url.searchParams.get('currency') || 'USD') });
    if (url.pathname.endsWith('/me')) return route.fulfill({ json: { id: 2, name: '테스터', device: 'tester' } });
    if (url.pathname.endsWith('/summary')) return route.fulfill({ json: { usedUsd: 0, limitUsd: 4000, remainingUsd: 4000, wallets: [{ currency: 'KRW', balance: 1000000, available: 1000000 }, { currency: 'USD', balance: 200, available: 200 }, { currency: 'JPY', balance: 20000, available: 20000 }] } });
    return route.fulfill({ status: 404 });
  });
}

test('dashboard renders chart, local visual assets and responsive layout', async ({ page }, testInfo) => {
  await dashboard(page, { authenticated: true });
  const errors: string[] = [];
  page.on('pageerror', error => errors.push(error.message));
  await page.goto('/');
  await expect(page.locator('.recharts-line-curve')).toHaveCount(4);
  await expect(page.locator('.limit-number')).toContainText('0.00');
  await expect(page.getByRole('row')).toHaveCount(5);
  expect(await page.locator('.header-currency-tab img').evaluateAll(images => images.every(image => (image as HTMLImageElement).naturalWidth > 0))).toBe(true);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  expect(await page.locator('.recharts-line-curve').first().getAttribute('d')).toMatch(/^M/);
  await page.screenshot({ path: testInfo.outputPath('dashboard.png'), fullPage: true });
  const buyRate = page.getByRole('checkbox', { name: '전신환 매도율' });
  const sellRate = page.getByRole('checkbox', { name: '전신환 매입율' });
  const baseRate = page.getByRole('checkbox', { name: '매매기준율' });
  await expect(buyRate).toBeChecked();
  await sellRate.check();
  await expect(page.locator('.recharts-line-curve')).toHaveCount(8);
  await baseRate.check();
  await expect(page.locator('.recharts-line-curve')).toHaveCount(12);
  expect(await page.locator('.recharts-line-curve[stroke-dasharray="8 4"]').count()).toBe(3);
  expect(await page.locator('.recharts-line-curve[stroke-dasharray="2 4"]').count()).toBe(3);
  expect(await page.locator('.recharts-line-curve[stroke="var(--buy)"]').count()).toBe(4);
  expect(await page.locator('.recharts-line-curve[stroke="var(--sell)"]').count()).toBe(4);
  expect(await page.locator('.recharts-line-curve[stroke="var(--base)"]').count()).toBe(4);
  await buyRate.uncheck();
  await sellRate.uncheck();
  await baseRate.uncheck();
  await expect(page.getByText('표시할 환율을 선택해 주세요')).toBeVisible();
  await baseRate.check();
  await expect(page.locator('.recharts-line-curve')).toHaveCount(4);
  await page.getByRole('tab', { name: /JPY/ }).click();
  await expect(page.getByRole('tab', { name: /JPY/ })).toHaveAttribute('aria-selected', 'true');
  await expect(page.locator('.unit-label')).toContainText('100 JPY');
  await page.getByRole('button', { name: '판매', exact: true }).click();
  await expect(page.locator('.amount-input')).toContainText('JPY');
  await expect(page.locator('.receive-line')).toContainText('KRW');
  expect(errors).toEqual([]);
});

test('system theme switches surfaces and keeps rate colors distinct', async ({ page }, testInfo) => {
  await dashboard(page, { authenticated: true });
  await page.emulateMedia({ colorScheme: 'light' });
  await page.goto('/');
  await expect(page.locator('.recharts-line-curve')).toHaveCount(4);
  const colors = async () => page.evaluate(() => {
    const root = getComputedStyle(document.documentElement);
    return ['--background', '--surface', '--purple', '--buy', '--sell', '--base'].map(name => root.getPropertyValue(name).trim());
  });
  const light = await colors();
  await page.screenshot({ path: testInfo.outputPath('light.png'), fullPage: true });
  await page.emulateMedia({ colorScheme: 'dark' });
  const dark = await colors();
  await expect(page.locator('.confirmation')).toHaveCount(0);
  await page.screenshot({ path: testInfo.outputPath('dark.png'), fullPage: true });
  await page.getByRole('checkbox', { name: '전신환 매입율' }).check();
  await page.getByRole('checkbox', { name: '매매기준율' }).check();
  await expect(page.locator('.recharts-line-curve')).toHaveCount(12);
  await page.screenshot({ path: testInfo.outputPath('dark-all-rates.png'), fullPage: true });
  await page.getByRole('button', { name: '구매 주문 확인' }).click();
  await expect(page.getByRole('dialog')).toBeVisible();
  expect(await page.locator('.confirmation').evaluate(element => getComputedStyle(element).backgroundColor))
    .toBe(await page.locator('.trade-tool').evaluate(element => getComputedStyle(element).backgroundColor));
  await page.getByRole('button', { name: '주문 확인 닫기' }).click();
  await page.getByRole('button', { name: '로그아웃' }).click();
  await page.getByRole('button', { name: '로그인', exact: true }).click();
  expect(await page.locator('.auth-form input').first().evaluate(element => getComputedStyle(element).backgroundColor))
    .toBe(await page.locator('.site-header').evaluate(element => getComputedStyle(element).backgroundColor));
  expect(light.every((color, index) => color !== dark[index])).toBe(true);
  expect(new Set(light.slice(2)).size).toBe(4);
  expect(new Set(dark.slice(2)).size).toBe(4);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
});

test('buy, sell and arbitrage require confirmation and use separate endpoints', async ({ page }) => {
  await dashboard(page, { authenticated: true });
  const requests: { path: string; key: string | undefined; query: string }[] = [];
  await page.route('**/api/exchange/*?*', async route => {
    if (route.request().method() !== 'POST') return route.fallback();
    const url = new URL(route.request().url());
    requests.push({ path: url.pathname, key: route.request().headers()['idempotency-key'], query: url.search });
    return route.fulfill({ status: 202, contentType: 'text/plain', body: 'BUY order accepted. orderId=42' });
  });
  await page.goto('/');
  await page.getByRole('button', { name: '구매 주문 확인' }).click();
  expect(requests).toHaveLength(0);
  await expect(page.getByRole('dialog')).toContainText('전신환 매도율');
  await page.getByRole('button', { name: '주문 접수', exact: true }).click();
  await expect(page.getByRole('status')).toContainText('주문번호 #42');
  await page.getByRole('button', { name: '판매', exact: true }).click();
  await page.getByRole('button', { name: '판매 주문 확인' }).click();
  await expect(page.getByRole('dialog')).toContainText('전신환 매입율');
  await page.getByRole('button', { name: '주문 접수', exact: true }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await page.getByRole('button', { name: '매매기준율 재테크 주문', exact: true }).click();
  await page.getByRole('button', { name: '재테크 주문 확인' }).click();
  await expect(page.getByRole('dialog')).toContainText('수익을 보장하지 않습니다');
  await page.getByRole('button', { name: '주문 접수', exact: true }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  expect(requests.map(request => request.path)).toEqual(['/api/exchange/buy', '/api/exchange/sell', '/api/exchange/arbitrage']);
  expect(requests[0].query).toContain('toCurrencyCode=USD');
  expect(requests[1].query).toContain('fromCurrencyCode=USD');
  expect(requests[2].query).toContain('currencyCode=USD');
  expect(new Set(requests.map(request => request.key)).size).toBe(3);
  expect(requests.every(request => request.key)).toBe(true);
});

test('uncertain submission reuses its idempotency key', async ({ page }) => {
  await dashboard(page, { authenticated: true });
  const keys: string[] = [];
  await page.route('**/api/exchange/buy?*', async route => {
    keys.push(route.request().headers()['idempotency-key']);
    if (keys.length === 1) return route.abort();
    return route.fulfill({ status: 202, body: 'BUY order accepted. orderId=99' });
  });
  await page.goto('/');
  await page.getByRole('button', { name: '구매 주문 확인' }).click();
  await page.getByRole('button', { name: '주문 접수', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('접수 결과를 확인하지 못했습니다');
  await page.getByRole('button', { name: '같은 요청으로 다시 확인' }).click();
  await expect(page.getByRole('status')).toContainText('#99');
  expect(keys).toHaveLength(2);
  expect(keys[0]).toBe(keys[1]);
});

test('offline mode does not fabricate live data or permit sample trades', async ({ page }) => {
  await dashboard(page, { failRates: true });
  await page.goto('/');
  await expect(page.getByText('환율을 불러오지 못했습니다')).toBeVisible();
  await expect(page.locator('.limit-number')).toContainText('—');
  await page.getByRole('button', { name: '샘플 보기' }).click();
  await expect(page.getByText('샘플 · 실제 주문 불가')).toBeVisible();
  await expect(page.locator('.recharts-line-curve')).toHaveCount(4);
  await expect(page.getByRole('button', { name: '로그인하고 환전하기' })).toBeDisabled();
});

test('insufficient balance and fractional KRW cannot submit an order', async ({ page }) => {
  await dashboard(page, { authenticated: true });
  await page.goto('/');
  await page.getByLabel('주문 금액').fill('1000001');
  await page.getByRole('button', { name: '구매 주문 확인' }).click();
  await expect(page.getByRole('alert')).toContainText('잔액이 부족');
  await page.getByLabel('주문 금액').fill('100000.1');
  await page.getByRole('button', { name: '구매 주문 확인' }).click();
  await expect(page.getByRole('alert')).toContainText('소수점 0자리');
  await expect(page.getByRole('dialog')).toHaveCount(0);
});

test('small guest screen fits without page overflow and unknown quota stays unknown', async ({ page }) => {
  await dashboard(page, { failRates: true });
  await page.setViewportSize({ width: 320, height: 740 });
  await page.goto('/');
  await expect(page.getByText('환율을 불러오지 못했습니다')).toBeVisible();
  await expect(page.getByRole('button', { name: '회원가입', exact: true })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await expect(page.getByRole('tab', { name: /USD/ })).toBeVisible();
  await expect(page.getByRole('tab', { name: /JPY/ })).toBeVisible();
  await expect(page.locator('.limit-number')).toContainText('—');
});
