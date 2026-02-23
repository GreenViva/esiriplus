import { NextResponse } from "next/server";
import { createClient } from "@/lib/supabase/server";

export async function GET(request: Request) {
  const { searchParams, origin } = new URL(request.url);
  const code = searchParams.get("code");

  if (code) {
    const supabase = await createClient();
    const { error } = await supabase.auth.exchangeCodeForSession(code);

    if (!error) {
      // Verify user has a portal role before granting access
      const {
        data: { user },
      } = await supabase.auth.getUser();

      if (user) {
        const { data: roles } = await supabase
          .from("user_roles")
          .select("role_name")
          .eq("user_id", user.id);

        const allowedRoles = ["admin", "hr", "finance", "audit"];
        const userRole = roles?.find((r) => allowedRoles.includes(r.role_name))?.role_name;

        if (userRole) {
          const dest = userRole === "hr" ? "/dashboard/hr" : "/dashboard";
          return NextResponse.redirect(`${origin}${dest}`);
        }
      }

      // No portal role — sign out and redirect
      await supabase.auth.signOut();
      return NextResponse.redirect(`${origin}/login?error=access_denied`);
    }
  }

  return NextResponse.redirect(`${origin}/login?error=auth_failed`);
}
