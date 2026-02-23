"use client";

import { useState, useEffect } from "react";
import { useRouter } from "next/navigation";
import { checkAdminExists, setupInitialAdmin } from "@/lib/actions";

function ShieldIcon() {
  return (
    <div className="w-14 h-14 rounded-full bg-brand-teal/10 flex items-center justify-center">
      <svg
        width="28"
        height="28"
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
  );
}

export default function SetupPage() {
  const router = useRouter();
  const [checking, setChecking] = useState(true);
  const [alreadySetUp, setAlreadySetUp] = useState(false);

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    checkAdminExists().then(({ exists }) => {
      setAlreadySetUp(exists);
      setChecking(false);
    });
  }, []);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError("");

    if (password.length < 8) {
      setError("Password must be at least 8 characters.");
      return;
    }

    setLoading(true);

    const result = await setupInitialAdmin({
      email,
      password,
      full_name: "Admin",
    });

    if (result.error) {
      setError(result.error);
      setLoading(false);
      return;
    }

    router.push("/login?setup=success");
  }

  if (checking) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-50">
        <p className="text-gray-400">Checking setup status...</p>
      </div>
    );
  }

  if (alreadySetUp) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-50">
        <div className="w-full max-w-md px-4 text-center">
          <div className="flex justify-center mb-6">
            <ShieldIcon />
          </div>
          <h1 className="text-2xl font-bold text-gray-900 mb-2">
            Already Set Up
          </h1>
          <p className="text-gray-400 mb-6">
            An admin account has already been created.
          </p>
          <a
            href="/login"
            className="inline-block w-full py-3.5 bg-brand-teal text-white rounded-xl text-sm font-semibold hover:bg-brand-teal-dark transition-colors"
          >
            Go to Login
          </a>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50">
      <div className="w-full max-w-md px-4">
        <div className="bg-white rounded-2xl shadow-sm p-8">
          {/* Shield icon */}
          <div className="flex justify-center mb-5">
            <ShieldIcon />
          </div>

          {/* Title */}
          <h1 className="text-xl font-bold text-gray-900 text-center">
            Create Admin Account
          </h1>
          <p className="text-gray-400 text-sm text-center mt-1 mb-6">
            Set up the initial admin account
          </p>

          <form onSubmit={handleSubmit} className="space-y-4">
            {/* Email */}
            <div>
              <label
                htmlFor="email"
                className="block text-sm font-medium text-gray-700 mb-1.5"
              >
                Admin Email
              </label>
              <div className="relative">
                <span className="absolute inset-y-0 left-0 flex items-center pl-3.5 pointer-events-none text-gray-400">
                  <svg
                    width="16"
                    height="16"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="1.5"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                  >
                    <rect x="2" y="4" width="20" height="16" rx="2" />
                    <path d="M22 4l-10 8L2 4" />
                  </svg>
                </span>
                <input
                  id="email"
                  type="email"
                  required
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  className="w-full pl-10 pr-4 py-3 bg-blue-50 border-0 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-brand-teal"
                  placeholder="admin@esiriplus.com"
                />
              </div>
            </div>

            {/* Password */}
            <div>
              <label
                htmlFor="password"
                className="block text-sm font-medium text-gray-700 mb-1.5"
              >
                Password
              </label>
              <div className="relative">
                <span className="absolute inset-y-0 left-0 flex items-center pl-3.5 pointer-events-none text-gray-400">
                  <svg
                    width="16"
                    height="16"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="1.5"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                  >
                    <rect x="3" y="11" width="18" height="11" rx="2" ry="2" />
                    <path d="M7 11V7a5 5 0 0 1 10 0v4" />
                  </svg>
                </span>
                <input
                  id="password"
                  type="password"
                  required
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  className="w-full pl-10 pr-4 py-3 bg-blue-50 border-0 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-brand-teal"
                />
              </div>
            </div>

            {error && (
              <p className="text-sm text-red-600 text-center">{error}</p>
            )}

            {/* Submit button */}
            <button
              type="submit"
              disabled={loading}
              className="w-full flex items-center justify-center gap-2 py-3.5 bg-brand-teal text-white rounded-xl text-sm font-semibold hover:bg-brand-teal-dark disabled:opacity-50 transition-colors"
            >
              <svg
                width="16"
                height="16"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="2"
                strokeLinecap="round"
                strokeLinejoin="round"
              >
                <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
                <path d="M9 12l2 2 4-4" />
              </svg>
              {loading ? "Creating account..." : "Create Admin Account"}
            </button>
          </form>

          {/* Back link */}
          <div className="text-center mt-5">
            <a
              href="/login"
              className="text-sm text-gray-500 hover:text-gray-700 inline-flex items-center gap-1"
            >
              <span>&larr;</span> Back to Admin Login
            </a>
          </div>

          {/* Footer note */}
          <div className="mt-6 pt-5 border-t border-gray-100">
            <p className="text-xs text-gray-400 text-center">
              Set up the initial admin account
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}
