'use client';

import { useState, useEffect, use } from 'react';
import { useRouter } from 'next/navigation';
import { ArrowLeft, Download, Share2 } from 'lucide-react';
import { useAuthStore } from '@/store/auth';
import { useSupabase } from '@/hooks/useSupabase';
import { Button } from '@/components/ui';

function formatDate(d: string | number) {
  if (!d) return '';
  const date = new Date(d);
  return date.toLocaleDateString('en-GB', {
    day: '2-digit',
    month: 'long',
    year: 'numeric',
  });
}

export default function ReportDetailPage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const { id: reportId } = use(params);
  const router = useRouter();
  const db = useSupabase();
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const [report, setReport] = useState<Record<string, any> | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    if (!db) return;
    async function loadReport() {
      try {
        const { data } = await db!
          .from('consultation_reports')
          .select('*')
          .eq('report_id', reportId)
          .single();

        if (data) {
          let prescriptions = data.prescriptions;
          if (typeof prescriptions === 'string') {
            try { prescriptions = JSON.parse(prescriptions); } catch { prescriptions = []; }
          }
          if (!Array.isArray(prescriptions)) prescriptions = [];
          setReport({ ...data, prescriptions });
        }
      } catch { /* handle error */ } finally {
        setLoading(false);
      }
    }
    loadReport();
  }, [reportId, db]);

  const g = (key: string, snake?: string) =>
    (report?.[key] ?? report?.[snake ?? key] ?? '') as string;

  async function handleDownloadPDF() {
    if (!report) return;
    const { jsPDF } = await import('jspdf');
    const doc = new jsPDF({ unit: 'mm', format: 'a4' });
    const W = 210;
    const margin = 15;
    const contentW = W - margin * 2;
    let y = 15;

    const teal: [number, number, number] = [42, 157, 143];
    const black: [number, number, number] = [0, 0, 0];
    const grey: [number, number, number] = [107, 114, 128];
    const bg: [number, number, number] = [248, 255, 254];

    // Helper: wrapped text, returns new Y
    function addText(text: string, x: number, maxW: number, size: number, color: [number, number, number], style: string = 'normal'): number {
      doc.setFontSize(size);
      doc.setTextColor(...color);
      doc.setFont('helvetica', style);
      const lines = doc.splitTextToSize(text || '', maxW);
      if (y + lines.length * (size * 0.4) > 280) { doc.addPage(); y = 15; }
      doc.text(lines, x, y);
      return y + lines.length * (size * 0.4) + 2;
    }

    function addSection(title: string) {
      if (y > 265) { doc.addPage(); y = 15; }
      y += 4;
      doc.setFontSize(12);
      doc.setTextColor(...teal);
      doc.setFont('helvetica', 'bold');
      doc.text(title, margin, y);
      y += 6;
    }

    function addInfoRow(label: string, value: string) {
      if (y > 275) { doc.addPage(); y = 15; }
      doc.setFontSize(9);
      doc.setFont('helvetica', 'bold');
      doc.setTextColor(...grey);
      doc.text(label + ':', margin, y);
      doc.setFont('helvetica', 'normal');
      doc.setTextColor(...black);
      doc.text(value || '-', margin + 50, y);
      y += 5;
    }

    function addProse(text: string) {
      doc.setFillColor(...bg);
      const lines = doc.splitTextToSize(text || '-', contentW - 6);
      const blockH = lines.length * 4 + 6;
      if (y + blockH > 280) { doc.addPage(); y = 15; }
      doc.roundedRect(margin, y - 2, contentW, blockH, 2, 2, 'F');
      doc.setFontSize(9);
      doc.setTextColor(...black);
      doc.setFont('helvetica', 'normal');
      doc.text(lines, margin + 3, y + 2);
      y += blockH + 3;
    }

    // ── Header ──
    doc.setFillColor(...teal);
    doc.rect(0, 0, W, 30, 'F');
    doc.setFontSize(20);
    doc.setTextColor(255, 255, 255);
    doc.setFont('helvetica', 'bold');
    doc.text('eSIRI HEALTH', W / 2, 14, { align: 'center' });
    doc.setFontSize(10);
    doc.setFont('helvetica', 'normal');
    doc.text('Telemedicine Consultation Report', W / 2, 22, { align: 'center' });
    y = 35;

    // ── Info bar ──
    doc.setFontSize(8);
    doc.setTextColor(...teal);
    doc.setFont('helvetica', 'bold');
    doc.text('CONSULTATION REPORT', margin, y);
    if (verificationCode) {
      doc.setTextColor(...grey);
      doc.setFont('helvetica', 'normal');
      doc.text('Ref: ' + verificationCode, margin, y + 4);
    }
    if (consultDate) {
      doc.setTextColor(...black);
      doc.setFont('helvetica', 'normal');
      doc.text(formatDate(consultDate), W - margin, y, { align: 'right' });
    }
    y += 10;
    doc.setDrawColor(...teal);
    doc.setLineWidth(0.5);
    doc.line(margin, y, W - margin, y);
    y += 6;

    // ── Patient Info ──
    addSection('Patient Information');
    if (patientSessionId) addInfoRow('Patient ID', patientSessionId.slice(0, 12) + '...');
    if (consultDate) addInfoRow('Consultation Date', formatDate(consultDate));
    addInfoRow('Consultation Type', 'Telemedicine');

    // ── Symptoms ──
    addSection('Presenting Symptoms');
    addProse(symptoms || 'No symptoms recorded');

    // ── Diagnosis ──
    addSection('Diagnosis & Assessment');
    if (diagnosis) addInfoRow('Primary Diagnosis', diagnosis);
    if (category) addInfoRow('Category', category);
    if (severity) addInfoRow('Severity', severity);
    if (assessment) addProse(assessment);

    // ── Treatment ──
    addSection('Treatment Plan');
    addProse(treatmentPlan || 'No treatment plan recorded');

    if (prescribedMedications) {
      addSection('Prescribed Medications');
      addProse(prescribedMedications);
    }

    // ── Prescriptions ──
    if (prescriptions.length > 0) {
      addSection('Prescriptions');
      for (const rx of prescriptions) {
        const name = rx.medication ?? rx.medication_name ?? '';
        const form = rx.form ?? '';
        const dosageStr = (rx.dosage ?? '') as string;
        const qtyMatch = dosageStr.match(/^(\d+)\s/);
        const freqMatch = dosageStr.match(/(\d+)x\/day/);
        const durMatch = dosageStr.match(/(\d+)\s*days/);
        const qty = rx.quantity ?? (qtyMatch ? qtyMatch[1] : '-');
        const freq = rx.timesPerDay ?? rx.times_per_day ?? rx.frequency ?? (freqMatch ? freqMatch[1] : '-');
        const dur = rx.days ?? rx.duration ?? (durMatch ? durMatch[1] : '-');
        if (y > 270) { doc.addPage(); y = 15; }
        doc.setFontSize(9);
        doc.setFont('helvetica', 'bold');
        doc.setTextColor(...black);
        doc.text(`${name} (${form})`, margin, y);
        y += 4;
        doc.setFont('helvetica', 'normal');
        doc.setTextColor(...grey);
        doc.text(`Qty: ${qty}  |  ${freq}x/day  |  ${dur} days`, margin, y);
        y += 6;
      }
    }

    // ── Follow-up ──
    addSection('Follow-up Instructions');
    addInfoRow('Follow-up Recommended', followUpRecommended ? 'Yes' : 'No');
    if (followUpPlan) addProse(followUpPlan);
    if (furtherNotes) {
      y += 2;
      y = addText('Additional Notes:', margin, contentW, 9, black, 'bold');
      addProse(furtherNotes);
    }

    // ── Disclaimer ──
    addSection('Telemedicine Disclaimer');
    doc.setFillColor(255, 251, 235);
    const discText = 'This report was generated from a telemedicine consultation. It is intended for informational purposes and does not replace an in-person medical examination. Please consult your healthcare provider for definitive diagnosis and treatment.';
    const discLines = doc.splitTextToSize(discText, contentW - 6);
    const discH = discLines.length * 4 + 6;
    if (y + discH > 280) { doc.addPage(); y = 15; }
    doc.roundedRect(margin, y - 2, contentW, discH, 2, 2, 'F');
    doc.setFontSize(8);
    doc.setTextColor(146, 64, 14);
    doc.setFont('helvetica', 'italic');
    doc.text(discLines, margin + 3, y + 2);
    y += discH + 6;

    // ── Signature ──
    if (y > 265) { doc.addPage(); y = 15; }
    doc.setDrawColor(229, 231, 235);
    doc.line(margin, y, W - margin, y);
    y += 6;
    if (doctorName) {
      doc.setFontSize(12);
      doc.setFont('helvetica', 'bold');
      doc.setTextColor(...black);
      doc.text('Dr. ' + doctorName, margin, y);
      y += 5;
      doc.setFontSize(9);
      doc.setFont('helvetica', 'normal');
      doc.setTextColor(...grey);
      doc.text('Attending Physician', margin, y);
      y += 5;
    }
    doc.setFontSize(8);
    doc.setFont('helvetica', 'italic');
    doc.setTextColor(...grey);
    doc.text('Electronically signed via eSIRI Plus', margin, y);
    y += 8;

    // ── Footer ──
    doc.setFillColor(243, 244, 246);
    doc.roundedRect(margin, y, contentW, 10, 2, 2, 'F');
    doc.setFontSize(8);
    doc.setTextColor(...grey);
    doc.setFont('helvetica', 'normal');
    doc.text('Generated by eSIRI Plus Telemedicine Platform', W / 2, y + 6, { align: 'center' });

    doc.save(`eSIRI-Report-${reportId.slice(0, 8)}.pdf`);
  }

  if (loading) {
    return (
      <div className="min-h-dvh bg-white">
        <div className="sticky top-0 z-10 bg-white border-b px-5 py-4 flex items-center gap-3">
          <button onClick={() => router.back()} className="p-1.5 -ml-1.5 rounded-xl hover:bg-gray-100">
            <ArrowLeft size={22} className="text-black" />
          </button>
          <h1 className="text-lg font-bold text-black">Medical Report</h1>
        </div>
        <div className="px-5 py-6 space-y-4">
          {[1, 2, 3, 4].map((i) => (
            <div key={i} className="h-24 bg-gray-100 rounded-xl animate-pulse" />
          ))}
        </div>
      </div>
    );
  }

  if (!report) {
    return (
      <div className="min-h-dvh bg-white flex flex-col items-center justify-center px-5">
        <p className="text-base font-bold text-black mb-4">Report not found</p>
        <Button variant="outline" onClick={() => router.push('/reports')}>Back to Reports</Button>
      </div>
    );
  }

  const doctorName = g('doctor_name', 'doctorName');
  const consultDate = g('consultation_date', 'consultationDate');
  const verificationCode = g('verification_code', 'verificationCode');
  const symptoms = g('presenting_symptoms', 'presentingSymptoms');
  const diagnosis = g('diagnosed_problem', 'diagnosedProblem');
  const category = g('category');
  const severity = g('severity');
  const assessment = g('assessment');
  const treatmentPlan = g('treatment_plan', 'treatmentPlan');
  const prescribedMedications = g('prescribed_medications', 'prescribedMedications');
  const followUpPlan = g('follow_up_plan', 'followUpPlan');
  const followUpRecommended = report.follow_up_recommended ?? report.followUpRecommended ?? false;
  const furtherNotes = g('further_notes', 'furtherNotes');
  const prescriptions = report.prescriptions ?? [];
  const patientSessionId = g('patient_session_id', 'patientSessionId');

  return (
    <div className="min-h-dvh bg-white">
      {/* Header bar */}
      <div className="sticky top-0 z-10 bg-white border-b px-5 py-4 flex items-center gap-3">
        <button onClick={() => router.back()} className="p-1.5 -ml-1.5 rounded-xl hover:bg-gray-100 transition-colors">
          <ArrowLeft size={22} className="text-black" />
        </button>
        <h1 className="text-lg font-bold text-black flex-1">Medical Report</h1>
        <button onClick={handleDownloadPDF} className="p-2 rounded-xl hover:bg-gray-100 transition-colors">
          <Download size={20} className="text-[#2A9D8F]" />
        </button>
      </div>

      <div className="px-5 py-5 pb-28">
        {/* ── Teal Header ── */}
        <div className="bg-[#2A9D8F] rounded-t-xl px-6 py-6 text-center">
          <h2 className="text-2xl font-bold text-white tracking-widest">eSIRI HEALTH</h2>
          <p className="text-sm text-white/90 mt-1">Telemedicine Consultation Report</p>
        </div>

        {/* ── Consultation Info Bar ── */}
        <div className="bg-[#F8FFFE] border-x border-gray-200 px-5 py-3 flex items-center justify-between">
          <div>
            <p className="text-xs font-bold text-[#2A9D8F] tracking-wider uppercase">Consultation Report</p>
            {verificationCode && (
              <p className="text-[11px] text-gray-500 mt-0.5">Ref: {verificationCode}</p>
            )}
          </div>
          {consultDate && (
            <p className="text-xs font-medium text-black">{formatDate(consultDate)}</p>
          )}
        </div>

        <div className="border-x border-b border-gray-200 rounded-b-xl">
          {/* Teal divider */}
          <div className="h-[2px] bg-[#2A9D8F]" />

          <div className="px-5 py-5 space-y-6">
            {/* ── Section 1: Patient Information ── */}
            <section>
              <SectionHeader>Patient Information</SectionHeader>
              <div className="mt-2 space-y-1">
                {patientSessionId && (
                  <InfoRow label="Patient ID" value={patientSessionId.slice(0, 12) + '...'} />
                )}
                {consultDate && <InfoRow label="Consultation Date" value={formatDate(consultDate)} />}
                <InfoRow label="Consultation Type" value="Telemedicine" />
              </div>
            </section>

            {/* ── Section 2: Presenting Symptoms ── */}
            <section>
              <SectionHeader>Presenting Symptoms</SectionHeader>
              <ProseBlock>{symptoms || 'No symptoms recorded'}</ProseBlock>
            </section>

            {/* ── Section 3: Diagnosis & Assessment ── */}
            <section>
              <SectionHeader>Diagnosis &amp; Assessment</SectionHeader>
              <div className="mt-2 space-y-1">
                {diagnosis && <InfoRow label="Primary Diagnosis" value={diagnosis} />}
                {category && <InfoRow label="Category" value={category} />}
                {severity && <InfoRow label="Severity" value={severity} />}
              </div>
              {assessment && <ProseBlock className="mt-3">{assessment}</ProseBlock>}
            </section>

            {/* ── Section 4: Treatment Plan ── */}
            <section>
              <SectionHeader>Treatment Plan</SectionHeader>
              <ProseBlock>{treatmentPlan || 'No treatment plan recorded'}</ProseBlock>
            </section>

            {/* ── Section 4b: Prescribed Medications (text) ── */}
            {prescribedMedications && (
              <section>
                <SectionHeader>Prescribed Medications</SectionHeader>
                <ProseBlock>{prescribedMedications}</ProseBlock>
              </section>
            )}

            {/* ── Section 4c: Prescriptions (structured) ── */}
            {prescriptions.length > 0 && (
              <section>
                <SectionHeader>Prescriptions</SectionHeader>
                <div className="mt-2 space-y-3">
                  {prescriptions.map((rx: Record<string, unknown>, idx: number) => {
                    // Parse structured fields, or extract from dosage string as fallback
                    const dosageStr = (rx.dosage ?? '') as string;
                    const qtyMatch = dosageStr.match(/^(\d+)\s/);
                    const freqMatch = dosageStr.match(/(\d+)x\/day/);
                    const durMatch = dosageStr.match(/(\d+)\s*days/);

                    const qty = rx.quantity ?? rx.quantityPerDose ?? (qtyMatch ? qtyMatch[1] : '-');
                    const freq = rx.timesPerDay ?? rx.times_per_day ?? rx.frequency ?? (freqMatch ? freqMatch[1] : '-');
                    const dur = rx.days ?? rx.duration ?? (durMatch ? durMatch[1] : '-');

                    return (
                    <div key={idx} className="bg-[#F8FFFE] rounded-lg p-4 border border-gray-100">
                      <p className="text-sm font-bold text-black">{(rx.medication ?? rx.medication_name ?? '') as string}</p>
                      <p className="text-xs text-gray-500 mt-0.5">
                        {(rx.form ?? '') as string}{rx.route ? ` (${rx.route})` : ''}
                      </p>
                      {dosageStr && !rx.quantity && (
                        <p className="text-xs text-[#2A9D8F] mt-1">{dosageStr}</p>
                      )}
                      <div className="grid grid-cols-3 gap-3 mt-3 pt-3 border-t border-gray-100">
                        <div>
                          <p className="text-[10px] text-gray-400 uppercase tracking-wide">Quantity</p>
                          <p className="text-sm font-bold text-black">{String(qty)}</p>
                        </div>
                        <div>
                          <p className="text-[10px] text-gray-400 uppercase tracking-wide">Frequency</p>
                          <p className="text-sm font-bold text-black">{String(freq)}x/day</p>
                        </div>
                        <div>
                          <p className="text-[10px] text-gray-400 uppercase tracking-wide">Duration</p>
                          <p className="text-sm font-bold text-black">{String(dur)} days</p>
                        </div>
                      </div>
                    </div>
                    );
                  })}
                </div>
              </section>
            )}

            {/* ── Section 5: Follow-up Instructions ── */}
            <section>
              <SectionHeader>Follow-up Instructions</SectionHeader>
              <div className="mt-2">
                <InfoRow label="Follow-up Recommended" value={followUpRecommended ? 'Yes' : 'No'} />
              </div>
              {followUpPlan && <ProseBlock className="mt-2">{followUpPlan}</ProseBlock>}
              {furtherNotes && (
                <div className="mt-3">
                  <p className="text-[13px] font-semibold text-black mb-1">Additional Notes</p>
                  <ProseBlock>{furtherNotes}</ProseBlock>
                </div>
              )}
            </section>

            {/* ── Section 6: Disclaimer ── */}
            <section>
              <SectionHeader>Telemedicine Disclaimer</SectionHeader>
              <div className="bg-[#FFFBEB] rounded-lg p-3 mt-2">
                <p className="text-xs text-[#92400E] italic leading-5">
                  This report was generated from a telemedicine consultation. It is intended for
                  informational purposes and does not replace an in-person medical examination.
                  Please consult your healthcare provider for definitive diagnosis and treatment.
                </p>
              </div>
            </section>

            {/* ── Electronic Signature ── */}
            <div className="border-t border-gray-200 pt-4">
              {doctorName && (
                <div className="mb-2">
                  <p className="text-base font-bold text-black">Dr. {doctorName}</p>
                  <p className="text-[13px] text-gray-500">Attending Physician</p>
                </div>
              )}
              <p className="text-[11px] text-gray-400 italic">Electronically signed via eSIRI Plus</p>
            </div>

            {/* ── Footer ── */}
            <div className="bg-gray-100 rounded-lg p-3 text-center">
              <p className="text-[11px] text-gray-400">Generated by eSIRI Plus Telemedicine Platform</p>
            </div>
          </div>
        </div>

        {/* Download button */}
        <button
          onClick={handleDownloadPDF}
          className="mt-6 w-full flex items-center justify-center gap-2 py-4 bg-[#2A9D8F] text-white rounded-xl text-sm font-semibold hover:opacity-90 active:scale-[0.98] transition-all"
        >
          <Download size={18} />
          Download PDF Report
        </button>
      </div>
    </div>
  );
}

function SectionHeader({ children }: { children: React.ReactNode }) {
  return (
    <h3 className="text-base font-bold text-[#2A9D8F]">{children}</h3>
  );
}

function InfoRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex py-[3px]">
      <span className="text-[13px] font-semibold text-gray-500 w-[160px] shrink-0">{label}:</span>
      <span className="text-[13px] text-black flex-1">{value}</span>
    </div>
  );
}

function ProseBlock({ children, className }: { children: React.ReactNode; className?: string }) {
  return (
    <div className={`bg-[#F8FFFE] rounded-lg p-3 mt-2 ${className ?? ''}`}>
      <p className="text-[13px] text-black leading-5">{children}</p>
    </div>
  );
}
