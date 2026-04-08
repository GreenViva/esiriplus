import { createClient, SupabaseClient } from '@supabase/supabase-js';

const supabaseUrl = process.env.NEXT_PUBLIC_SUPABASE_URL!;
const supabaseAnonKey = process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY!;

// ── Token refresh ────────────────────────────────────────────────────────────

const REFRESH_MARGIN_MS = 5 * 60 * 1000; // refresh 5 min before expiry
let refreshInFlight: Promise<boolean> | null = null;

/**
 * Checks if the patient token is about to expire and refreshes it.
 * Returns true if the token is valid (either still fresh or successfully refreshed).
 */
export async function ensureFreshToken(): Promise<boolean> {
  // Lazy import to avoid circular dependency
  const { useAuthStore } = await import('@/store/auth');
  const state = useAuthStore.getState();
  const session = state.session;
  const patientSession = state.patientSession;

  if (!session?.accessToken || !session.expiresAt) return false;

  const timeLeft = session.expiresAt - Date.now();
  if (timeLeft > REFRESH_MARGIN_MS) return true; // still fresh

  // Deduplicate concurrent refresh calls
  if (refreshInFlight) return refreshInFlight;

  refreshInFlight = (async () => {
    try {
      if (!session.refreshToken) return false;

      // Doctor tokens: use Supabase Auth built-in refresh
      if (session.user?.role === 'doctor') {
        return refreshDoctorToken(session);
      }

      // Patient tokens: use custom edge function
      if (!patientSession?.sessionId) return false;

      const res = await fetch(`${supabaseUrl}/functions/v1/refresh-patient-session`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${supabaseAnonKey}`,
        },
        body: JSON.stringify({
          session_id: patientSession.sessionId,
          refresh_token: session.refreshToken,
        }),
      });

      if (!res.ok) return false;

      const data = await res.json();
      if (!data.access_token) return false;

      useAuthStore.getState().setSession({
        ...session,
        accessToken: data.access_token,
        refreshToken: data.refresh_token,
        expiresAt: new Date(data.expires_at).getTime(),
        user: session.user,
      });

      console.log('[TokenRefresh] Patient token refreshed successfully');
      return true;
    } catch (err) {
      console.warn('[TokenRefresh] Refresh failed:', err);
      return false;
    } finally {
      refreshInFlight = null;
    }
  })();

  return refreshInFlight;
}

/**
 * Refresh a doctor's Supabase Auth JWT using the built-in refresh endpoint.
 */
async function refreshDoctorToken(session: { accessToken: string; refreshToken: string; user: { id: string; fullName: string; phone: string; email?: string; role: string; isVerified: boolean } }): Promise<boolean> {
  try {
    // Use Supabase Auth REST API directly to avoid creating extra GoTrueClient instances
    const res = await fetch(`${supabaseUrl}/auth/v1/token?grant_type=refresh_token`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'apikey': supabaseAnonKey,
      },
      body: JSON.stringify({
        refresh_token: session.refreshToken,
      }),
    });

    if (!res.ok) return false;

    const data = await res.json();
    if (!data.access_token) return false;

    const { useAuthStore } = await import('@/store/auth');
    useAuthStore.getState().setSession({
      accessToken: data.access_token,
      refreshToken: data.refresh_token,
      expiresAt: data.expires_at
        ? data.expires_at * 1000
        : Date.now() + (data.expires_in ? data.expires_in * 1000 : 3600_000),
      user: session.user,
    });

    console.log('[TokenRefresh] Doctor token refreshed successfully');
    return true;
  } catch (err) {
    console.warn('[TokenRefresh] Doctor refresh failed:', err);
    return false;
  }
}

/** Unauthenticated client — for public operations. */
export const supabase = createClient(supabaseUrl, supabaseAnonKey, {
  auth: {
    persistSession: false,
    autoRefreshToken: false,
    storageKey: 'sb-anon',
  },
});

/**
 * Creates an authenticated Supabase client using the patient/doctor JWT.
 * For patients: sends token via X-Patient-Token header (custom HS256 JWT).
 * For doctors: sends token via Authorization Bearer (Supabase Auth JWT).
 */
/**
 * Creates an authenticated Supabase client for direct DB queries (PostgREST).
 * Both patient and doctor JWTs go in Authorization — PostgREST only reads that header.
 * Patient JWT has role:"authenticated" and is signed with SUPABASE_JWT_SECRET, so PostgREST accepts it.
 * Edge function calls use invokeEdgeFunction() which handles X-Patient-Token separately.
 */
// Cache key uses token length + last 8 chars (not the full token) to avoid leaking secrets
let _cachedAuthClient: { key: string; client: SupabaseClient } | null = null;

function tokenCacheKey(token: string): string {
  return `${token.length}:${token.slice(-8)}`;
}

export function getAuthClient(token: string, _role: 'patient' | 'doctor' = 'patient'): SupabaseClient {
  const key = tokenCacheKey(token);
  if (_cachedAuthClient && _cachedAuthClient.key === key) {
    return _cachedAuthClient.client;
  }
  const client = createClient(supabaseUrl, supabaseAnonKey, {
    auth: {
      persistSession: false,
      autoRefreshToken: false,
      storageKey: `sb-auth-${key}`,
    },
    global: {
      headers: {
        Authorization: `Bearer ${token}`,
      },
    },
  });
  // Set the JWT for Realtime WebSocket so RLS policies work on subscriptions
  client.realtime.setAuth(token);
  _cachedAuthClient = { key, client };
  return client;
}

/**
 * Creates a Supabase client with the JWT in Authorization header.
 * Required for Storage uploads — RLS policies need `role: "authenticated"`.
 * The patient JWT has this role and is signed with SUPABASE_JWT_SECRET.
 */
export function getStorageClient(token: string): SupabaseClient {
  return createClient(supabaseUrl, supabaseAnonKey, {
    auth: { persistSession: false, autoRefreshToken: false },
    global: { headers: { Authorization: `Bearer ${token}` } },
  });
}

/**
 * Calls a Supabase Edge Function via direct fetch.
 *
 * The auth system expects:
 *   - Authorization: Bearer <anon_key>  (to bypass Supabase gateway)
 *   - X-Patient-Token: <jwt>           (for patient auth)
 *   - X-Doctor-Token: <jwt>            (for doctor auth)
 *
 * @param functionName - Edge function name
 * @param body - JSON body
 * @param token - Optional user JWT
 * @param role - 'patient' or 'doctor' (determines which header to use)
 */
/**
 * Calls a Supabase Edge Function via server-side proxy to avoid CORS issues.
 */
export async function invokeEdgeFunction<T = unknown>(
  functionName: string,
  body: Record<string, unknown>,
  token?: string,
  role: 'patient' | 'doctor' = 'patient',
): Promise<T> {
  const res = await fetch('/api/edge-fn', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ functionName, body, token, role }),
  });

  const data = await res.json();

  if (!res.ok) {
    throw new Error(data?.error || data?.message || `Edge function error (${res.status})`);
  }

  return data as T;
}
