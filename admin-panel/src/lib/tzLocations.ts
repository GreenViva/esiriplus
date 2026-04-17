// Tanzania administrative districts used for location-based offers.
// Kept intentionally short — extend as the business expands. The offer schema
// also accepts free-text district, so admins can target areas not listed here.

export interface TzDistrict {
  region: string;
  district: string;
  wards?: string[];
}

export const TZ_DISTRICTS: TzDistrict[] = [
  {
    region: "Dar es Salaam",
    district: "Ubungo",
    wards: ["Kibamba", "Kimara", "Mbezi", "Goba", "Makuburi", "Sinza", "Mburahati", "Manzese"],
  },
  {
    region: "Dar es Salaam",
    district: "Kinondoni",
    wards: ["Kawe", "Msasani", "Mikocheni", "Mwananyamala", "Magomeni", "Kijitonyama"],
  },
  {
    region: "Dar es Salaam",
    district: "Ilala",
    wards: ["Upanga", "Kariakoo", "Buguruni", "Ilala", "Tabata", "Segerea", "Kivukoni"],
  },
  {
    region: "Dar es Salaam",
    district: "Temeke",
    wards: ["Mbagala", "Chamazi", "Kurasini", "Mtoni", "Tandika", "Azimio"],
  },
  {
    region: "Dar es Salaam",
    district: "Kigamboni",
    wards: ["Kigamboni", "Vijibweni", "Kibada", "Tungi", "Mjimwema"],
  },
  { region: "Dodoma",    district: "Dodoma Urban" },
  { region: "Mwanza",    district: "Nyamagana" },
  { region: "Mwanza",    district: "Ilemela" },
  { region: "Arusha",    district: "Arusha Urban" },
  { region: "Arusha",    district: "Arusha Rural" },
  { region: "Mbeya",     district: "Mbeya Urban" },
  { region: "Tanga",     district: "Tanga Urban" },
  { region: "Zanzibar",  district: "Urban West" },
  { region: "Morogoro",  district: "Morogoro Urban" },
];

export const SERVICE_TYPES = [
  { value: "nurse",            label: "Nurse" },
  { value: "clinical_officer", label: "Clinical Officer" },
  { value: "pharmacist",       label: "Pharmacist" },
  { value: "gp",               label: "General Practitioner" },
  { value: "specialist",       label: "Specialist" },
  { value: "psychologist",     label: "Psychologist" },
] as const;

export const TIERS = [
  { value: "ECONOMY", label: "Economy" },
  { value: "ROYAL",   label: "Royal" },
] as const;
