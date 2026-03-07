import { createClient, type User } from "@supabase/supabase-js";

export function createAdminClient() {
  return createClient(
    process.env.NEXT_PUBLIC_SUPABASE_URL!,
    process.env.SUPABASE_SERVICE_ROLE_KEY!,
    {
      auth: { autoRefreshToken: false, persistSession: false },
      global: { fetch: (...args: Parameters<typeof fetch>) => fetch(...args) },
    },
  );
}

/**
 * Paginate through all auth users. Supabase `listUsers` returns at most
 * 1000 per page, so we loop until we have them all.
 */
export async function fetchAllAuthUsers(
  supabase: ReturnType<typeof createAdminClient>,
): Promise<User[]> {
  const allUsers: User[] = [];
  const perPage = 1000;
  let page = 1;
  let hasMore = true;

  while (hasMore) {
    const { data: { users }, error } = await supabase.auth.admin.listUsers({
      page,
      perPage,
    });

    if (error || !users || users.length === 0) {
      hasMore = false;
    } else {
      allUsers.push(...users);
      hasMore = users.length >= perPage;
      page++;
    }
  }

  return allUsers;
}
