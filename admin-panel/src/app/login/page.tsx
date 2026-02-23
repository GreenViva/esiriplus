"use client";

import { Suspense, useState, useEffect } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import { createClient } from "@/lib/supabase/client";
import { checkAdminExists } from "@/lib/actions";

export default function LoginPage() {
  return (
    <Suspense>
      <LoginForm />
    </Suspense>
  );
}

function LoginForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState(
    searchParams.get("error") === "access_denied"
      ? "Access denied. You need an admin, HR, finance, or audit role to sign in."
      : "",
  );
  const [successMsg] = useState(
    searchParams.get("setup") === "success"
      ? "Admin account created. You can now sign in."
      : "",
  );
  const [loading, setLoading] = useState(false);
  const [noAdmin, setNoAdmin] = useState(false);
  const [showForgot, setShowForgot] = useState(false);
  const [resetEmail, setResetEmail] = useState("");
  const [resetMsg, setResetMsg] = useState("");
  const [resetLoading, setResetLoading] = useState(false);

  useEffect(() => {
    checkAdminExists().then(({ exists }) => {
      if (!exists) setNoAdmin(true);
    });
  }, []);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError("");
    setLoading(true);

    const supabase = createClient();

    const { error: signInError } = await supabase.auth.signInWithPassword({
      email,
      password,
    });

    if (signInError) {
      setError(signInError.message);
      setLoading(false);
      return;
    }

    // Check role
    const {
      data: { user },
    } = await supabase.auth.getUser();

    if (!user) {
      setError("Authentication failed.");
      setLoading(false);
      return;
    }

    const { data: roles } = await supabase
      .from("user_roles")
      .select("role_name")
      .eq("user_id", user.id);

    const allowedRoles = ["admin", "hr", "finance", "audit"];
    const hasAccess = roles?.some((r) => allowedRoles.includes(r.role_name));

    if (!hasAccess) {
      await supabase.auth.signOut();
      setError("Access denied. You need an admin, HR, finance, or audit role to sign in.");
      setLoading(false);
      return;
    }

    // Route to the correct dashboard based on role
    const userRole = roles?.find((r) =>
      allowedRoles.includes(r.role_name)
    )?.role_name;

    if (userRole === "hr") {
      router.push("/dashboard/hr");
    } else if (userRole === "finance") {
      router.push("/dashboard/payments");
    } else if (userRole === "audit") {
      router.push("/dashboard/hr/audit");
    } else {
      router.push("/dashboard");
    }
    router.refresh();
  }

  async function handleForgotPassword(e: React.FormEvent) {
    e.preventDefault();
    if (!resetEmail.trim()) return;
    setResetLoading(true);
    setResetMsg("");
    const supabase = createClient();
    const { error: resetError } = await supabase.auth.resetPasswordForEmail(resetEmail, {
      redirectTo: `${window.location.origin}/auth/callback`,
    });
    if (resetError) {
      setResetMsg(resetError.message);
    } else {
      setResetMsg("If an account exists with that email, a reset link has been sent.");
    }
    setResetLoading(false);
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50">
      <div className="w-full max-w-md px-4">
        {/* Shield icon */}
        <div className="flex justify-center mb-6">
          <div className="w-16 h-16 rounded-full bg-brand-teal/10 flex items-center justify-center">
            <svg
              width="32"
              height="32"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="1.5"
              strokeLinecap="round"
              strokeLinejoin="round"
              className="text-brand-teal"
            >
              <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
            </svg>
          </div>
        </div>

        {/* Title */}
        <h1 className="text-2xl font-bold text-gray-900 text-center">
          Admin Portal
        </h1>
        <p className="text-gray-400 text-center mt-1 mb-4">
          Admin, HR, Finance &amp; Audit access only
        </p>

        {/* Setup link */}
        {noAdmin && (
          <p className="text-center mb-6">
            <a
              href="/setup"
              className="text-brand-teal font-medium hover:underline"
            >
              First time? Create admin account
            </a>
          </p>
        )}

        <form onSubmit={handleSubmit} className="space-y-5">
          <div>
            <label
              htmlFor="email"
              className="block text-sm font-medium text-gray-700 mb-1.5"
            >
              Email
            </label>
            <input
              id="email"
              type="email"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              className="w-full px-4 py-3 bg-blue-50 border-0 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-brand-teal"
              placeholder="admin@esiriplus.com"
            />
          </div>

          <div>
            <div className="flex items-center justify-between mb-1.5">
              <label
                htmlFor="password"
                className="block text-sm font-medium text-gray-700"
              >
                Password
              </label>
              <button
                type="button"
                onClick={() => setShowForgot(true)}
                className="text-xs text-brand-teal hover:underline"
              >
                Forgot password?
              </button>
            </div>
            <input
              id="password"
              type="password"
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="w-full px-4 py-3 bg-blue-50 border-0 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-brand-teal"
            />
          </div>

          {successMsg && (
            <p className="text-sm text-green-600 text-center">{successMsg}</p>
          )}

          {error && (
            <p className="text-sm text-red-600 text-center">{error}</p>
          )}

          <button
            type="submit"
            disabled={loading}
            className="w-full py-3.5 bg-brand-teal text-white rounded-xl text-sm font-semibold hover:bg-brand-teal-dark disabled:opacity-50 transition-colors"
          >
            {loading ? "Signing in..." : "Sign In"}
          </button>
        </form>

        {/* Forgot Password */}
        {showForgot && (
          <div className="mt-6 p-4 bg-white border border-gray-200 rounded-xl">
            <h2 className="text-sm font-semibold text-gray-900 mb-2">
              Reset Password
            </h2>
            <form onSubmit={handleForgotPassword} className="space-y-3">
              <input
                type="email"
                required
                value={resetEmail}
                onChange={(e) => setResetEmail(e.target.value)}
                placeholder="Enter your email"
                className="w-full px-4 py-2.5 bg-blue-50 border-0 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-brand-teal"
              />
              {resetMsg && (
                <p className="text-xs text-gray-600">{resetMsg}</p>
              )}
              <div className="flex gap-2">
                <button
                  type="submit"
                  disabled={resetLoading}
                  className="flex-1 py-2 bg-brand-teal text-white rounded-xl text-sm font-medium hover:bg-brand-teal-dark disabled:opacity-50 transition-colors"
                >
                  {resetLoading ? "Sending..." : "Send Reset Link"}
                </button>
                <button
                  type="button"
                  onClick={() => { setShowForgot(false); setResetMsg(""); }}
                  className="px-3 py-2 border border-gray-200 rounded-xl text-sm text-gray-600 hover:bg-gray-50 transition-colors"
                >
                  Cancel
                </button>
              </div>
            </form>
          </div>
        )}
      </div>
    </div>
  );
}
