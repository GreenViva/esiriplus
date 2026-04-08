'use client';

import { useState, Suspense } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import Link from 'next/link';
import { Mail, Lock, Eye, EyeOff, LogIn } from 'lucide-react';
import { Button, Input, Card } from '@/components/ui';
import ForgotPasswordDialog from '@/components/ForgotPasswordDialog';
import { invokeEdgeFunction } from '@/lib/supabase';
import { useAuthStore } from '@/store/auth';
import type { Session } from '@/types';

export default function LoginPage() {
  return <Suspense><LoginContent /></Suspense>;
}

function LoginContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const showForgot = searchParams.get('forgot') === 'true';

  const { setSession } = useAuthStore();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  // Forgot password dialog
  const [forgotOpen, setForgotOpen] = useState(showForgot);

  async function handleLogin(e: React.FormEvent) {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      const raw = await invokeEdgeFunction<Record<string, unknown>>('login-doctor', {
        email: email.trim().toLowerCase(),
        password,
      });
      // Edge function returns snake_case — normalize to Session type
      const user = raw.user as Record<string, unknown> | undefined;
      setSession({
        accessToken: (raw.access_token ?? raw.accessToken) as string,
        refreshToken: (raw.refresh_token ?? raw.refreshToken) as string,
        expiresAt: typeof raw.expires_at === 'number'
          ? raw.expires_at * 1000
          : typeof raw.expiresAt === 'number'
            ? raw.expiresAt
            : Date.now() + 3600_000,
        user: {
          id: (user?.id ?? '') as string,
          fullName: (user?.full_name ?? user?.fullName ?? '') as string,
          phone: (user?.phone ?? '') as string,
          email: (user?.email ?? '') as string,
          role: 'doctor',
          isVerified: (user?.is_verified ?? user?.isVerified ?? false) as boolean,
        },
      });
      router.push('/dashboard');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Login failed. Please try again.');
    } finally {
      setLoading(false);
    }
  }

  // (Forgot password dialog rendered below)

  return (
    <div className="pt-4">
      <Card className="p-6">
        {/* Header */}
        <div className="text-center mb-6">
          <div className="w-14 h-14 mx-auto rounded-full bg-[var(--brand-teal)]/10 flex items-center justify-center mb-3">
            <LogIn size={24} className="text-[var(--brand-teal)]" />
          </div>
          <h1 className="text-xl font-bold text-black">Doctor Sign In</h1>
          <p className="text-sm text-black mt-1">
            Access your eSIRI Plus dashboard
          </p>
        </div>

        <form onSubmit={handleLogin} className="space-y-4">
          <Input
            label="Email Address"
            type="email"
            placeholder="doctor@example.com"
            icon={<Mail size={18} />}
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
          />

          <div className="relative">
            <Input
              label="Password"
              type={showPassword ? 'text' : 'password'}
              placeholder="Enter your password"
              icon={<Lock size={18} />}
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
            <button
              type="button"
              onClick={() => setShowPassword(!showPassword)}
              className="absolute right-3 top-[38px] text-gray-400 hover:text-gray-600"
              tabIndex={-1}
            >
              {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
            </button>
          </div>

          {error && (
            <p className="text-sm text-[var(--error-red)] text-center">{error}</p>
          )}

          <Button type="submit" fullWidth loading={loading} size="lg">
            Sign In
          </Button>
        </form>

        <div className="mt-4 text-center">
          <button
            type="button"
            onClick={() => setForgotOpen(true)}
            className="text-sm font-medium text-[var(--brand-teal)]"
          >
            Forgot password?
          </button>
        </div>

        <ForgotPasswordDialog
          open={forgotOpen}
          onClose={() => setForgotOpen(false)}
          prefillEmail={email}
        />

        <div className="flex items-center gap-4 my-5">
          <div className="flex-1 h-px bg-[var(--card-border)]" />
          <span className="text-xs text-[var(--subtitle-grey)]">New here?</span>
          <div className="flex-1 h-px bg-[var(--card-border)]" />
        </div>

        <Link href="/register">
          <Button variant="outline" fullWidth>
            Create Doctor Account
          </Button>
        </Link>
      </Card>
    </div>
  );
}
