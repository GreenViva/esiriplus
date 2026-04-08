import { NextRequest, NextResponse } from 'next/server';

const SUPABASE_URL = 'https://nzzvphhqbcscoetzfzkd.supabase.co';
const SUPABASE_ANON_KEY =
  'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im56enZwaGhxYmNzY29ldHpmemtkIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzEzMjI3OTYsImV4cCI6MjA4Njg5ODc5Nn0.31g9pCxm5AThy9xckctfWMHG7wrcmykIPepA_PMHDkQ';

/**
 * Server-side proxy for Supabase Edge Functions — avoids CORS issues.
 * Accepts: { functionName, body, token?, role? }
 */
export async function POST(req: NextRequest) {
  const { functionName, body, token, role } = await req.json();

  if (!functionName || typeof functionName !== 'string') {
    return NextResponse.json({ error: 'functionName is required' }, { status: 400 });
  }

  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
  };

  if (token) {
    if (role === 'patient') {
      headers['Authorization'] = `Bearer ${SUPABASE_ANON_KEY}`;
      headers['X-Patient-Token'] = token;
    } else if (role === 'doctor') {
      headers['Authorization'] = `Bearer ${SUPABASE_ANON_KEY}`;
      headers['X-Doctor-Token'] = token;
    } else {
      headers['Authorization'] = `Bearer ${SUPABASE_ANON_KEY}`;
      headers['X-Patient-Token'] = token;
    }
  } else {
    headers['Authorization'] = `Bearer ${SUPABASE_ANON_KEY}`;
  }

  const res = await fetch(`${SUPABASE_URL}/functions/v1/${functionName}`, {
    method: 'POST',
    headers,
    body: JSON.stringify(body ?? {}),
  });

  const data = await res.json().catch(() => null);
  return NextResponse.json(data, { status: res.status });
}
