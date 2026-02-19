// _shared/supabase.ts
// Returns a service-role Supabase client (bypasses RLS for backend ops).

import { createClient, SupabaseClient } from "https://esm.sh/@supabase/supabase-js@2";

let _client: SupabaseClient | null = null;

export function getServiceClient(): SupabaseClient {
  if (!_client) {
    _client = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
      { auth: { persistSession: false } }
    );
  }
  return _client;
}
