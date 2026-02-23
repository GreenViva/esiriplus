import { createServerClient } from "@supabase/ssr";
import { NextResponse, type NextRequest } from "next/server";

export async function middleware(request: NextRequest) {
  let supabaseResponse = NextResponse.next({ request });

  const supabase = createServerClient(
    process.env.NEXT_PUBLIC_SUPABASE_URL!,
    process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY!,
    {
      cookies: {
        getAll() {
          return request.cookies.getAll();
        },
        setAll(cookiesToSet: { name: string; value: string; options?: Record<string, unknown> }[]) {
          cookiesToSet.forEach(({ name, value }) =>
            request.cookies.set(name, value),
          );
          supabaseResponse = NextResponse.next({ request });
          cookiesToSet.forEach(({ name, value, options }) =>
            supabaseResponse.cookies.set(name, value, options),
          );
        },
      },
    },
  );

  const {
    data: { user },
  } = await supabase.auth.getUser();

  if (!user) {
    const url = request.nextUrl.clone();
    url.pathname = "/login";
    return NextResponse.redirect(url);
  }

  // Check user has an admin-panel role
  const { data: roles } = await supabase
    .from("user_roles")
    .select("role_name")
    .eq("user_id", user.id);

  const userRoles = (roles ?? []).map((r) => r.role_name);
  const allowedRoles = ["admin", "hr", "finance", "audit"];
  const hasAccess = userRoles.some((r) => allowedRoles.includes(r));

  if (!hasAccess) {
    const url = request.nextUrl.clone();
    url.pathname = "/login";
    url.searchParams.set("error", "access_denied");
    return NextResponse.redirect(url);
  }

  // Route-based role enforcement
  const pathname = request.nextUrl.pathname;

  // Define which roles can access which routes
  const routeRules: { prefix: string; roles: string[] }[] = [
    { prefix: "/dashboard/users", roles: ["admin"] },
    { prefix: "/dashboard/doctors", roles: ["admin", "hr"] },
    { prefix: "/dashboard/payments", roles: ["admin", "finance"] },
    { prefix: "/dashboard/analytics", roles: ["admin", "finance"] },
    { prefix: "/dashboard/hr/audit", roles: ["admin", "hr", "audit"] },
    { prefix: "/dashboard/hr", roles: ["admin", "hr"] },
  ];

  for (const rule of routeRules) {
    if (pathname.startsWith(rule.prefix)) {
      const canAccess = userRoles.some((r) => rule.roles.includes(r));
      if (!canAccess) {
        // Redirect to the appropriate dashboard for their role
        const url = request.nextUrl.clone();
        if (userRoles.includes("hr")) {
          url.pathname = "/dashboard/hr";
        } else if (userRoles.includes("finance")) {
          url.pathname = "/dashboard/payments";
        } else if (userRoles.includes("audit")) {
          url.pathname = "/dashboard/hr/audit";
        } else {
          url.pathname = "/dashboard";
        }
        return NextResponse.redirect(url);
      }
      break;
    }
  }

  return supabaseResponse;
}

export const config = {
  matcher: ["/dashboard/:path*"],
};
