import { test, expect } from '@playwright/test';

test('login uses the backend credentials and logout clears the session', async ({ page }) => {
  await page.route('**/api/**', async route => {
    const path = new URL(route.request().url()).pathname;
    if (path === '/api/auth/login') {
      expect(route.request().postDataJSON()).toEqual({ device: 'tester', password: 'password123' });
      return route.fulfill({ json: { accessToken: 'access', refreshToken: 'refresh' } });
    }
    if (path === '/api/user/me') return route.fulfill({ json: { id: 2, name: '테스터', device: 'tester' } });
    if (path === '/api/auth/logout') return route.fulfill({ status: 204 });
    return route.fulfill({ status: 503 });
  });
  await page.goto('/');
  await page.getByRole('button', { name: '로그인', exact: true }).click();
  await page.getByLabel('아이디', { exact: true }).fill('tester');
  await page.getByLabel('비밀번호', { exact: true }).fill('password123');
  await page.locator('form').getByRole('button', { name: '로그인', exact: true }).click();
  await expect(page.getByText('테스터 님')).toBeVisible();
  await page.getByRole('button', { name: '로그아웃' }).click();
  await expect(page.getByRole('button', { name: '회원가입', exact: true })).toBeVisible();
  expect(await page.evaluate(() => sessionStorage.getItem('currency-session'))).toBeNull();
});

test('signup validates confirmation and creates only a general user', async ({ page }) => {
  let submissions = 0;
  await page.route('**/api/**', async route => {
    if (route.request().url().endsWith('/user/signup')) {
      submissions++;
      expect(route.request().postDataJSON().type).toBe('GENERAL');
      return route.fulfill({ status: 201, json: { id: 3 } });
    }
    return route.fulfill({ status: 503 });
  });
  await page.goto('/');
  await page.getByRole('button', { name: '회원가입', exact: true }).click();
  await page.getByLabel('이름', { exact: true }).fill('새 사용자');
  await page.getByLabel('아이디', { exact: true }).fill('new-user');
  await page.getByLabel('휴대전화').fill('01012345678');
  await page.getByLabel('비밀번호', { exact: true }).fill('password123');
  await page.getByLabel('비밀번호 확인').fill('password456');
  await page.locator('form').getByRole('button', { name: '회원가입', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('일치하지 않습니다');
  expect(submissions).toBe(0);
  await page.getByLabel('비밀번호 확인').fill('password123');
  await page.locator('form').getByRole('button', { name: '회원가입', exact: true }).click();
  await expect(page.getByRole('status')).toContainText('회원가입이 완료');
  expect(submissions).toBe(1);
});
