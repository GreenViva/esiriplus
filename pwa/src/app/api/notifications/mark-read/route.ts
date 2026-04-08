import { NextRequest, NextResponse } from 'next/server';

const SUPABASE_URL = 'https://nzzvphhqbcscoetzfzkd.supabase.co';
const SUPABASE_ANON_KEY =
  'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im56enZwaGhxYmNzY29ldHpmemtkIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzEzMjI3OTYsImV4cCI6MjA4Njg5ODc5Nn0.31g9pCxm5AThy9xckctfWMHG7wrcmykIPepA_PMHDkQ';

/**
 * Mark notifications as read via PostgREST.
 * Accepts: { notification_ids: string[], token: string }
 * Uses the doctor's JWT for auth — requires an UPDATE RLS policy on notifications.
 * Falls back to anon key if no token provided.
 */
export async function POST(req: NextRequest) {
  const { notification_ids, token } = await req.json();

  if (!Array.isArray(notification_ids) || notification_ids.length === 0) {
    return NextResponse.json({ error: 'notification_ids required' }, { status: 400 });
  }

  const filter = `notification_id=in.(${notification_ids.join(',')})`;
  const authToken = token || SUPABASE_ANON_KEY;

  const res = await fetch(`${SUPABASE_URL}/rest/v1/notifications?${filter}`, {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json',
      'apikey': SUPABASE_ANON_KEY,
      'Authorization': `Bearer ${authToken}`,
      'Prefer': 'return=minimal',
    },
    body: JSON.stringify({ is_read: true }),
  });

  if (!res.ok) {
    // If RLS blocks the update, return success anyway — the UI is already updated optimistically
    return NextResponse.json({ ok: true, persisted: false });
  }

  return NextResponse.json({ ok: true, persisted: true });
}
