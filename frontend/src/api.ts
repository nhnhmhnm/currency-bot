export class ApiError extends Error {
  constructor(message: string, public status = 0) { super(message); }
}

type Tokens = { accessToken: string; refreshToken: string };
let tokens: Tokens | null = null;
try { tokens = JSON.parse(sessionStorage.getItem('currency-session') || 'null'); } catch { /* Invalid sessions are discarded. */ }
let refreshing: Promise<boolean> | null = null;

export function setSession(value: Tokens | null) {
  tokens = value;
  if (value) sessionStorage.setItem('currency-session', JSON.stringify(value));
  else sessionStorage.removeItem('currency-session');
}
export const hasSession = () => Boolean(tokens?.accessToken);

async function refresh() {
  if (!tokens?.refreshToken) return false;
  try {
    const res = await fetch('/api/auth/reissue', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken: tokens.refreshToken }), signal: AbortSignal.timeout(15000),
    });
    if (!res.ok) return false;
    setSession(await res.json());
    return true;
  } catch { return false; }
}

export async function api<T>(path: string, init: RequestInit = {}, retry = true): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`/api${path}`, {
      ...init, signal: init.signal || AbortSignal.timeout(15000),
      headers: {
        ...(init.body ? { 'Content-Type': 'application/json' } : {}),
        ...(tokens ? { Authorization: `Bearer ${tokens.accessToken}` } : {}), ...init.headers,
      },
    });
  } catch { throw new ApiError('서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.'); }
  if (response.status === 401 && tokens && retry && !path.startsWith('/auth/')) {
    refreshing ??= refresh().finally(() => { refreshing = null; });
    if (await refreshing) return api<T>(path, init, false);
    setSession(null);
    window.dispatchEvent(new Event('session-expired'));
  }
  const raw = await response.text();
  let data: unknown = raw;
  try { data = JSON.parse(raw); } catch { /* Order acceptance is a text response. */ }
  if (!response.ok) {
    const message = typeof data === 'object' && data && 'message' in data ? String(data.message) : '';
    throw new ApiError(response.status >= 500 ? '서버에서 요청을 처리하지 못했습니다.' : message || '요청을 처리할 수 없습니다.', response.status);
  }
  return data as T;
}

export type User = { id: number; name: string; device: string };
export async function login(device: string, password: string) {
  const session = await api<Tokens>('/auth/login', { method: 'POST', body: JSON.stringify({ device, password }) });
  setSession(session);
  return api<User>('/user/me');
}
