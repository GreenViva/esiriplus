"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { createPortalUserWithPassword } from "@/lib/actions";

const roleOptions = [
  { value: "admin", label: "Admin", enabled: true },
  { value: "hr", label: "HR", enabled: true },
  { value: "finance", label: "Finance", enabled: true },
  { value: "audit", label: "Audit", enabled: true },
];

export default function CreateUserPage() {
  const router = useRouter();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");
  const [form, setForm] = useState({
    email: "",
    password: "",
    role: "admin",
  });

  function update(field: string, value: string) {
    setForm((prev) => ({ ...prev, [field]: value }));
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError("");
    setSuccess("");

    if (!form.email.trim()) {
      setError("Email is required.");
      return;
    }
    if (form.password.length < 8) {
      setError("Password must be at least 8 characters.");
      return;
    }

    setLoading(true);

    const result = await createPortalUserWithPassword({
      email: form.email,
      password: form.password,
      role: form.role,
    });

    if (result.error) {
      setError(result.error);
    } else {
      setSuccess("User created successfully. They can now sign in with their email and password.");
      setForm({ email: "", password: "", role: "admin" });
    }
    setLoading(false);
  }

  return (
    <div className="max-w-lg">
      {/* Back link */}
      <button
        onClick={() => router.push("/dashboard/users")}
        className="flex items-center gap-1.5 text-sm text-gray-500 hover:text-gray-700 transition-colors mb-4"
      >
        <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M10.5 19.5L3 12m0 0l7.5-7.5M3 12h18" />
        </svg>
        Back to Role Management
      </button>

      <h1 className="text-2xl font-bold text-gray-900 mb-1">Create User</h1>
      <p className="text-sm text-gray-400 mb-6">
        Assign a role with sign-in credentials
      </p>

      <div className="bg-white rounded-xl border border-gray-100 shadow-sm p-6">
        <form onSubmit={handleSubmit} className="space-y-5">
          {/* Email */}
          <div>
            <label htmlFor="email" className="block text-sm font-medium text-gray-700 mb-1.5">
              Email
            </label>
            <input
              id="email"
              type="email"
              required
              value={form.email}
              onChange={(e) => update("email", e.target.value)}
              placeholder="user@example.com"
              className="w-full px-3.5 py-2.5 rounded-xl border border-gray-200 bg-white text-sm text-gray-900 placeholder:text-gray-400 focus:outline-none focus:ring-2 focus:ring-brand-teal/30 focus:border-brand-teal transition-colors"
            />
          </div>

          {/* Password */}
          <div>
            <label htmlFor="password" className="block text-sm font-medium text-gray-700 mb-1.5">
              Password
            </label>
            <input
              id="password"
              type="password"
              required
              value={form.password}
              onChange={(e) => update("password", e.target.value)}
              placeholder="Minimum 8 characters"
              className="w-full px-3.5 py-2.5 rounded-xl border border-gray-200 bg-white text-sm text-gray-900 placeholder:text-gray-400 focus:outline-none focus:ring-2 focus:ring-brand-teal/30 focus:border-brand-teal transition-colors"
            />
          </div>

          {/* Role selector */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-2">
              Role
            </label>
            <div className="grid grid-cols-2 gap-2">
              {roleOptions.map((opt) => (
                <button
                  key={opt.value}
                  type="button"
                  disabled={!opt.enabled}
                  onClick={() => opt.enabled && update("role", opt.value)}
                  className={`px-4 py-3 rounded-xl border text-sm font-semibold text-left transition-colors ${
                    form.role === opt.value && opt.enabled
                      ? "border-brand-teal bg-brand-teal/5 text-brand-teal ring-2 ring-brand-teal/20"
                      : opt.enabled
                      ? "border-gray-200 bg-white text-gray-700 hover:border-gray-300"
                      : "border-gray-100 bg-gray-50 text-gray-400 opacity-50 cursor-not-allowed"
                  }`}
                >
                  {opt.label}
                </button>
              ))}
            </div>
          </div>

          {/* Messages */}
          {error && (
            <div className="flex items-center gap-2 px-4 py-3 rounded-xl bg-red-50 border border-red-100">
              <svg className="h-4 w-4 text-red-500 flex-shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v3.75m9-.75a9 9 0 11-18 0 9 9 0 0118 0zm-9 3.75h.008v.008H12v-.008z" />
              </svg>
              <p className="text-sm text-red-700">{error}</p>
            </div>
          )}
          {success && (
            <div className="flex items-center gap-2 px-4 py-3 rounded-xl bg-green-50 border border-green-100">
              <svg className="h-4 w-4 text-green-500 flex-shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M9 12.75L11.25 15 15 9.75M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
              <p className="text-sm text-green-700">{success}</p>
            </div>
          )}

          {/* Actions */}
          <div className="flex gap-3 pt-1">
            <button
              type="submit"
              disabled={loading}
              className="flex-1 inline-flex items-center justify-center gap-2 px-4 py-2.5 bg-brand-teal text-white rounded-xl text-sm font-medium hover:bg-brand-teal-dark transition-colors disabled:opacity-50"
            >
              {loading ? (
                <>
                  <svg className="animate-spin h-4 w-4" viewBox="0 0 24 24" fill="none">
                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
                  </svg>
                  Creating...
                </>
              ) : (
                "Create User"
              )}
            </button>
            <button
              type="button"
              onClick={() => router.push("/dashboard/users")}
              className="px-4 py-2.5 bg-white text-gray-700 border border-gray-200 rounded-xl text-sm font-medium hover:bg-gray-50 transition-colors"
            >
              Cancel
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
