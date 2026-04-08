'use client';

import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { Session, User, UserRole, PatientSession } from '@/types';

interface AuthState {
  session: Session | null;
  patientSession: PatientSession | null;
  isLoading: boolean;
  _hasHydrated: boolean;
  setSession: (session: Session | null) => void;
  setPatientSession: (ps: PatientSession | null) => void;
  setLoading: (loading: boolean) => void;
  logout: () => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      session: null,
      patientSession: null,
      isLoading: true,
      _hasHydrated: false,
      setSession: (session) => set({ session, isLoading: false }),
      setPatientSession: (ps) => set({ patientSession: ps }),
      setLoading: (loading) => set({ isLoading: loading }),
      logout: () => set({ session: null, patientSession: null }),
    }),
    {
      name: 'esiri-auth',
      partialize: (state) => ({
        session: state.session,
        patientSession: state.patientSession,
      }),
      onRehydrateStorage: () => {
        return (state) => {
          if (state) {
            state._hasHydrated = true;
            state.isLoading = false;
          }
        };
      },
    },
  ),
);

// Helper selectors
export const useAuthToken = () => useAuthStore((s) => s.session?.accessToken ?? null);
export const useAuthRole = () => useAuthStore((s) => s.session?.user?.role ?? null);
export const useHasHydrated = () => useAuthStore((s) => s._hasHydrated);
