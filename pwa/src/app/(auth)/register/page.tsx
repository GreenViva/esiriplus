'use client';

import { useState, useCallback, useRef } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import {
  User, Mail, Phone, Lock, Eye, EyeOff, Stethoscope, FileText,
  Globe, CheckCircle2, Camera, Upload, ChevronLeft, ChevronRight,
  Shield, ArrowLeft,
} from 'lucide-react';
import { Button, Input, Card, Select } from '@/components/ui';
import { TextArea } from '@/components/ui/Input';
import { invokeEdgeFunction } from '@/lib/supabase';
import { useAuthStore } from '@/store/auth';
import type { Session } from '@/types';

const TOTAL_STEPS = 7;

const SPECIALTIES = [
  { value: 'general_practice', label: 'General Practice' },
  { value: 'internal_medicine', label: 'Internal Medicine' },
  { value: 'pediatrics', label: 'Pediatrics' },
  { value: 'obstetrics_gynecology', label: 'Obstetrics & Gynecology' },
  { value: 'surgery', label: 'Surgery' },
  { value: 'dermatology', label: 'Dermatology' },
  { value: 'psychiatry', label: 'Psychiatry' },
  { value: 'cardiology', label: 'Cardiology' },
  { value: 'orthopedics', label: 'Orthopedics' },
  { value: 'ophthalmology', label: 'Ophthalmology' },
  { value: 'ent', label: 'ENT (Ear, Nose & Throat)' },
  { value: 'urology', label: 'Urology' },
  { value: 'neurology', label: 'Neurology' },
  { value: 'oncology', label: 'Oncology' },
  { value: 'radiology', label: 'Radiology' },
  { value: 'other', label: 'Other' },
];

const LANGUAGES = [
  'English', 'Swahili', 'French', 'Arabic', 'Portuguese', 'Amharic', 'Hausa', 'Yoruba',
];

const SERVICES = [
  'General Consultation', 'Follow-up Visit', 'Prescription Renewal',
  'Mental Health', 'Chronic Disease Management', 'Pediatric Consultation',
  'Women\'s Health', 'Dermatology Consultation', 'Lab Result Review',
];

interface FormData {
  fullName: string;
  email: string;
  phone: string;
  password: string;
  confirmPassword: string;
  otpCode: string;
  specialty: string;
  licenseNumber: string;
  yearsExperience: string;
  bio: string;
  languages: string[];
  services: string[];
  licenseImage: File | null;
  idImage: File | null;
  profilePhoto: File | null;
  termsAccepted: boolean;
}

export default function RegisterPage() {
  const router = useRouter();
  const { setSession } = useAuthStore();

  const [step, setStep] = useState(1);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [otpSent, setOtpSent] = useState(false);
  const [otpVerified, setOtpVerified] = useState(false);

  const licenseInputRef = useRef<HTMLInputElement>(null);
  const idInputRef = useRef<HTMLInputElement>(null);
  const photoInputRef = useRef<HTMLInputElement>(null);

  const [form, setForm] = useState<FormData>({
    fullName: '',
    email: '',
    phone: '',
    password: '',
    confirmPassword: '',
    otpCode: '',
    specialty: '',
    licenseNumber: '',
    yearsExperience: '',
    bio: '',
    languages: [],
    services: [],
    licenseImage: null,
    idImage: null,
    profilePhoto: null,
    termsAccepted: false,
  });

  const update = useCallback((field: keyof FormData, value: FormData[keyof FormData]) => {
    setForm((prev) => ({ ...prev, [field]: value }));
    setError('');
  }, []);

  function toggleArrayItem(field: 'languages' | 'services', item: string) {
    setForm((prev) => {
      const arr = prev[field];
      return { ...prev, [field]: arr.includes(item) ? arr.filter((i) => i !== item) : [...arr, item] };
    });
  }

  function validateStep(): boolean {
    switch (step) {
      case 1:
        if (!form.fullName.trim()) { setError('Full name is required'); return false; }
        if (!form.email.trim()) { setError('Email is required'); return false; }
        if (!form.phone.trim()) { setError('Phone number is required'); return false; }
        if (form.password.length < 8) { setError('Password must be at least 8 characters'); return false; }
        if (form.password !== form.confirmPassword) { setError('Passwords do not match'); return false; }
        return true;
      case 2:
        if (!otpVerified && !form.otpCode.trim()) { setError('Please enter the verification code'); return false; }
        return true;
      case 3:
        if (!form.specialty) { setError('Please select a specialty'); return false; }
        if (!form.licenseNumber.trim()) { setError('License number is required'); return false; }
        if (!form.yearsExperience) { setError('Years of experience is required'); return false; }
        return true;
      case 4:
        if (!form.bio.trim()) { setError('Please write a short bio'); return false; }
        if (form.languages.length === 0) { setError('Select at least one language'); return false; }
        if (form.services.length === 0) { setError('Select at least one service'); return false; }
        return true;
      case 5:
        if (!form.licenseImage) { setError('Please upload your medical license'); return false; }
        if (!form.idImage) { setError('Please upload your government ID'); return false; }
        return true;
      case 6:
        return true; // Profile photo is optional
      case 7:
        if (!form.termsAccepted) { setError('You must accept the terms to continue'); return false; }
        return true;
      default:
        return true;
    }
  }

  async function handleSendOtp() {
    setLoading(true);
    setError('');
    try {
      await invokeEdgeFunction('send-doctor-otp', { email: form.email.trim().toLowerCase() });
      setOtpSent(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to send OTP');
    } finally {
      setLoading(false);
    }
  }

  async function handleVerifyOtp() {
    setLoading(true);
    setError('');
    try {
      await invokeEdgeFunction('verify-doctor-otp', {
        email: form.email.trim().toLowerCase(),
        code: form.otpCode.trim(),
      });
      setOtpVerified(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Invalid verification code');
    } finally {
      setLoading(false);
    }
  }

  function fileToBase64(file: File): Promise<string> {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(reader.result as string);
      reader.onerror = reject;
      reader.readAsDataURL(file);
    });
  }

  async function handleSubmit() {
    if (!validateStep()) return;
    setLoading(true);
    setError('');

    try {
      const payload: Record<string, unknown> = {
        fullName: form.fullName.trim(),
        email: form.email.trim().toLowerCase(),
        phone: form.phone.trim(),
        password: form.password,
        specialty: form.specialty,
        licenseNumber: form.licenseNumber.trim(),
        yearsExperience: parseInt(form.yearsExperience, 10),
        bio: form.bio.trim(),
        languages: form.languages,
        services: form.services,
        termsAccepted: form.termsAccepted,
      };

      if (form.licenseImage) payload.licenseImage = await fileToBase64(form.licenseImage);
      if (form.idImage) payload.idImage = await fileToBase64(form.idImage);
      if (form.profilePhoto) payload.profilePhoto = await fileToBase64(form.profilePhoto);

      const result = await invokeEdgeFunction<Session>('doctor-register', payload);
      setSession(result);
      router.push('/dashboard');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Registration failed. Please try again.');
    } finally {
      setLoading(false);
    }
  }

  async function handleNext() {
    if (!validateStep()) return;

    if (step === 1 && !otpSent) {
      await handleSendOtp();
      if (!error) setStep(2);
      return;
    }

    if (step === TOTAL_STEPS) {
      await handleSubmit();
      return;
    }

    setStep((s) => Math.min(s + 1, TOTAL_STEPS));
  }

  function handleBack() {
    setError('');
    setStep((s) => Math.max(s - 1, 1));
  }

  const stepTitles = [
    'Personal Info',
    'Verify Email',
    'Professional',
    'Profile Details',
    'Credentials',
    'Photo',
    'Terms',
  ];

  return (
    <div className="pt-2 pb-4">
      {/* Progress bar */}
      <div className="mb-4">
        <div className="flex items-center justify-between mb-2 px-1">
          <p className="text-xs font-bold text-black">Step {step} of {TOTAL_STEPS}</p>
          <p className="text-xs font-medium text-[var(--brand-teal)]">{stepTitles[step - 1]}</p>
        </div>
        <div className="w-full h-2 bg-gray-100 rounded-full overflow-hidden">
          <div
            className="h-full bg-gradient-to-r from-[var(--brand-teal)] to-[#1A7A6E] rounded-full transition-all duration-300"
            style={{ width: `${(step / TOTAL_STEPS) * 100}%` }}
          />
        </div>
        {/* Step dots */}
        <div className="flex justify-between mt-2 px-0.5">
          {Array.from({ length: TOTAL_STEPS }).map((_, i) => (
            <div
              key={i}
              className={`w-2 h-2 rounded-full transition-colors ${
                i + 1 <= step ? 'bg-[var(--brand-teal)]' : 'bg-gray-200'
              }`}
            />
          ))}
        </div>
      </div>

      <Card className="p-5">
        {/* Step 1: Personal Info */}
        {step === 1 && (
          <div className="space-y-4">
            <div className="text-center mb-2">
              <h2 className="text-lg font-bold text-black">Create Your Account</h2>
              <p className="text-sm text-black">Join the eSIRI Plus doctor network</p>
            </div>

            <Input
              label="Full Name"
              placeholder="Dr. John Doe"
              icon={<User size={18} />}
              value={form.fullName}
              onChange={(e) => update('fullName', e.target.value)}
              required
            />
            <Input
              label="Email Address"
              type="email"
              placeholder="doctor@example.com"
              icon={<Mail size={18} />}
              value={form.email}
              onChange={(e) => update('email', e.target.value)}
              required
            />
            <Input
              label="Phone Number"
              type="tel"
              placeholder="+255 7XX XXX XXX"
              icon={<Phone size={18} />}
              value={form.phone}
              onChange={(e) => update('phone', e.target.value)}
              required
            />
            <div className="relative">
              <Input
                label="Password"
                type={showPassword ? 'text' : 'password'}
                placeholder="Min. 8 characters"
                icon={<Lock size={18} />}
                value={form.password}
                onChange={(e) => update('password', e.target.value)}
                required
              />
              <button
                type="button"
                onClick={() => setShowPassword(!showPassword)}
                className="absolute right-3 top-[38px] text-gray-400 hover:text-gray-600"
                tabIndex={-1}
              >
                {showPassword ? <EyeOff size={18} /> : <Eye size={18} />}
              </button>
            </div>
            <Input
              label="Confirm Password"
              type={showPassword ? 'text' : 'password'}
              placeholder="Re-enter password"
              icon={<Lock size={18} />}
              value={form.confirmPassword}
              onChange={(e) => update('confirmPassword', e.target.value)}
              required
            />
          </div>
        )}

        {/* Step 2: OTP Verification */}
        {step === 2 && (
          <div className="space-y-4">
            <div className="text-center mb-2">
              <div className="w-14 h-14 mx-auto rounded-full bg-[var(--brand-teal)]/10 flex items-center justify-center mb-3">
                <Mail size={24} className="text-[var(--brand-teal)]" />
              </div>
              <h2 className="text-lg font-bold text-black">Verify Your Email</h2>
              <p className="text-sm text-black">
                We sent a 6-digit code to <strong>{form.email}</strong>
              </p>
            </div>

            {otpVerified ? (
              <div className="text-center py-4">
                <div className="w-16 h-16 mx-auto rounded-full bg-[var(--success-green)]/10 flex items-center justify-center mb-3">
                  <CheckCircle2 size={32} className="text-[var(--success-green)]" />
                </div>
                <p className="text-sm font-semibold text-[var(--success-green)]">Email verified successfully</p>
              </div>
            ) : (
              <>
                <Input
                  label="Verification Code"
                  placeholder="Enter 6-digit code"
                  value={form.otpCode}
                  onChange={(e) => update('otpCode', e.target.value.replace(/\D/g, '').slice(0, 6))}
                  className="text-center text-lg tracking-[0.3em] font-bold"
                  maxLength={6}
                  required
                />
                <Button fullWidth onClick={handleVerifyOtp} loading={loading}>
                  Verify Code
                </Button>
                <button
                  type="button"
                  onClick={handleSendOtp}
                  className="w-full text-center text-sm font-medium text-[var(--brand-teal)] py-1"
                >
                  Resend Code
                </button>
              </>
            )}
          </div>
        )}

        {/* Step 3: Professional Details */}
        {step === 3 && (
          <div className="space-y-4">
            <div className="text-center mb-2">
              <h2 className="text-lg font-bold text-black">Professional Details</h2>
              <p className="text-sm text-black">Tell us about your medical practice</p>
            </div>

            <Select
              label="Specialty"
              options={SPECIALTIES}
              placeholder="Select your specialty"
              value={form.specialty}
              onChange={(e) => update('specialty', e.target.value)}
              required
            />
            <Input
              label="License Number"
              placeholder="e.g. MCT-12345"
              icon={<FileText size={18} />}
              value={form.licenseNumber}
              onChange={(e) => update('licenseNumber', e.target.value)}
              required
            />
            <Input
              label="Years of Experience"
              type="number"
              placeholder="e.g. 5"
              icon={<Stethoscope size={18} />}
              value={form.yearsExperience}
              onChange={(e) => update('yearsExperience', e.target.value)}
              min="0"
              max="60"
              required
            />
          </div>
        )}

        {/* Step 4: Bio, Languages, Services */}
        {step === 4 && (
          <div className="space-y-4">
            <div className="text-center mb-2">
              <h2 className="text-lg font-bold text-black">Profile Details</h2>
              <p className="text-sm text-black">Help patients get to know you</p>
            </div>

            <TextArea
              label="Short Bio"
              placeholder="Write a brief description about yourself and your practice..."
              value={form.bio}
              onChange={(e) => update('bio', e.target.value)}
              maxLength={500}
              required
            />
            <p className="text-xs text-[var(--subtitle-grey)] text-right -mt-2">{form.bio.length}/500</p>

            <div>
              <label className="block text-sm font-semibold text-black mb-2">
                Languages Spoken <span className="text-[var(--error-red)]">*</span>
              </label>
              <div className="flex flex-wrap gap-2">
                {LANGUAGES.map((lang) => (
                  <button
                    key={lang}
                    type="button"
                    onClick={() => toggleArrayItem('languages', lang)}
                    className={`px-3 py-1.5 text-xs font-medium rounded-full border transition-colors ${
                      form.languages.includes(lang)
                        ? 'bg-[var(--brand-teal)] text-white border-[var(--brand-teal)]'
                        : 'bg-white text-black border-[var(--card-border)] hover:border-[var(--brand-teal)]'
                    }`}
                  >
                    {lang}
                  </button>
                ))}
              </div>
            </div>

            <div>
              <label className="block text-sm font-semibold text-black mb-2">
                Services Offered <span className="text-[var(--error-red)]">*</span>
              </label>
              <div className="flex flex-wrap gap-2">
                {SERVICES.map((svc) => (
                  <button
                    key={svc}
                    type="button"
                    onClick={() => toggleArrayItem('services', svc)}
                    className={`px-3 py-1.5 text-xs font-medium rounded-full border transition-colors ${
                      form.services.includes(svc)
                        ? 'bg-[var(--brand-teal)] text-white border-[var(--brand-teal)]'
                        : 'bg-white text-black border-[var(--card-border)] hover:border-[var(--brand-teal)]'
                    }`}
                  >
                    {svc}
                  </button>
                ))}
              </div>
            </div>
          </div>
        )}

        {/* Step 5: Upload Credentials */}
        {step === 5 && (
          <div className="space-y-4">
            <div className="text-center mb-2">
              <h2 className="text-lg font-bold text-black">Upload Credentials</h2>
              <p className="text-sm text-black">Verify your identity and license</p>
            </div>

            <input
              ref={licenseInputRef}
              type="file"
              accept="image/*,.pdf"
              className="hidden"
              onChange={(e) => update('licenseImage', e.target.files?.[0] ?? null)}
            />
            <input
              ref={idInputRef}
              type="file"
              accept="image/*,.pdf"
              className="hidden"
              onChange={(e) => update('idImage', e.target.files?.[0] ?? null)}
            />

            <button
              type="button"
              onClick={() => licenseInputRef.current?.click()}
              className={`w-full p-4 border-2 border-dashed rounded-xl text-center transition-colors ${
                form.licenseImage
                  ? 'border-[var(--success-green)] bg-[var(--success-green)]/5'
                  : 'border-[var(--card-border)] hover:border-[var(--brand-teal)]'
              }`}
            >
              {form.licenseImage ? (
                <div className="flex items-center justify-center gap-2">
                  <CheckCircle2 size={20} className="text-[var(--success-green)]" />
                  <span className="text-sm font-medium text-black">{form.licenseImage.name}</span>
                </div>
              ) : (
                <>
                  <Upload size={28} className="mx-auto text-gray-400 mb-2" />
                  <p className="text-sm font-semibold text-black">Medical License</p>
                  <p className="text-xs text-[var(--subtitle-grey)]">Upload image or PDF</p>
                </>
              )}
            </button>

            <button
              type="button"
              onClick={() => idInputRef.current?.click()}
              className={`w-full p-4 border-2 border-dashed rounded-xl text-center transition-colors ${
                form.idImage
                  ? 'border-[var(--success-green)] bg-[var(--success-green)]/5'
                  : 'border-[var(--card-border)] hover:border-[var(--brand-teal)]'
              }`}
            >
              {form.idImage ? (
                <div className="flex items-center justify-center gap-2">
                  <CheckCircle2 size={20} className="text-[var(--success-green)]" />
                  <span className="text-sm font-medium text-black">{form.idImage.name}</span>
                </div>
              ) : (
                <>
                  <Upload size={28} className="mx-auto text-gray-400 mb-2" />
                  <p className="text-sm font-semibold text-black">Government-Issued ID</p>
                  <p className="text-xs text-[var(--subtitle-grey)]">National ID, passport, or driver&apos;s license</p>
                </>
              )}
            </button>

            <p className="text-xs text-[var(--subtitle-grey)] text-center">
              Files are encrypted and stored securely. Only used for verification.
            </p>
          </div>
        )}

        {/* Step 6: Profile Photo */}
        {step === 6 && (
          <div className="space-y-4">
            <div className="text-center mb-2">
              <h2 className="text-lg font-bold text-black">Profile Photo</h2>
              <p className="text-sm text-black">Help patients recognize you (optional)</p>
            </div>

            <input
              ref={photoInputRef}
              type="file"
              accept="image/*"
              className="hidden"
              onChange={(e) => update('profilePhoto', e.target.files?.[0] ?? null)}
            />

            <div className="flex flex-col items-center py-4">
              <button
                type="button"
                onClick={() => photoInputRef.current?.click()}
                className="w-32 h-32 rounded-full border-2 border-dashed border-[var(--card-border)] hover:border-[var(--brand-teal)] flex items-center justify-center overflow-hidden transition-colors"
              >
                {form.profilePhoto ? (
                  <img
                    src={URL.createObjectURL(form.profilePhoto)}
                    alt="Profile preview"
                    className="w-full h-full object-cover"
                  />
                ) : (
                  <Camera size={36} className="text-gray-300" />
                )}
              </button>
              <button
                type="button"
                onClick={() => photoInputRef.current?.click()}
                className="mt-3 text-sm font-medium text-[var(--brand-teal)]"
              >
                {form.profilePhoto ? 'Change Photo' : 'Upload Photo'}
              </button>
              {form.profilePhoto && (
                <button
                  type="button"
                  onClick={() => update('profilePhoto', null)}
                  className="mt-1 text-xs text-[var(--error-red)]"
                >
                  Remove
                </button>
              )}
            </div>
          </div>
        )}

        {/* Step 7: Terms */}
        {step === 7 && (
          <div className="space-y-4">
            <div className="text-center mb-2">
              <div className="w-14 h-14 mx-auto rounded-full bg-[var(--royal-purple)]/10 flex items-center justify-center mb-3">
                <Shield size={24} className="text-[var(--royal-purple)]" />
              </div>
              <h2 className="text-lg font-bold text-black">Terms & Conditions</h2>
              <p className="text-sm text-black">Please review and accept to continue</p>
            </div>

            <div className="bg-gray-50 rounded-xl p-4 max-h-48 overflow-y-auto text-xs text-black leading-relaxed space-y-2">
              <p><strong>eSIRI Plus Doctor Terms of Service</strong></p>
              <p>By registering as a doctor on eSIRI Plus, you agree to:</p>
              <ul className="list-disc pl-4 space-y-1">
                <li>Provide accurate and truthful professional information.</li>
                <li>Maintain a valid medical license throughout your use of the platform.</li>
                <li>Deliver consultations with the highest standard of care.</li>
                <li>Keep patient information confidential and comply with medical privacy laws.</li>
                <li>Respond to consultations in a timely manner when marked as available.</li>
                <li>Accept the platform&apos;s commission structure and payment terms.</li>
                <li>Not engage in any fraudulent or unethical medical practices.</li>
              </ul>
              <p>eSIRI Plus reserves the right to suspend or terminate accounts that violate these terms.</p>
            </div>

            <label className="flex items-start gap-3 cursor-pointer">
              <input
                type="checkbox"
                checked={form.termsAccepted}
                onChange={(e) => update('termsAccepted', e.target.checked)}
                className="mt-0.5 w-5 h-5 rounded border-[var(--card-border)] text-[var(--brand-teal)] focus:ring-[var(--brand-teal)] accent-[var(--brand-teal)]"
              />
              <span className="text-sm text-black">
                I have read and agree to the <strong>Terms of Service</strong> and <strong>Privacy Policy</strong>
              </span>
            </label>
          </div>
        )}

        {/* Error */}
        {error && (
          <p className="mt-3 text-sm text-[var(--error-red)] text-center">{error}</p>
        )}

        {/* Navigation buttons */}
        <div className="flex gap-3 mt-6">
          {step > 1 && (
            <Button variant="outline" onClick={handleBack} className="flex items-center gap-1">
              <ChevronLeft size={16} /> Back
            </Button>
          )}
          <Button
            fullWidth
            onClick={handleNext}
            loading={loading}
            className="flex items-center justify-center gap-1"
          >
            {step === TOTAL_STEPS ? 'Complete Registration' : (
              <>Next <ChevronRight size={16} /></>
            )}
          </Button>
        </div>

        {step === 1 && (
          <p className="mt-4 text-center text-sm text-black">
            Already have an account?{' '}
            <Link href="/login" className="font-semibold text-[var(--brand-teal)]">Sign In</Link>
          </p>
        )}
      </Card>
    </div>
  );
}
