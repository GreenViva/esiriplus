'use client';

import { useMemo } from 'react';
import { getAuthClient } from '@/lib/supabase';
import { useAuthStore } from '@/store/auth';

/**
 * Returns an authenticated Supabase client using the current user's JWT.
 * Automatically detects patient vs doctor role for correct header usage.
 */
export function useSupabase() {
  const token = useAuthStore((s) => s.session?.accessToken);
  const role = useAuthStore((s) => s.session?.user?.role);

  return useMemo(() => {
    if (!token) return null;
    return getAuthClient(token, role === 'doctor' ? 'doctor' : 'patient');
  }, [token, role]);
}

/**
 * Returns the current auth token for edge function calls.
 */
export function useAuthToken() {
  return useAuthStore((s) => s.session?.accessToken ?? null);
}
