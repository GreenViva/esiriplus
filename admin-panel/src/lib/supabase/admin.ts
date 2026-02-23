import { createClient, type User } from "@supabase/supabase-js";

export function createAdminClient() {
  return createClient(
    process.env.NEXT_PUBLIC_SUPABASE_URL!,
    process.env.SUPABASE_SERVICE_ROLE_KEY!,
    { auth: { autoRefreshToken: false, persistSession: false } },
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

  // eslint-disable-next-line no-constant-condition
  while (true) {
    const { data: { users }, error } = await supabase.auth.admin.listUsers({
      page,
      perPage,
    });

    if (error || !users || users.length === 0) break;
    allUsers.push(...users);
    if (users.length < perPage) break;
    page++;
  }

  return allUsers;
}
