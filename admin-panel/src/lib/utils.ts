export function formatCurrency(amount: number): string {
  return `TZS ${amount.toLocaleString("en-US")}`;
}

export function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString("en-US", {
    year: "numeric",
    month: "short",
    day: "numeric",
  });
}

export function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString("en-US", {
    year: "numeric",
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

const serviceTypeLabels: Record<string, string> = {
  nurse: "Nurse",
  clinical_officer: "Clinical Officer",
  pharmacist: "Pharmacist",
  gp: "General Practitioner",
  general_practitioner: "General Practitioner",
  specialist: "Specialist",
  psychologist: "Psychologist",
  dermatologist: "Dermatologist",
  pediatrician: "Pediatrician",
  gynecologist: "Gynecologist",
  cardiologist: "Cardiologist",
  neurologist: "Neurologist",
  psychiatrist: "Psychiatrist",
  orthopedist: "Orthopedist",
  ophthalmologist: "Ophthalmologist",
  ent_specialist: "ENT Specialist",
  urologist: "Urologist",
  dentist: "Dentist",
  service_access: "Service Access",
  call_recharge: "Call Recharge",
};

export function serviceTypeLabel(slug: string): string {
  return serviceTypeLabels[slug] ?? slug.replace(/_/g, " ").replace(/\b\w/g, (c) => c.toUpperCase());
}

const specialtyLabels: Record<string, string> = {
  general_practitioner: "General Practitioner",
  dermatologist: "Dermatologist",
  pediatrician: "Pediatrician",
  gynecologist: "Gynecologist",
  cardiologist: "Cardiologist",
  neurologist: "Neurologist",
  psychiatrist: "Psychiatrist",
  orthopedist: "Orthopedist",
  ophthalmologist: "Ophthalmologist",
  ent_specialist: "ENT Specialist",
  urologist: "Urologist",
  dentist: "Dentist",
};

export function specialtyLabel(slug: string): string {
  return specialtyLabels[slug] ?? slug.replace(/_/g, " ").replace(/\b\w/g, (c) => c.toUpperCase());
}
