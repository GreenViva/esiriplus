'use client';

import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import {
  ArrowLeft,
  Calendar,
  Clock,
  Search,
  Star,
  CheckCircle,
  ChevronRight,
} from 'lucide-react';
import { Button, Card, Input, Badge } from '@/components/ui';
import { useAuthStore } from '@/store/auth';
import { useSupabase } from '@/hooks/useSupabase';
import type { DoctorProfile, ServiceTier } from '@/types';

type BookingStep = 'doctor' | 'datetime' | 'service' | 'confirm';

export default function BookAppointmentPage() {
  const router = useRouter();
  const { patientSession } = useAuthStore();
  const db = useSupabase();

  const [step, setStep] = useState<BookingStep>('doctor');
  const [doctors, setDoctors] = useState<DoctorProfile[]>([]);
  const [loading, setLoading] = useState(true);
  const [doctorSearch, setDoctorSearch] = useState('');
  const [submitting, setSubmitting] = useState(false);

  // Selections
  const [selectedDoctor, setSelectedDoctor] = useState<DoctorProfile | null>(null);
  const [selectedDate, setSelectedDate] = useState('');
  const [selectedTime, setSelectedTime] = useState('');
  const [selectedTier, setSelectedTier] = useState<ServiceTier>('ECONOMY');

  // Load available doctors
  useEffect(() => {
    if (!db) return;
    async function loadDoctors() {
      try {
        const { data } = await db!
          .from('doctor_profiles')
          .select('*')
          .eq('is_verified', true)
          .order('rating', { ascending: false });

        if (data) setDoctors(data);
      } catch {
        // Silently handle
      } finally {
        setLoading(false);
      }
    }

    loadDoctors();
  }, [db]);

  const filteredDoctors = doctors.filter((d) => {
    if (!doctorSearch) return true;
    const q = doctorSearch.toLowerCase();
    return (
      d.fullName.toLowerCase().includes(q) ||
      d.specialty.toLowerCase().includes(q)
    );
  });

  // Generate time slots
  const timeSlots: string[] = [];
  for (let h = 8; h <= 20; h++) {
    for (const m of ['00', '30']) {
      const hStr = h.toString().padStart(2, '0');
      timeSlots.push(`${hStr}:${m}`);
    }
  }

  // Get min date (today)
  const today = new Date().toISOString().split('T')[0];

  async function handleConfirm() {
    if (!selectedDoctor || !selectedDate || !selectedTime || !db) return;
    setSubmitting(true);

    try {
      const scheduledAt = new Date(`${selectedDate}T${selectedTime}:00`).getTime();

      const { data, error } = await db.from('appointments').insert({
        patient_session_id: patientSession?.sessionId,
        doctor_id: selectedDoctor.doctorId,
        scheduled_at: scheduledAt,
        service_type: selectedTier === 'ROYAL' ? 'premium' : 'standard',
        status: 'scheduled',
      }).select('id').single();

      if (error) throw error;

      // Navigate to payment
      if (data?.id) {
        router.push(`/payment/${data.id}`);
      } else {
        router.push('/home');
      }
    } catch {
      // Handle error
    } finally {
      setSubmitting(false);
    }
  }

  function renderStep() {
    switch (step) {
      case 'doctor':
        return (
          <div className="space-y-4">
            <div>
              <h2 className="text-base font-bold text-black mb-1">Select a Doctor</h2>
              <p className="text-sm text-[var(--subtitle-grey)]">
                Choose a doctor for your appointment
              </p>
            </div>

            <Input
              placeholder="Search by name or specialty..."
              value={doctorSearch}
              onChange={(e) => setDoctorSearch(e.target.value)}
              icon={<Search size={16} />}
            />

            {loading ? (
              <div className="space-y-3">
                {[1, 2, 3].map((i) => (
                  <div
                    key={i}
                    className="h-20 bg-white rounded-2xl animate-pulse border border-[var(--card-border)]"
                  />
                ))}
              </div>
            ) : filteredDoctors.length === 0 ? (
              <div className="text-center py-12">
                <p className="text-sm text-[var(--subtitle-grey)]">
                  No doctors found
                </p>
              </div>
            ) : (
              <div className="space-y-3">
                {filteredDoctors.map((doc) => (
                  <Card
                    key={doc.doctorId}
                    onClick={() => {
                      setSelectedDoctor(doc);
                      setStep('datetime');
                    }}
                    className={
                      selectedDoctor?.doctorId === doc.doctorId
                        ? '!border-[var(--brand-teal)] !border-2'
                        : ''
                    }
                  >
                    <div className="flex items-center gap-3">
                      <div className="w-12 h-12 rounded-xl bg-[var(--brand-teal)]/10 flex items-center justify-center shrink-0">
                        {doc.profilePhotoUrl ? (
                          <img
                            src={doc.profilePhotoUrl}
                            alt={doc.fullName}
                            className="w-full h-full rounded-xl object-cover"
                          />
                        ) : (
                          <span className="text-lg font-bold text-[var(--brand-teal)]">
                            {doc.fullName.charAt(0)}
                          </span>
                        )}
                      </div>
                      <div className="flex-1 min-w-0">
                        <p className="text-sm font-bold text-black truncate">
                          Dr. {doc.fullName}
                        </p>
                        <p className="text-xs text-[var(--subtitle-grey)]">
                          {doc.specialty}
                        </p>
                        <div className="flex items-center gap-1 mt-0.5">
                          <Star
                            size={12}
                            fill="#F59E0B"
                            className="text-[var(--royal-gold)]"
                          />
                          <span className="text-xs font-medium text-black">
                            {doc.rating.toFixed(1)}
                          </span>
                        </div>
                      </div>
                      {doc.isOnline && (
                        <Badge variant="green">Online</Badge>
                      )}
                      <ChevronRight size={16} className="text-gray-300" />
                    </div>
                  </Card>
                ))}
              </div>
            )}
          </div>
        );

      case 'datetime':
        return (
          <div className="space-y-5">
            <div>
              <h2 className="text-base font-bold text-black mb-1">
                Select Date & Time
              </h2>
              <p className="text-sm text-[var(--subtitle-grey)]">
                With Dr. {selectedDoctor?.fullName}
              </p>
            </div>

            {/* Date picker */}
            <div>
              <label className="block text-sm font-semibold text-black mb-1.5">
                Date
              </label>
              <div className="relative">
                <Calendar
                  size={18}
                  className="absolute left-3 top-1/2 -translate-y-1/2 text-[var(--brand-teal)]"
                />
                <input
                  type="date"
                  min={today}
                  value={selectedDate}
                  onChange={(e) => setSelectedDate(e.target.value)}
                  className="w-full h-11 pl-10 pr-3 text-sm text-black border border-[var(--card-border)] rounded-xl focus:outline-none focus:border-[var(--brand-teal)] focus:ring-1 focus:ring-[var(--brand-teal)]"
                />
              </div>
            </div>

            {/* Time slots */}
            <div>
              <label className="block text-sm font-semibold text-black mb-2">
                <Clock size={14} className="inline mr-1.5" />
                Available Times
              </label>
              <div className="grid grid-cols-4 gap-2">
                {timeSlots.map((time) => (
                  <button
                    key={time}
                    onClick={() => setSelectedTime(time)}
                    className={`h-10 rounded-xl text-sm font-medium transition-colors ${
                      selectedTime === time
                        ? 'bg-[var(--brand-teal)] text-white'
                        : 'bg-white border border-[var(--card-border)] text-black hover:border-[var(--brand-teal)]'
                    }`}
                  >
                    {time}
                  </button>
                ))}
              </div>
            </div>

            <div className="flex gap-3 pt-2">
              <Button variant="outline" fullWidth onClick={() => setStep('doctor')}>
                Back
              </Button>
              <Button
                fullWidth
                disabled={!selectedDate || !selectedTime}
                onClick={() => setStep('service')}
              >
                Next
              </Button>
            </div>
          </div>
        );

      case 'service':
        return (
          <div className="space-y-5">
            <div>
              <h2 className="text-base font-bold text-black mb-1">Service Type</h2>
              <p className="text-sm text-[var(--subtitle-grey)]">
                Choose your consultation package
              </p>
            </div>

            {/* Economy */}
            <Card
              onClick={() => setSelectedTier('ECONOMY')}
              className={
                selectedTier === 'ECONOMY'
                  ? '!border-[var(--brand-teal)] !border-2'
                  : ''
              }
            >
              <div className="flex items-center gap-3">
                <div
                  className={`w-5 h-5 rounded-full border-2 flex items-center justify-center ${
                    selectedTier === 'ECONOMY'
                      ? 'border-[var(--brand-teal)]'
                      : 'border-gray-300'
                  }`}
                >
                  {selectedTier === 'ECONOMY' && (
                    <div className="w-2.5 h-2.5 rounded-full bg-[var(--brand-teal)]" />
                  )}
                </div>
                <div className="flex-1">
                  <p className="text-sm font-bold text-black">Economy</p>
                  <p className="text-xs text-[var(--subtitle-grey)]">
                    15 min, 1 follow-up
                  </p>
                </div>
                <p className="text-sm font-bold text-[var(--brand-teal)]">
                  TZS 5,000
                </p>
              </div>
            </Card>

            {/* Royal */}
            <Card
              onClick={() => setSelectedTier('ROYAL')}
              className={
                selectedTier === 'ROYAL'
                  ? '!border-[var(--royal-purple)] !border-2'
                  : ''
              }
            >
              <div className="flex items-center gap-3">
                <div
                  className={`w-5 h-5 rounded-full border-2 flex items-center justify-center ${
                    selectedTier === 'ROYAL'
                      ? 'border-[var(--royal-purple)]'
                      : 'border-gray-300'
                  }`}
                >
                  {selectedTier === 'ROYAL' && (
                    <div className="w-2.5 h-2.5 rounded-full bg-[var(--royal-purple)]" />
                  )}
                </div>
                <div className="flex-1">
                  <p className="text-sm font-bold text-black">Royal</p>
                  <p className="text-xs text-[var(--subtitle-grey)]">
                    30 min, 14-day follow-up
                  </p>
                </div>
                <div className="text-right">
                  <p className="text-sm font-bold text-[var(--royal-purple)]">
                    TZS 15,000
                  </p>
                  <Badge variant="purple">Premium</Badge>
                </div>
              </div>
            </Card>

            <div className="flex gap-3 pt-2">
              <Button variant="outline" fullWidth onClick={() => setStep('datetime')}>
                Back
              </Button>
              <Button fullWidth onClick={() => setStep('confirm')}>
                Next
              </Button>
            </div>
          </div>
        );

      case 'confirm':
        return (
          <div className="space-y-5">
            <div>
              <h2 className="text-base font-bold text-black mb-1">
                Confirm Appointment
              </h2>
              <p className="text-sm text-[var(--subtitle-grey)]">
                Review your appointment details
              </p>
            </div>

            <Card className="space-y-4">
              {/* Doctor */}
              <div className="flex items-center gap-3 pb-4 border-b border-[var(--card-border)]">
                <div className="w-12 h-12 rounded-xl bg-[var(--brand-teal)]/10 flex items-center justify-center">
                  <span className="text-lg font-bold text-[var(--brand-teal)]">
                    {selectedDoctor?.fullName.charAt(0)}
                  </span>
                </div>
                <div>
                  <p className="text-sm font-bold text-black">
                    Dr. {selectedDoctor?.fullName}
                  </p>
                  <p className="text-xs text-[var(--subtitle-grey)]">
                    {selectedDoctor?.specialty}
                  </p>
                </div>
              </div>

              {/* Date & Time */}
              <div className="flex items-center gap-3">
                <Calendar size={16} className="text-[var(--brand-teal)]" />
                <div>
                  <p className="text-xs text-[var(--subtitle-grey)]">Date & Time</p>
                  <p className="text-sm font-semibold text-black">
                    {selectedDate &&
                      new Date(selectedDate).toLocaleDateString('en-GB', {
                        weekday: 'long',
                        day: 'numeric',
                        month: 'long',
                        year: 'numeric',
                      })}{' '}
                    at {selectedTime}
                  </p>
                </div>
              </div>

              {/* Service */}
              <div className="flex items-center gap-3">
                <CheckCircle
                  size={16}
                  className={
                    selectedTier === 'ROYAL'
                      ? 'text-[var(--royal-purple)]'
                      : 'text-[var(--brand-teal)]'
                  }
                />
                <div>
                  <p className="text-xs text-[var(--subtitle-grey)]">Service</p>
                  <p className="text-sm font-semibold text-black">
                    {selectedTier === 'ROYAL' ? 'Royal' : 'Economy'} - TZS{' '}
                    {selectedTier === 'ROYAL' ? '15,000' : '5,000'}
                  </p>
                </div>
              </div>
            </Card>

            <div className="flex gap-3 pt-2">
              <Button variant="outline" fullWidth onClick={() => setStep('service')}>
                Back
              </Button>
              <Button fullWidth loading={submitting} onClick={handleConfirm}>
                Confirm & Pay
              </Button>
            </div>
          </div>
        );
    }
  }

  // Progress indicator
  const steps: BookingStep[] = ['doctor', 'datetime', 'service', 'confirm'];
  const currentIdx = steps.indexOf(step);

  return (
    <div className="min-h-dvh bg-gray-50">
      {/* Header */}
      <div className="sticky top-0 z-10 bg-white border-b border-[var(--card-border)] px-5 py-4">
        <div className="flex items-center gap-3 mb-3">
          <button
            onClick={() => {
              if (currentIdx > 0) {
                setStep(steps[currentIdx - 1]);
              } else {
                router.push('/home');
              }
            }}
            className="p-1.5 -ml-1.5 rounded-xl hover:bg-gray-100 transition-colors"
          >
            <ArrowLeft size={22} className="text-black" />
          </button>
          <h1 className="text-lg font-bold text-black">Book Appointment</h1>
        </div>

        {/* Step progress */}
        <div className="flex gap-1.5">
          {steps.map((s, i) => (
            <div
              key={s}
              className={`h-1 flex-1 rounded-full ${
                i <= currentIdx ? 'bg-[var(--brand-teal)]' : 'bg-gray-200'
              }`}
            />
          ))}
        </div>
      </div>

      <div className="px-5 py-6">{renderStep()}</div>
    </div>
  );
}
