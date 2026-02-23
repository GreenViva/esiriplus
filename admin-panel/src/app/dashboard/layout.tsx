import { redirect } from "next/navigation";
import { createClient } from "@/lib/supabase/server";
import Sidebar from "@/components/Sidebar";
import Topbar from "@/components/Topbar";
import type { PortalRole } from "@/lib/types/database";

export default async function DashboardLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  const supabase = await createClient();
  const {
    data: { user },
  } = await supabase.auth.getUser();

  if (!user) redirect("/login");

  const { data: roles } = await supabase
    .from("user_roles")
    .select("role_name")
    .eq("user_id", user.id);

  // Deterministic role priority: admin > hr > finance > audit
  const rolePriority = ["admin", "hr", "finance", "audit"];
  const userRoles = (roles ?? []).map((r) => r.role_name);
  const role = (rolePriority.find((r) => userRoles.includes(r)) ?? userRoles[0] ?? "admin") as PortalRole;
  const fullName = (user.user_metadata?.full_name as string) ?? "";

  return (
    <div className="flex h-screen overflow-hidden">
      <Sidebar email={user.email ?? ""} fullName={fullName} role={role} />
      <div className="flex-1 flex flex-col min-w-0">
        <Topbar email={user.email ?? ""} role={role} />
        <main className="flex-1 overflow-y-auto p-6 bg-gray-50">{children}</main>
      </div>
    </div>
  );
}
