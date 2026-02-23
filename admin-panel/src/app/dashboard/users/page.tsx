export const dynamic = "force-dynamic";

import Link from "next/link";
import { createAdminClient, fetchAllAuthUsers } from "@/lib/supabase/admin";
import { createClient } from "@/lib/supabase/server";
import { formatDate } from "@/lib/utils";
import type { UserRole } from "@/lib/types/database";
import UserActionButtons from "./UserActionButtons";

export default async function UsersPage() {
  const supabase = createAdminClient();
  const serverClient = await createClient();
  const {
    data: { user: currentUser },
  } = await serverClient.auth.getUser();

  const { data: roles } = await supabase
    .from("user_roles")
    .select("*")
    .order("created_at", { ascending: true });

  const roleList = (roles ?? []) as UserRole[];

  // Fetch auth users to check ban status (paginated)
  const authUsers = await fetchAllAuthUsers(supabase);

  const authMap = new Map(
    authUsers.map((u) => [
      u.id,
      {
        email: u.email ?? "",
        isBanned:
          !!u.banned_until && new Date(u.banned_until) > new Date(),
      },
    ])
  );

  const roleBadge: Record<string, { bg: string; text: string }> = {
    admin: { bg: "bg-purple-50", text: "text-purple-700" },
    hr: { bg: "bg-blue-50", text: "text-blue-700" },
    finance: { bg: "bg-amber-50", text: "text-amber-700" },
    audit: { bg: "bg-gray-100", text: "text-gray-700" },
  };

  return (
    <div>
      {/* Header */}
      <div className="flex items-start justify-between mb-6">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-brand-teal/10 flex items-center justify-center flex-shrink-0">
            <svg
              className="h-5 w-5 text-brand-teal"
              fill="none"
              viewBox="0 0 24 24"
              stroke="currentColor"
              strokeWidth={1.5}
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                d="M9 12.75L11.25 15 15 9.75m-3-7.036A11.959 11.959 0 013.598 6 11.99 11.99 0 003 9.749c0 5.592 3.824 10.29 9 11.623 5.176-1.332 9-6.03 9-11.622 0-1.31-.21-2.571-.598-3.751h-.152c-3.196 0-6.1-1.248-8.25-3.285z"
              />
            </svg>
          </div>
          <div>
            <h1 className="text-2xl font-bold text-gray-900">Role Management</h1>
            <p className="text-sm text-gray-400 mt-0.5">
              Create, assign, and revoke user roles
            </p>
          </div>
        </div>

        <Link
          href="/dashboard/users/create"
          className="inline-flex items-center gap-2 px-5 py-2.5 bg-brand-teal text-white rounded-xl text-sm font-medium hover:bg-brand-teal-dark transition-colors"
        >
          <svg
            className="h-4 w-4"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
            strokeWidth={1.5}
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              d="M19 7.5v3m0 0v3m0-3h3m-3 0h-3m-2.25-4.125a3.375 3.375 0 11-6.75 0 3.375 3.375 0 016.75 0zM4 19.235v-.11a6.375 6.375 0 0112.75 0v.109A12.318 12.318 0 0110.374 21c-2.331 0-4.512-.645-6.374-1.766z"
            />
          </svg>
          Create User
        </Link>
      </div>

      {/* Roles table */}
      <div className="bg-white rounded-xl border border-gray-100 shadow-sm overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-gray-100">
                <th className="text-left px-5 py-3.5 text-xs font-semibold text-brand-teal uppercase tracking-wider">
                  Email
                </th>
                <th className="text-left px-5 py-3.5 text-xs font-semibold text-brand-teal uppercase tracking-wider">
                  Role
                </th>
                <th className="text-left px-5 py-3.5 text-xs font-semibold text-brand-teal uppercase tracking-wider">
                  Assigned
                </th>
                <th className="text-left px-5 py-3.5 text-xs font-semibold text-brand-teal uppercase tracking-wider">
                  Actions
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-50">
              {roleList.map((r) => {
                const isCurrentUser = r.user_id === currentUser?.id;
                const auth = authMap.get(r.user_id);
                const isBanned = auth?.isBanned ?? false;
                const userEmail = auth?.email ?? r.user_id.slice(0, 8) + "...";
                const badge = roleBadge[r.role_name] ?? {
                  bg: "bg-gray-100",
                  text: "text-gray-700",
                };

                return (
                  <tr
                    key={`${r.user_id}-${r.role_name}`}
                    className="hover:bg-gray-50/50 transition-colors"
                  >
                    <td className="px-5 py-4">
                      <div className="flex items-center gap-2">
                        <span className="text-gray-900 text-sm">
                          {userEmail}
                        </span>
                        {isCurrentUser && (
                          <span className="inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-semibold bg-gray-100 text-gray-600 border border-gray-200">
                            You
                          </span>
                        )}
                      </div>
                    </td>
                    <td className="px-5 py-4">
                      <span
                        className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold ${badge.bg} ${badge.text}`}
                      >
                        {r.role_name.toUpperCase()}
                      </span>
                    </td>
                    <td className="px-5 py-4 text-gray-500">
                      {formatDate(r.created_at)}
                    </td>
                    <td className="px-5 py-4">
                      {isCurrentUser ? (
                        <span className="text-xs text-gray-400">&mdash;</span>
                      ) : (
                        <UserActionButtons
                          userId={r.user_id}
                          isBanned={isBanned}
                          roleName={r.role_name}
                        />
                      )}
                    </td>
                  </tr>
                );
              })}
              {roleList.length === 0 && (
                <tr>
                  <td
                    colSpan={4}
                    className="px-5 py-12 text-center text-gray-400"
                  >
                    No roles assigned yet.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
