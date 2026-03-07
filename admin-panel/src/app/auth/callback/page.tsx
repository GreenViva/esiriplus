"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { createClient } from "@/lib/supabase/client";

export default function AuthCallbackPage() {
  const router = useRouter();

  useEffect(() => {
    const supabase = createClient();

    // Handle the OAuth code exchange or password reset from URL hash
    supabase.auth.onAuthStateChange(async (event) => {
      if (event === "SIGNED_IN" || event === "PASSWORD_RECOVERY") {
        const { data: { user } } = await supabase.auth.getUser();

        if (user) {
          const { data: roles } = await supabase
            .from("user_roles")
            .select("role_name")
            .eq("user_id", user.id);

          const allowedRoles = ["admin", "hr", "finance", "audit"];
          const validRoles = (roles ?? [])
            .map((r) => r.role_name)
            .filter((r) => allowedRoles.includes(r));

          if (validRoles.length > 0) {
            // Set signed httpOnly cookie via middleware API
            await fetch("/api/auth/set-roles", {
              method: "POST",
              headers: { "Content-Type": "application/json" },
              body: JSON.stringify({ roles: validRoles }),
            });

            const userRole = validRoles[0];
            const dest = userRole === "hr" ? "/dashboard/hr" : "/dashboard";
            router.replace(dest);
            return;
          }

          // No portal role
          await supabase.auth.signOut();
          router.replace("/login?error=access_denied");
          return;
        }

        router.replace("/login?error=auth_failed");
      }
    });
  }, [router]);

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-50">
      <p className="text-gray-400">Completing sign in...</p>
    </div>
  );
}
