"use client";

import { useState } from "react";
import { suspendUser, unsuspendUser, deleteUserRole } from "@/lib/adminApi";

interface Props {
  userId: string;
  isBanned: boolean;
  roleName: string;
  onRefresh?: () => void;
}

export default function UserActionButtons({ userId, isBanned, roleName, onRefresh }: Props) {
  const [loading, setLoading] = useState<string | null>(null);
  const [error, setError] = useState("");
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [success, setSuccess] = useState("");

  async function handleToggle() {
    setLoading("suspend");
    setError("");
    setSuccess("");
    const result = isBanned
      ? await unsuspendUser(userId)
      : await suspendUser(userId);
    if (result.error) setError(result.error);
    else {
      setSuccess(isBanned ? "User unsuspended" : "User suspended");
      onRefresh?.();
    }
    setLoading(null);
  }

  async function handleDeleteRole() {
    setLoading("delete");
    setError("");
    setSuccess("");
    const result = await deleteUserRole(userId, roleName);
    if (result.error) setError(result.error);
    else {
      setSuccess("Role removed");
      setConfirmDelete(false);
      onRefresh?.();
    }
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
      {confirmDelete ? (
        <span className="flex items-center gap-2">
          <span className="text-xs text-gray-500">Remove &ldquo;{roleName}&rdquo;?</span>
          <button
            onClick={handleDeleteRole}
            disabled={loading !== null}
            className="text-xs font-medium text-red-600 hover:text-red-700 disabled:opacity-50"
          >
            {loading === "delete" ? "..." : "Yes"}
          </button>
          <button
            onClick={() => setConfirmDelete(false)}
            disabled={loading !== null}
            className="text-xs font-medium text-gray-500 hover:text-gray-700 disabled:opacity-50"
          >
            No
          </button>
        </span>
      ) : (
        <button
          onClick={() => setConfirmDelete(true)}
          disabled={loading !== null}
          className="text-sm font-medium text-gray-500 hover:text-red-600 disabled:opacity-50 transition-colors"
          title="Remove role"
        >
          Remove
        </button>
      )}
      {error && <p className="text-xs text-red-600 mt-1">{error}</p>}
      {success && <p className="text-xs text-green-600 mt-1">{success}</p>}
    </div>
  );
}
