'use client';

import { useState, useEffect } from 'react';
import {
  Calendar,
  Clock,
  Save,
  CheckCircle,
} from 'lucide-react';
import { Card, Button } from '@/components/ui';
import BackButton from '@/components/ui/BackButton';
import { useAuthStore } from '@/store/auth';
import { useSupabase } from '@/hooks/useSupabase';
import type { DoctorAvailability } from '@/types';

const DAYS = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];

const DEFAULT_SCHEDULE: DoctorAvailability[] = DAYS.map((day) => ({
  dayOfWeek: day,
  enabled: day !== 'Sunday',
  startTime: '08:00',
  endTime: '17:00',
}));

export default function AvailabilityPage() {
  const { session } = useAuthStore();
  const db = useSupabase();
  const [schedule, setSchedule] = useState<DoctorAvailability[]>(DEFAULT_SCHEDULE);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    loadAvailability();
  }, []);

  async function loadAvailability() {
    if (!db || !session?.user?.id) {
      setLoading(false);
      return;
    }
    try {
      const { data } = await db
        .from('doctor_availability')
        .select('*')
        .eq('doctor_id', session.user.id)
        .order('day_of_week');
      if (data?.length) {
        setSchedule(data as DoctorAvailability[]);
      }
    } catch {
      // use defaults
    } finally {
      setLoading(false);
    }
  }

  function updateDay(index: number, updates: Partial<DoctorAvailability>) {
    setSchedule((prev) =>
      prev.map((d, i) => (i === index ? { ...d, ...updates } : d)),
    );
    setSaved(false);
  }

  async function saveAvailability() {
    if (!db || !session?.user?.id) return;
    setSaving(true);
    try {
      const rows = schedule.map((day) => ({
        doctor_id: session.user!.id,
        day_of_week: day.dayOfWeek,
        enabled: day.enabled,
        start_time: day.startTime,
        end_time: day.endTime,
      }));
      await db
        .from('doctor_availability')
        .upsert(rows, { onConflict: 'doctor_id,day_of_week' });
      setSaved(true);
      setTimeout(() => setSaved(false), 3000);
    } catch {
      // empty
    } finally {
      setSaving(false);
    }
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-[60vh]">
        <div className="w-8 h-8 border-3 border-[var(--brand-teal)] border-t-transparent rounded-full animate-spin" />
      </div>
    );
  }

  return (
    <div className="px-4 lg:px-8 py-6 max-w-2xl mx-auto">
      <BackButton href="/dashboard" />
      {/* Header */}
      <div className="flex items-center gap-3 mb-6">
        <div className="w-10 h-10 rounded-xl bg-[var(--brand-teal)]/10 flex items-center justify-center">
          <Calendar size={20} className="text-[var(--brand-teal)]" />
        </div>
        <div>
          <h1 className="text-xl font-bold text-black">Availability</h1>
          <p className="text-xs text-[var(--subtitle-grey)]">Set your weekly schedule</p>
        </div>
      </div>

      {/* Weekly schedule */}
      <div className="space-y-3 mb-8">
        {schedule.map((day, index) => (
          <Card key={day.dayOfWeek} className="!p-3.5">
            <div className="flex items-center justify-between">
              {/* Day name + toggle */}
              <div className="flex items-center gap-3">
                <button
                  onClick={() => updateDay(index, { enabled: !day.enabled })}
                  className={`relative w-11 h-6 rounded-full transition-colors ${
                    day.enabled ? 'bg-[var(--brand-teal)]' : 'bg-gray-200'
                  }`}
                >
                  <div
                    className={`absolute top-0.5 w-5 h-5 rounded-full bg-white shadow-sm transition-transform ${
                      day.enabled ? 'left-[22px]' : 'left-0.5'
                    }`}
                  />
                </button>
                <span className={`text-sm font-semibold ${day.enabled ? 'text-black' : 'text-gray-400'}`}>
                  {day.dayOfWeek}
                </span>
              </div>

              {/* Time pickers */}
              {day.enabled && (
                <div className="flex items-center gap-2">
                  <div className="flex items-center gap-1">
                    <Clock size={12} className="text-[var(--subtitle-grey)]" />
                    <input
                      type="time"
                      value={day.startTime}
                      onChange={(e) => updateDay(index, { startTime: e.target.value })}
                      className="text-xs text-black font-medium border border-[var(--card-border)] rounded-lg px-2 py-1.5 focus:outline-none focus:border-[var(--brand-teal)]"
                    />
                  </div>
                  <span className="text-xs text-[var(--subtitle-grey)]">to</span>
                  <input
                    type="time"
                    value={day.endTime}
                    onChange={(e) => updateDay(index, { endTime: e.target.value })}
                    className="text-xs text-black font-medium border border-[var(--card-border)] rounded-lg px-2 py-1.5 focus:outline-none focus:border-[var(--brand-teal)]"
                  />
                </div>
              )}

              {!day.enabled && (
                <span className="text-xs text-gray-400 italic">Day off</span>
              )}
            </div>
          </Card>
        ))}
      </div>

      {/* Save */}
      <Button
        fullWidth
        size="lg"
        loading={saving}
        onClick={saveAvailability}
      >
        {saved ? (
          <>
            <CheckCircle size={18} className="mr-2" /> Saved
          </>
        ) : (
          <>
            <Save size={18} className="mr-2" /> Save Availability
          </>
        )}
      </Button>
    </div>
  );
}
