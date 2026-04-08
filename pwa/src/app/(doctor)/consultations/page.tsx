'use client';

import { useState, useEffect, useCallback } from 'react';
import { useRouter } from 'next/navigation';
import {
  MessageSquare,
  Clock,
  CheckCircle2,
  XCircle,
  ChevronRight,
  Search,
} from 'lucide-react';
import { Card, Badge, Input } from '@/components/ui';
import BackButton from '@/components/ui/BackButton';
import { useAuthStore } from '@/store/auth';
import { useSupabase } from '@/hooks/useSupabase';
import type { Consultation, ConsultationStatus } from '@/types';

type TabKey = 'active' | 'pending' | 'completed' | 'cancelled';

const tabs: { key: TabKey; label: string; statuses: ConsultationStatus[] }[] = [
  { key: 'active', label: 'Active', statuses: ['active', 'in_progress', 'awaiting_extension', 'grace_period'] },
  { key: 'pending', label: 'Pending', statuses: ['pending'] },
  { key: 'completed', label: 'Completed', statuses: ['completed'] },
  { key: 'cancelled', label: 'Cancelled', statuses: ['cancelled', 'expired'] },
];

function formatCurrency(amount: number) {
  return `TZS ${amount.toLocaleString()}`;
}

function formatDate(ts: string) {
  return new Date(ts).toLocaleDateString('en-GB', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

function statusBadge(status: ConsultationStatus) {
  const map: Record<string, { variant: 'teal' | 'gold' | 'green' | 'red' | 'gray'; label: string }> = {
    active: { variant: 'teal', label: 'Active' },
    in_progress: { variant: 'teal', label: 'In Progress' },
    pending: { variant: 'gold', label: 'Pending' },
    awaiting_extension: { variant: 'gold', label: 'Awaiting Extension' },
    grace_period: { variant: 'gold', label: 'Grace Period' },
    completed: { variant: 'green', label: 'Completed' },
    cancelled: { variant: 'red', label: 'Cancelled' },
    expired: { variant: 'gray', label: 'Expired' },
  };
  const info = map[status] ?? { variant: 'gray' as const, label: status };
  return <Badge variant={info.variant}>{info.label}</Badge>;
}

export default function ConsultationsPage() {
  const router = useRouter();
  const db = useSupabase();
  const doctorId = useAuthStore((s) => s.session?.user?.id);
  const [activeTab, setActiveTab] = useState<TabKey>('active');
  const [consultations, setConsultations] = useState<Consultation[]>([]);
  const [loading, setLoading] = useState(true);
  const [search, setSearch] = useState('');

  const loadConsultations = useCallback(async () => {
    if (!db || !doctorId) return;
    try {
      const { data } = await db
        .from('consultations')
        .select('*')
        .eq('doctor_id', doctorId)
        .order('created_at', { ascending: false });
      setConsultations((data as Consultation[]) ?? []);
    } catch {
      // empty
    } finally {
      setLoading(false);
    }
  }, [db, doctorId]);

  useEffect(() => {
    loadConsultations();
  }, [loadConsultations]);

  const currentStatuses = tabs.find((t) => t.key === activeTab)!.statuses;
  const filtered = consultations.filter(
    (c) => currentStatuses.includes(c.status),
  );

  const tabIcons: Record<TabKey, React.ReactNode> = {
    active: <MessageSquare size={14} />,
    pending: <Clock size={14} />,
    completed: <CheckCircle2 size={14} />,
    cancelled: <XCircle size={14} />,
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-[60vh]">
        <div className="w-8 h-8 border-3 border-[var(--brand-teal)] border-t-transparent rounded-full animate-spin" />
      </div>
    );
  }

  return (
    <div className="px-4 lg:px-8 py-6 max-w-4xl mx-auto">
      <BackButton href="/dashboard" />
      <h1 className="text-xl font-bold text-black mb-4">Consultations</h1>

      {/* Search */}
      <div className="mb-4">
        <Input
          placeholder="Search consultations..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          icon={<Search size={16} />}
        />
      </div>

      {/* Tabs */}
      <div className="flex gap-2 overflow-x-auto pb-2 mb-4 -mx-4 px-4 scrollbar-hide">
        {tabs.map(({ key, label }) => {
          const count = consultations.filter((c) =>
            tabs.find((t) => t.key === key)!.statuses.includes(c.status),
          ).length;
          return (
            <button
              key={key}
              onClick={() => setActiveTab(key)}
              className={`flex items-center gap-1.5 px-4 py-2 rounded-full text-sm font-semibold whitespace-nowrap transition-all shrink-0 ${
                activeTab === key
                  ? 'bg-[var(--brand-teal)] text-white shadow-md shadow-[var(--brand-teal)]/20'
                  : 'bg-white border border-[var(--card-border)] text-gray-600 hover:bg-gray-50'
              }`}
            >
              {tabIcons[key]}
              {label}
              {count > 0 && (
                <span
                  className={`ml-0.5 px-1.5 py-0.5 rounded-full text-[10px] font-bold ${
                    activeTab === key
                      ? 'bg-white/20 text-white'
                      : 'bg-gray-100 text-gray-600'
                  }`}
                >
                  {count}
                </span>
              )}
            </button>
          );
        })}
      </div>

      {/* Consultation list */}
      {filtered.length === 0 ? (
        <div className="text-center py-16">
          <div className="w-14 h-14 rounded-full bg-gray-100 flex items-center justify-center mx-auto mb-3">
            <MessageSquare size={24} className="text-gray-400" />
          </div>
          <p className="text-sm font-semibold text-black">No {activeTab} consultations</p>
          <p className="text-xs text-[var(--subtitle-grey)] mt-1">
            {activeTab === 'active' ? 'No consultations in progress right now' : `No ${activeTab} consultations found`}
          </p>
        </div>
      ) : (
        <div className="space-y-3">
          {filtered.map((c) => (
            <Card
              key={c.consultation_id}
              onClick={() => router.push(`/doc-consultation/${c.consultation_id}`)}
            >
              <div className="flex items-start justify-between">
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2 mb-1.5">
                    <p className="text-sm font-semibold text-black truncate">
                      {c.service_type}
                    </p>
                    <Badge variant={c.service_tier === 'ROYAL' ? 'purple' : 'teal'}>
                      {c.service_tier}
                    </Badge>
                  </div>
                  <div className="flex items-center gap-2 flex-wrap">
                    {statusBadge(c.status)}
                    <span className="text-xs text-[var(--subtitle-grey)]">
                      {formatDate(c.created_at)}
                    </span>
                  </div>
                </div>
                <div className="flex items-center gap-2 ml-3 shrink-0">
                  <p className="text-sm font-bold text-black">
                    {formatCurrency(c.consultation_fee)}
                  </p>
                  <ChevronRight size={16} className="text-gray-400" />
                </div>
              </div>
            </Card>
          ))}
        </div>
      )}
    </div>
  );
}
