'use client';

import { useState, useEffect, useCallback } from 'react';
import { useRouter } from 'next/navigation';
import {
  Crown,
  Phone,
  Video,
  MessageSquare,
  ChevronRight,
  Clock,
  Pencil,
  Check,
  X,
} from 'lucide-react';
import { Card, GradientCard, Badge, Button } from '@/components/ui';
import BackButton from '@/components/ui/BackButton';
import { BottomSheet } from '@/components/ui';
import { useAuthStore } from '@/store/auth';
import { useSupabase } from '@/hooks/useSupabase';
import type { Consultation } from '@/types';

function formatCurrency(amount: number) {
  return `TZS ${amount.toLocaleString()}`;
}

function formatDate(ts: string) {
  return new Date(ts).toLocaleDateString('en-GB', {
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  });
}

function getDaysRemaining(followUpExpiry?: string) {
  if (!followUpExpiry) return 0;
  return Math.max(0, Math.ceil((new Date(followUpExpiry).getTime() - Date.now()) / (1000 * 60 * 60 * 24)));
}

type NicknameMap = Record<string, string>;

function loadNicknames(): NicknameMap {
  try {
    return JSON.parse(localStorage.getItem('royal-client-nicknames') || '{}');
  } catch {
    return {};
  }
}

function saveNicknames(map: NicknameMap) {
  localStorage.setItem('royal-client-nicknames', JSON.stringify(map));
}

export default function RoyalClientsPage() {
  const router = useRouter();
  const db = useSupabase();
  const doctorId = useAuthStore((s) => s.session?.user?.id);
  const [consultations, setConsultations] = useState<Consultation[]>([]);
  const [loading, setLoading] = useState(true);
  const [selected, setSelected] = useState<Consultation | null>(null);
  const [sheetOpen, setSheetOpen] = useState(false);
  const [nicknames, setNicknames] = useState<NicknameMap>({});
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editName, setEditName] = useState('');

  const loadRoyalClients = useCallback(async () => {
    if (!db || !doctorId) return;
    try {
      const { data } = await db
        .from('consultations')
        .select('*')
        .eq('doctor_id', doctorId)
        .eq('service_tier', 'ROYAL')
        .order('created_at', { ascending: false });
      setConsultations((data as Consultation[]) ?? []);
    } catch {
      // empty
    } finally {
      setLoading(false);
    }
  }, [db, doctorId]);

  useEffect(() => {
    setNicknames(loadNicknames());
    loadRoyalClients();
  }, [loadRoyalClients]);

  function openSheet(c: Consultation) {
    setSelected(c);
    setSheetOpen(true);
  }

  function startEditing(consultationId: string) {
    setEditingId(consultationId);
    setEditName(nicknames[consultationId] || '');
  }

  function saveNickname(consultationId: string) {
    const updated = { ...nicknames };
    if (editName.trim()) {
      updated[consultationId] = editName.trim();
    } else {
      delete updated[consultationId];
    }
    setNicknames(updated);
    saveNicknames(updated);
    setEditingId(null);
  }

  function getClientName(c: Consultation) {
    return nicknames[c.consultation_id] || `Royal Client #${c.consultation_id.slice(0, 6)}`;
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-[60vh]">
        <div className="w-8 h-8 border-3 border-[var(--royal-purple)] border-t-transparent rounded-full animate-spin" />
      </div>
    );
  }

  return (
    <div className="px-4 lg:px-8 py-6 max-w-4xl mx-auto">
      <BackButton href="/dashboard" />
      {/* Purple gradient header */}
      <GradientCard gradient="royal" className="p-6 mb-6">
        <div className="flex items-center gap-3 mb-2">
          <Crown size={24} className="text-[var(--royal-gold)]" />
          <h1 className="text-xl font-bold text-white">Royal Clients</h1>
        </div>
        <p className="text-sm text-white/80">
          Your premium clients with 14-day follow-up access. {consultations.length} client{consultations.length !== 1 ? 's' : ''} active.
        </p>
      </GradientCard>

      {/* Client list */}
      {consultations.length === 0 ? (
        <div className="text-center py-16">
          <div className="w-16 h-16 rounded-full bg-[var(--royal-purple)]/10 flex items-center justify-center mx-auto mb-4">
            <Crown size={28} className="text-[var(--royal-purple)]" />
          </div>
          <h3 className="text-base font-bold text-black mb-1">No royal clients yet</h3>
          <p className="text-sm text-[var(--subtitle-grey)]">
            Royal tier consultations will appear here with 14-day follow-up access
          </p>
        </div>
      ) : (
        <div className="space-y-3">
          {consultations.map((c) => {
            const daysLeft = getDaysRemaining(c.follow_up_expiry);
            const isEditing = editingId === c.consultation_id;

            return (
              <Card key={c.consultation_id}>
                <div className="flex items-start gap-3">
                  {/* Royal avatar */}
                  <div className="w-11 h-11 rounded-full bg-gradient-to-br from-[var(--royal-purple)] to-[#7C3AED] flex items-center justify-center shrink-0">
                    <Crown size={18} className="text-[var(--royal-gold)]" />
                  </div>

                  <div className="flex-1 min-w-0">
                    {/* Name with edit */}
                    <div className="flex items-center gap-2 mb-1">
                      {isEditing ? (
                        <div className="flex items-center gap-1.5 flex-1">
                          <input
                            type="text"
                            value={editName}
                            onChange={(e) => setEditName(e.target.value)}
                            onKeyDown={(e) => e.key === 'Enter' && saveNickname(c.consultation_id)}
                            className="flex-1 text-sm font-semibold text-black border-b border-[var(--brand-teal)] focus:outline-none py-0.5 bg-transparent"
                            placeholder="Enter nickname..."
                            autoFocus
                          />
                          <button onClick={() => saveNickname(c.consultation_id)} className="p-0.5">
                            <Check size={14} className="text-[var(--success-green)]" />
                          </button>
                          <button onClick={() => setEditingId(null)} className="p-0.5">
                            <X size={14} className="text-gray-400" />
                          </button>
                        </div>
                      ) : (
                        <>
                          <p className="text-sm font-semibold text-black truncate">
                            {getClientName(c)}
                          </p>
                          <button
                            onClick={() => startEditing(c.consultation_id)}
                            className="p-0.5 rounded hover:bg-gray-100"
                          >
                            <Pencil size={12} className="text-gray-400" />
                          </button>
                        </>
                      )}
                    </div>

                    {/* Info row */}
                    <div className="flex items-center gap-2 flex-wrap">
                      <Badge variant="purple">Royal</Badge>
                      <Badge
                        variant={
                          ['active', 'in_progress'].includes(c.status)
                            ? 'green'
                            : c.status === 'completed'
                            ? 'teal'
                            : 'gray'
                        }
                      >
                        {c.status.replace('_', ' ')}
                      </Badge>
                      <span className="text-xs text-[var(--subtitle-grey)]">{c.service_type}</span>
                    </div>

                    {/* Bottom row */}
                    <div className="flex items-center justify-between mt-2">
                      <div className="flex items-center gap-3 text-xs text-[var(--subtitle-grey)]">
                        <span>{formatDate(c.created_at)}</span>
                        <span className="font-semibold text-black">{formatCurrency(c.consultation_fee)}</span>
                      </div>
                      {daysLeft > 0 && (
                        <div className="flex items-center gap-1">
                          <Clock size={12} className="text-[var(--royal-gold)]" />
                          <span className="text-xs font-semibold text-[var(--royal-gold)]">
                            {daysLeft}d left
                          </span>
                        </div>
                      )}
                    </div>
                  </div>

                  {/* Open sheet */}
                  <button
                    onClick={() => openSheet(c)}
                    className="p-1.5 rounded-lg hover:bg-gray-100 shrink-0"
                  >
                    <ChevronRight size={18} className="text-gray-400" />
                  </button>
                </div>
              </Card>
            );
          })}
        </div>
      )}

      {/* Action bottom sheet */}
      <BottomSheet
        open={sheetOpen}
        onClose={() => {
          setSheetOpen(false);
          setSelected(null);
        }}
        title={selected ? getClientName(selected) : 'Client Options'}
      >
        {selected && (
          <div className="space-y-3 mt-2">
            <div className="flex items-center gap-2 mb-4">
              <Badge variant="purple">Royal</Badge>
              <span className="text-sm text-[var(--subtitle-grey)]">{selected.service_type}</span>
              {getDaysRemaining(selected.follow_up_expiry) > 0 && (
                <span className="text-xs font-semibold text-[var(--royal-gold)]">
                  {getDaysRemaining(selected.follow_up_expiry)} days remaining
                </span>
              )}
            </div>

            <button
              onClick={() => {
                setSheetOpen(false);
                router.push(`/doc-video-call/${selected.consultation_id}?type=AUDIO`);
              }}
              className="w-full flex items-center gap-3 p-3.5 bg-white border border-[var(--card-border)] rounded-xl hover:bg-gray-50 transition-colors"
            >
              <div className="w-10 h-10 rounded-full bg-[var(--success-green)]/10 flex items-center justify-center">
                <Phone size={18} className="text-[var(--success-green)]" />
              </div>
              <div className="text-left">
                <p className="text-sm font-semibold text-black">Voice Call</p>
                <p className="text-xs text-[var(--subtitle-grey)]">Start an audio call</p>
              </div>
            </button>

            <button
              onClick={() => {
                setSheetOpen(false);
                router.push(`/doc-video-call/${selected.consultation_id}?type=VIDEO`);
              }}
              className="w-full flex items-center gap-3 p-3.5 bg-white border border-[var(--card-border)] rounded-xl hover:bg-gray-50 transition-colors"
            >
              <div className="w-10 h-10 rounded-full bg-[var(--brand-teal)]/10 flex items-center justify-center">
                <Video size={18} className="text-[var(--brand-teal)]" />
              </div>
              <div className="text-left">
                <p className="text-sm font-semibold text-black">Video Call</p>
                <p className="text-xs text-[var(--subtitle-grey)]">Start a video consultation</p>
              </div>
            </button>

            <button
              onClick={() => {
                setSheetOpen(false);
                router.push(`/doc-consultation/${selected.consultation_id}`);
              }}
              className="w-full flex items-center gap-3 p-3.5 bg-white border border-[var(--card-border)] rounded-xl hover:bg-gray-50 transition-colors"
            >
              <div className="w-10 h-10 rounded-full bg-[var(--royal-purple)]/10 flex items-center justify-center">
                <MessageSquare size={18} className="text-[var(--royal-purple)]" />
              </div>
              <div className="text-left">
                <p className="text-sm font-semibold text-black">View Chat</p>
                <p className="text-xs text-[var(--subtitle-grey)]">Open consultation chat</p>
              </div>
            </button>
          </div>
        )}
      </BottomSheet>
    </div>
  );
}
