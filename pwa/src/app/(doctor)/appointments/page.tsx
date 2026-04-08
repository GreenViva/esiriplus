'use client';

import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import {
  CalendarClock,
  Clock,
  User,
  ChevronRight,
} from 'lucide-react';
import { Card, Badge } from '@/components/ui';
import BackButton from '@/components/ui/BackButton';
import { useAuthStore } from '@/store/auth';
import { invokeEdgeFunction } from '@/lib/supabase';
import type { Appointment } from '@/types';

function formatTime(ts: number) {
  return new Date(ts).toLocaleTimeString('en-GB', {
    hour: '2-digit',
    minute: '2-digit',
  });
}

function formatDate(ts: number) {
  return new Date(ts).toLocaleDateString('en-GB', {
    weekday: 'short',
    day: 'numeric',
    month: 'short',
  });
}

function isToday(ts: number) {
  const d = new Date(ts);
  const now = new Date();
  return (
    d.getDate() === now.getDate() &&
    d.getMonth() === now.getMonth() &&
    d.getFullYear() === now.getFullYear()
  );
}

interface AppointmentRow {
  appointment_id: string;
  consultation_id: string | null;
  patient_session_id: string;
  doctor_id: string;
  scheduled_at: string;
  service_type: string;
  status: string;
}

export default function AppointmentsPage() {
  const router = useRouter();
  const [appointments, setAppointments] = useState<Appointment[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadAppointments();
  }, []);

  async function loadAppointments() {
    try {
      const token = useAuthStore.getState().session?.accessToken;
      const data = await invokeEdgeFunction<{ appointments: AppointmentRow[] }>(
        'book-appointment',
        { action: 'get_appointments', limit: 100, offset: 0 },
        token ?? undefined,
        'doctor',
      );
      setAppointments(
        (data?.appointments ?? []).map((r) => ({
          id: r.appointment_id,
          consultationId: r.consultation_id ?? '',
          patientSessionId: r.patient_session_id,
          doctorId: r.doctor_id,
          scheduledAt: new Date(r.scheduled_at).getTime(),
          serviceType: r.service_type,
          status: r.status,
        })),
      );
    } catch {
      // empty
    } finally {
      setLoading(false);
    }
  }

  const today = appointments.filter((a) => isToday(a.scheduledAt));
  const upcoming = appointments.filter((a) => !isToday(a.scheduledAt) && a.scheduledAt > Date.now());

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-[60vh]">
        <div className="w-8 h-8 border-3 border-[var(--brand-teal)] border-t-transparent rounded-full animate-spin" />
      </div>
    );
  }

  function AppointmentCard({ appointment }: { appointment: Appointment }) {
    return (
      <Card
        onClick={() => router.push(`/doc-consultation/${appointment.consultationId}`)}
      >
        <div className="flex items-center gap-3">
          <div className="w-11 h-11 rounded-full bg-[var(--brand-teal)]/10 flex items-center justify-center shrink-0">
            <User size={20} className="text-[var(--brand-teal)]" />
          </div>
          <div className="flex-1 min-w-0">
            <p className="text-sm font-semibold text-black truncate">
              Patient #{appointment.patientSessionId.slice(0, 8)}
            </p>
            <div className="flex items-center gap-2 mt-1">
              <Badge variant="teal">{appointment.serviceType}</Badge>
              <Badge
                variant={
                  appointment.status === 'scheduled'
                    ? 'gold'
                    : appointment.status === 'completed'
                    ? 'green'
                    : 'gray'
                }
              >
                {appointment.status}
              </Badge>
            </div>
          </div>
          <div className="text-right shrink-0">
            <div className="flex items-center gap-1 text-[var(--brand-teal)]">
              <Clock size={12} />
              <span className="text-sm font-semibold">{formatTime(appointment.scheduledAt)}</span>
            </div>
            {!isToday(appointment.scheduledAt) && (
              <p className="text-xs text-[var(--subtitle-grey)] mt-0.5">
                {formatDate(appointment.scheduledAt)}
              </p>
            )}
          </div>
          <ChevronRight size={16} className="text-gray-400 shrink-0" />
        </div>
      </Card>
    );
  }

  return (
    <div className="px-4 lg:px-8 py-6 max-w-4xl mx-auto">
      <BackButton href="/dashboard" />
      {/* Header */}
      <div className="flex items-center gap-3 mb-6">
        <div className="w-10 h-10 rounded-xl bg-[var(--brand-teal)]/10 flex items-center justify-center">
          <CalendarClock size={20} className="text-[var(--brand-teal)]" />
        </div>
        <div>
          <h1 className="text-xl font-bold text-black">Appointments</h1>
          <p className="text-xs text-[var(--subtitle-grey)]">
            {today.length} today, {upcoming.length} upcoming
          </p>
        </div>
      </div>

      {/* Today's appointments */}
      <section className="mb-8">
        <h2 className="text-base font-bold text-black mb-3 flex items-center gap-2">
          <div className="w-2 h-2 rounded-full bg-[var(--brand-teal)]" />
          Today
        </h2>
        {today.length === 0 ? (
          <Card>
            <p className="text-sm text-[var(--subtitle-grey)] text-center py-4">
              No appointments scheduled for today
            </p>
          </Card>
        ) : (
          <div className="space-y-3">
            {today.map((a) => (
              <AppointmentCard key={a.id} appointment={a} />
            ))}
          </div>
        )}
      </section>

      {/* Upcoming appointments */}
      <section>
        <h2 className="text-base font-bold text-black mb-3 flex items-center gap-2">
          <div className="w-2 h-2 rounded-full bg-[var(--royal-gold)]" />
          Upcoming
        </h2>
        {upcoming.length === 0 ? (
          <Card>
            <p className="text-sm text-[var(--subtitle-grey)] text-center py-4">
              No upcoming appointments
            </p>
          </Card>
        ) : (
          <div className="space-y-3">
            {upcoming.map((a) => (
              <AppointmentCard key={a.id} appointment={a} />
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
