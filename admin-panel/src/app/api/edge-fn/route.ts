import { NextRequest, NextResponse } from "next/server";
import { createServerClient } from "@supabase/ssr";

const SUPABASE_URL = "https://nzzvphhqbcscoetzfzkd.supabase.co";
const SUPABASE_ANON_KEY =
  "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im56enZwaGhxYmNzY29ldHpmemtkIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzEzMjI3OTYsImV4cCI6MjA4Njg5ODc5Nn0.31g9pCxm5AThy9xckctfWMHG7wrcmykIPepA_PMHDkQ";

// Whitelist of allowed edge functions the admin panel can call
const ALLOWED_FUNCTIONS = [
  "admin-portal-action",
  "manage-doctor",
  "list-all-doctors",
  "get-all-ratings",
  "get-audit-logs",
  "deauthorize-device",
  "generate-health-analytics",
];

export async function POST(req: NextRequest) {
  const { functionName, body } = await req.json();

  if (
    !functionName ||
    typeof functionName !== "string" ||
    !ALLOWED_FUNCTIONS.includes(functionName)
  ) {
    return NextResponse.json({ error: "Invalid function" }, { status: 400 });
  }

  // Get the user's session from cookies
  const supabase = createServerClient(SUPABASE_URL, SUPABASE_ANON_KEY, {
    cookies: {
      getAll() {
        return req.cookies.getAll();
      },
      setAll() {},
    },
  });

  const {
    data: { session },
  } = await supabase.auth.getSession();

  if (!session) {
    return NextResponse.json({ error: "Not authenticated" }, { status: 401 });
  }

  // Proxy to the edge function (server-side, no CORS issues)
  const res = await fetch(`${SUPABASE_URL}/functions/v1/${functionName}`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${SUPABASE_ANON_KEY}`,
      "X-Doctor-Token": session.access_token,
    },
    body: JSON.stringify(body ?? {}),
  });

  const data = await res.json().catch(() => null);

  return NextResponse.json(data, { status: res.status });
}
