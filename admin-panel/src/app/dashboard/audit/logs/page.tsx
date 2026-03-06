export const dynamic = "force-dynamic";

import { createAdminClient } from "@/lib/supabase/admin";

export default async function AuditLogExplorerPage() {
  const supabase = createAdminClient();

  const { data: logs } = await supabase
    .from("admin_logs")
    .select("*")
    .order("created_at", { ascending: false })
    .limit(50);

  return (
    <div>
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">Audit Log Explorer</h1>
        <p className="text-sm text-gray-400 mt-0.5">
          Browse and search all administrative actions
        </p>
      </div>

      <div className="bg-white rounded-xl border border-gray-100 shadow-sm overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-left">
            <thead>
              <tr className="border-b border-gray-100">
                <th className="px-5 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">
                  Timestamp
                </th>
                <th className="px-5 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">
                  Action
                </th>
                <th className="px-5 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">
                  Admin
                </th>
                <th className="px-5 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">
                  Target
                </th>
                <th className="px-5 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">
                  Details
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-50">
              {(logs ?? []).length === 0 ? (
                <tr>
                  <td
                    colSpan={5}
                    className="px-5 py-8 text-center text-sm text-gray-400"
                  >
                    No audit logs found.
                  </td>
                </tr>
              ) : (
                (logs ?? []).map((log) => {
                  const date = new Date(log.created_at);
                  const details = log.details as Record<string, unknown> | null;
                  const email = (details?.email as string) ?? "";

                  return (
                    <tr key={log.log_id} className="hover:bg-gray-50">
                      <td className="px-5 py-3 text-sm text-gray-600 whitespace-nowrap">
                        {date.toLocaleDateString("en-US", {
                          month: "short",
                          day: "numeric",
                          year: "numeric",
                        })}{" "}
                        {date.toLocaleTimeString("en-US", {
                          hour: "2-digit",
                          minute: "2-digit",
                        })}
                      </td>
                      <td className="px-5 py-3">
                        <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-gray-100 text-gray-700">
                          {log.action}
                        </span>
                      </td>
                      <td className="px-5 py-3 text-sm text-gray-600 truncate max-w-[200px]">
                        {log.admin_id?.slice(0, 8) ?? "—"}
                      </td>
                      <td className="px-5 py-3 text-sm text-gray-600">
                        {log.target_type ?? "—"}
                        {email && (
                          <span className="text-gray-400 ml-1">({email})</span>
                        )}
                      </td>
                      <td className="px-5 py-3 text-sm text-gray-400 truncate max-w-[250px]">
                        {details
                          ? JSON.stringify(details).slice(0, 80)
                          : "—"}
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
