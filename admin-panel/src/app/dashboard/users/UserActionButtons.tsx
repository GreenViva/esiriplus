"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { suspendUser, unsuspendUser, deleteUserRole } from "@/lib/actions";

interface Props {
  userId: string;
  isBanned: boolean;
  roleName: string;
}

export default function UserActionButtons({ userId, isBanned, roleName }: Props) {
  const router = useRouter();
  const [loading, setLoading] = useState<string | null>(null);
  const [error, setError] = useState("");

  async function handleToggle() {
    setLoading("suspend");
    setError("");
    const result = isBanned
      ? await unsuspendUser(userId)
      : await suspendUser(userId);
    if (result.error) setError(result.error);
    else router.refresh();
    setLoading(null);
  }

  async function handleDeleteRole() {
    if (!confirm(`Remove the "${roleName}" role from this user?`)) return;
    setLoading("delete");
    setError("");
    const result = await deleteUserRole(userId, roleName);
    if (result.error) setError(result.error);
    else router.refresh();
    setLoading(null);
  }

  return (
    <div className="flex items-center gap-3">
      <button
        onClick={handleToggle}
        disabled={loading !== null}
        className={`text-sm font-medium disabled:opacity-50 ${
          isBanned
            ? "text-green-600 hover:text-green-700"
            : "text-red-600 hover:text-red-700"
        }`}
      >
        {loading === "suspend" ? "..." : isBanned ? "Unsuspend" : "Suspend"}
      </button>
      <button
        onClick={handleDeleteRole}
        disabled={loading !== null}
        className="text-sm font-medium text-gray-500 hover:text-red-600 disabled:opacity-50 transition-colors"
        title="Remove role"
      >
        {loading === "delete" ? "..." : "Remove"}
      </button>
      {error && <p className="text-xs text-red-600 mt-1">{error}</p>}
    </div>
  );
}
