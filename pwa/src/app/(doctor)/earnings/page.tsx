'use client';

import { useState, useEffect, useCallback } from 'react';
import {
  DollarSign,
  TrendingUp,
  Calendar,
  Clock,
  ArrowUpRight,
  ArrowDownRight,
  Wallet,
} from 'lucide-react';
import { Card, GradientCard, Badge } from '@/components/ui';
import BackButton from '@/components/ui/BackButton';
import { useAuthStore } from '@/store/auth';
import { useSupabase } from '@/hooks/useSupabase';
import type { DoctorEarnings, EarningsTransaction, EarningsRow } from '@/types';

function formatCurrency(amount: number) {
  return `TZS ${amount.toLocaleString()}`;
}

function getStatusVariant(status: string): 'green' | 'gold' | 'red' | 'gray' {
  switch (status) {
    case 'completed':
    case 'paid':
      return 'green';
    case 'pending':
      return 'gold';
    case 'failed':
      return 'red';
    default:
      return 'gray';
  }
}

export default function EarningsPage() {
  const db = useSupabase();
  const doctorId = useAuthStore((s) => s.session?.user?.id);
  const [earnings, setEarnings] = useState<DoctorEarnings | null>(null);
  const [loading, setLoading] = useState(true);

  const loadEarnings = useCallback(async () => {
    if (!db || !doctorId) return;
    try {
      const { data: rows } = await db
        .from('doctor_earnings')
        .select('*')
        .eq('doctor_id', doctorId)
        .order('created_at', { ascending: false });

      const earningsRows = (rows as EarningsRow[]) ?? [];

      const now = new Date();
      const startOfMonth = new Date(now.getFullYear(), now.getMonth(), 1).toISOString();
      const startOfLastMonth = new Date(now.getFullYear(), now.getMonth() - 1, 1).toISOString();

      const total = earningsRows.reduce((sum, r) => sum + r.amount, 0);
      const thisMonth = earningsRows
        .filter((r) => r.created_at >= startOfMonth)
        .reduce((sum, r) => sum + r.amount, 0);
      const lastMonth = earningsRows
        .filter((r) => r.created_at >= startOfLastMonth && r.created_at < startOfMonth)
        .reduce((sum, r) => sum + r.amount, 0);
      const pending = earningsRows
        .filter((r) => r.status === 'pending')
        .reduce((sum, r) => sum + r.amount, 0);

      const transactions: EarningsTransaction[] = earningsRows.slice(0, 20).map((r) => ({
        id: r.earning_id ?? r.id ?? r.consultation_id,
        patientName: `${r.earning_type.replace(/_/g, ' ')} — ${r.consultation_id.slice(0, 8)}`,
        amount: r.amount,
        date: new Date(r.created_at).toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' }),
        status: r.status,
      }));

      setEarnings({ totalEarnings: total, thisMonthEarnings: thisMonth, lastMonthEarnings: lastMonth, pendingPayout: pending, transactions });
    } catch {
      // empty
    } finally {
      setLoading(false);
    }
  }, [db, doctorId]);

  useEffect(() => {
    loadEarnings();
  }, [loadEarnings]);

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-[60vh]">
        <div className="w-8 h-8 border-3 border-[var(--brand-teal)] border-t-transparent rounded-full animate-spin" />
      </div>
    );
  }

  const summaryCards = earnings
    ? [
        { label: 'Total Earnings', value: earnings.totalEarnings, icon: DollarSign, color: 'var(--brand-teal)', bg: 'bg-[var(--brand-teal)]/10' },
        { label: 'This Month', value: earnings.thisMonthEarnings, icon: TrendingUp, color: 'var(--success-green)', bg: 'bg-[var(--success-green)]/10' },
        { label: 'Last Month', value: earnings.lastMonthEarnings, icon: Calendar, color: 'var(--royal-purple)', bg: 'bg-[var(--royal-purple)]/10' },
        { label: 'Pending Payout', value: earnings.pendingPayout, icon: Clock, color: 'var(--royal-gold)', bg: 'bg-[var(--royal-gold)]/10' },
      ]
    : [];

  return (
    <div className="px-4 lg:px-8 py-6 max-w-4xl mx-auto">
      <BackButton href="/dashboard" />
      {/* Header */}
      <GradientCard gradient="teal" className="p-6 mb-6">
        <div className="flex items-center gap-3 mb-3">
          <Wallet size={24} className="text-white" />
          <h1 className="text-xl font-bold text-white">Earnings</h1>
        </div>
        {earnings && (
          <div>
            <p className="text-sm text-white/70 mb-1">Total Earnings</p>
            <p className="text-3xl font-bold text-white">
              {formatCurrency(earnings.totalEarnings)}
            </p>
          </div>
        )}
      </GradientCard>

      {/* Summary grid */}
      <div className="grid grid-cols-2 gap-3 mb-8">
        {summaryCards.map(({ label, value, icon: Icon, color, bg }) => (
          <Card key={label} className="!p-3.5">
            <div className="flex items-start justify-between mb-2">
              <div className={`w-9 h-9 rounded-xl ${bg} flex items-center justify-center`}>
                <Icon size={18} style={{ color }} />
              </div>
            </div>
            <p className="text-lg font-bold text-black">{formatCurrency(value)}</p>
            <p className="text-xs text-[var(--subtitle-grey)] mt-0.5">{label}</p>
          </Card>
        ))}
      </div>

      {/* Transactions */}
      <section>
        <h2 className="text-base font-bold text-black mb-3">Transaction History</h2>

        {!earnings?.transactions?.length ? (
          <Card>
            <div className="text-center py-8">
              <div className="w-14 h-14 rounded-full bg-gray-100 flex items-center justify-center mx-auto mb-3">
                <DollarSign size={24} className="text-gray-400" />
              </div>
              <p className="text-sm font-semibold text-black">No transactions yet</p>
              <p className="text-xs text-[var(--subtitle-grey)] mt-1">
                Your earnings from consultations will appear here
              </p>
            </div>
          </Card>
        ) : (
          <Card padding={false}>
            <div className="divide-y divide-[var(--card-border)]">
              {earnings.transactions.map((tx) => (
                <div key={tx.id} className="flex items-center justify-between px-4 py-3.5">
                  <div className="flex items-center gap-3">
                    <div
                      className={`w-9 h-9 rounded-full flex items-center justify-center ${
                        tx.status === 'completed' || tx.status === 'paid'
                          ? 'bg-[var(--success-green)]/10'
                          : tx.status === 'pending'
                          ? 'bg-[var(--royal-gold)]/10'
                          : 'bg-gray-100'
                      }`}
                    >
                      {tx.status === 'completed' || tx.status === 'paid' ? (
                        <ArrowUpRight size={16} className="text-[var(--success-green)]" />
                      ) : (
                        <Clock size={16} className="text-[var(--royal-gold)]" />
                      )}
                    </div>
                    <div>
                      <p className="text-sm font-semibold text-black">{tx.patientName}</p>
                      <div className="flex items-center gap-2 mt-0.5">
                        <span className="text-xs text-[var(--subtitle-grey)]">{tx.date}</span>
                        <Badge variant={getStatusVariant(tx.status)}>{tx.status}</Badge>
                      </div>
                    </div>
                  </div>
                  <p
                    className={`text-sm font-bold ${
                      tx.status === 'completed' || tx.status === 'paid'
                        ? 'text-[var(--success-green)]'
                        : 'text-black'
                    }`}
                  >
                    {tx.status === 'completed' || tx.status === 'paid' ? '+' : ''}
                    {formatCurrency(tx.amount)}
                  </p>
                </div>
              ))}
            </div>
          </Card>
        )}
      </section>
    </div>
  );
}
