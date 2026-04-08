'use client';

import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { ArrowLeft, FileText, ChevronRight, Search } from 'lucide-react';
import { Card, Badge, Input } from '@/components/ui';
import { useAuthStore } from '@/store/auth';
import { useSupabase } from '@/hooks/useSupabase';
import type { PatientReport } from '@/types';

function severityVariant(severity: string) {
  switch (severity?.toLowerCase()) {
    case 'high':
    case 'severe':
      return 'red' as const;
    case 'moderate':
      return 'gold' as const;
    case 'low':
    case 'mild':
      return 'green' as const;
    default:
      return 'teal' as const;
  }
}

export default function ReportsPage() {
  const router = useRouter();
  const { patientSession } = useAuthStore();
  const db = useSupabase();
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const [reports, setReports] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');

  useEffect(() => {
    if (!db) return;
    async function loadReports() {
      if (!patientSession?.sessionId) {
        setLoading(false);
        return;
      }

      try {
        const { data } = await db!
          .from('consultation_reports')
          .select('*')
          .eq('patient_session_id', patientSession.sessionId)
          .order('created_at', { ascending: false });

        if (data) {
          setReports(data);
          // Mark all reports as viewed
          try {
            const ids = data.map((r: Record<string, string>) => r.report_id ?? r.reportId);
            localStorage.setItem('esiri_viewed_reports', JSON.stringify(ids.filter(Boolean)));
          } catch { /* best-effort */ }
        }
      } catch {
        // Silently handle
      } finally {
        setLoading(false);
      }
    }

    loadReports();
  }, [patientSession?.sessionId, db]);

  // Supabase returns snake_case; access both formats for safety
  const get = (r: Record<string, unknown>, camel: string, snake: string) =>
    (r[camel] ?? r[snake] ?? '') as string;

  const filteredReports = reports.filter((r: Record<string, unknown>) => {
    if (!search) return true;
    const q = search.toLowerCase();
    return (
      get(r, 'diagnosedProblem', 'diagnosed_problem').toLowerCase().includes(q) ||
      get(r, 'doctorName', 'doctor_name').toLowerCase().includes(q) ||
      get(r, 'category', 'category').toLowerCase().includes(q)
    );
  });

  return (
    <div className="min-h-dvh bg-gray-50">
      {/* Header */}
      <div className="sticky top-0 z-10 bg-white border-b border-[var(--card-border)] px-5 py-4">
        <div className="flex items-center gap-3 mb-3">
          <button
            onClick={() => router.push('/home')}
            className="p-1.5 -ml-1.5 rounded-xl hover:bg-gray-100 transition-colors"
          >
            <ArrowLeft size={22} className="text-black" />
          </button>
          <h1 className="text-lg font-bold text-black">My Reports</h1>
          <span className="ml-auto text-xs text-[var(--subtitle-grey)] font-medium">
            {reports.length} total
          </span>
        </div>

        {/* Search */}
        <Input
          placeholder="Search reports..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          icon={<Search size={16} />}
        />
      </div>

      <div className="px-5 py-4 space-y-3">
        {loading ? (
          <div className="space-y-3">
            {[1, 2, 3, 4].map((i) => (
              <div
                key={i}
                className="h-20 bg-white rounded-2xl border border-[var(--card-border)] animate-pulse"
              />
            ))}
          </div>
        ) : filteredReports.length === 0 ? (
          <div className="text-center py-16">
            <FileText size={40} className="text-gray-300 mx-auto mb-3" />
            <p className="text-base font-bold text-black mb-1">
              {search ? 'No matching reports' : 'No reports yet'}
            </p>
            <p className="text-sm text-[var(--subtitle-grey)]">
              {search
                ? 'Try a different search term'
                : 'Reports will appear here after consultations'}
            </p>
          </div>
        ) : (
          filteredReports.map((r: Record<string, unknown>) => {
            const id = (r.reportId ?? r.report_id ?? '') as string;
            return (
            <Card
              key={id}
              onClick={() => router.push(`/reports/${id}`)}
            >
              <div className="flex items-start gap-3">
                <div className="w-10 h-10 rounded-xl bg-[var(--brand-teal)]/10 flex items-center justify-center shrink-0 mt-0.5">
                  <FileText size={18} className="text-[var(--brand-teal)]" />
                </div>
                <div className="flex-1 min-w-0">
                  <div className="flex items-start justify-between gap-2">
                    <div className="min-w-0">
                      <p className="text-sm font-bold text-black truncate">
                        {get(r, 'diagnosedProblem', 'diagnosed_problem')}
                      </p>
                      <p className="text-xs text-[var(--subtitle-grey)] mt-0.5">
                        Dr. {get(r, 'doctorName', 'doctor_name')}
                      </p>
                    </div>
                    <Badge variant={severityVariant(get(r, 'severity', 'severity'))}>
                      {get(r, 'category', 'category')}
                    </Badge>
                  </div>
                  <p className="text-xs text-[var(--subtitle-grey)] mt-1.5">
                    {get(r, 'consultationDate', 'consultation_date')}
                  </p>
                </div>
                <ChevronRight
                  size={16}
                  className="text-gray-300 shrink-0 mt-3"
                />
              </div>
            </Card>
            );
          })
        )}
      </div>
    </div>
  );
}
