/**
 * Static medication database — 74 medications bundled client-side.
 * Injectable detection: name contains " inj" (case-insensitive).
 */
export const MEDICATIONS: string[] = [
  // ANTIBIOTICS & ANTI-INFECTIVE (30)
  'Amoxycillin trihydrate 500mg BP caps',
  'Ceftriaxone 1gm vial USP 30',
  'Amoxicillin + Ac. Clavulanate 500/125mg tab',
  'Ciprofloxacine 500mg tab USP',
  'Sulphamethoxazole 400mg + Trimethoprim 80mg tabs BP',
  'Gentamycine 80mg/2ml inj BP09',
  'Amoxicilline 250mg caps BP',
  'Neomycine + Bacitracin (0.5% + 500IU/gm) ointment',
  'Streptomycine 1gm inj BP',
  'Gentamycine 20mg/2ml inj BP09',
  'Ceftazidime 1gm inj',
  'Cefotaxime 1gm + WFI inj USP',
  'Amoxicilline + Ac. Clavulanate 1gm/200mg inj',
  'Kanamycine 1gm inj BP',
  'Erythromycine 500mg tabs BP',
  'Clanoxy 1.2gm inj (Amox + Pot. Clav)',
  'Ceftriaxone 500mg inj',
  'Amoxicillin and clavulanate potassium tabs USP',
  'Cefixime tabs USP 200mg',
  'Keftaz 1000 (Ceftazidime for inj USP 1gm)',
  'Clanoxy 625mg (Amox + Pot Clav) tabs USP',
  'Chloramphenicol inj BP',
  'Gentamicine inj 80mg BP',
  'Amoxicillin 500mg and clavulanate potassium 62.5mg tabs USP',
  'Chloramphenicol inj 1gm BP',
  'Imipenem + Cilastatin 500mg',
  'Teicoplanin 400mg',
  'Vancomycin 500mg/1gm',
  'Meropenem 500mg',
  'Cefepime 1gm',
  // SEDATIVES & HYPNOTICS (1)
  'Diazepam 5mg/ml, 2ml inj BP',
  // ANTI-FUNGAL (3)
  'Miconazole 2% cream',
  'Nystatine 100000 IU ointment BP',
  'Amphotericine B for inj USP (50mg/vial)',
  // ANTI-HELMINTHICS (2)
  'Metronidazole 250mg tabs BP',
  'Helmanil tabs (Albendazole)',
  // ANTI-VIRAL (2)
  'Acyclovir 3% 5gm eye ointment BP',
  'Acyclovir 5% 10gm cream',
  // NSAIDs (4)
  'Paracetamol 500mg tabs BP 09',
  'Diclofenac 75mg/3ml inj BP',
  'Ibuprofen 200mg tabs BP',
  'Ibuprofen 400mg tab',
  // ANTI-MALARIALS (5)
  'Quinine base 600mg/2ml inj',
  'Quinine inj 100mg/ml, 2ml inj',
  'Quinine 100mg/ml inj amp 2.4ml',
  'Quinine 300mg (2ml amp) BP',
  'Artemether 80mg',
  // ANTI-CHOLINERGICS (1)
  'Atropine sulphate 1mg inj',
  // ANTI-SPASMODICS (2)
  'Hyoscine butyl bromide injection BP',
  'N-butyl hyoscine bromide inj 20mg',
  // STEROIDAL ANTI-INFLAMMATORY (4)
  'Hydrocortisone 100mg inj BP',
  'Dexamethasone 4mg inj BP',
  'Betamethasone 0.1%, 5gm cream BP',
  'Hydrocortisone sodium succinate 100mg',
  // ELECTROLYTE REPLENISHERS (3)
  'Calcium gluconate inj BP',
  'Sodium chloride inj',
  'Potassium chloride inj',
  // VITAMINS (2)
  'Cyanocobalamine (Hydroxocobalamine) 1mg inj',
  'Ascorbic acid inj 500mg BP',
  // GYNAECOLOGY (4)
  'Ergometrine inj BP',
  'Oxytocin inj 10IU/ml, 1ml BP',
  'Oxytocin inj 5IU/ml, 1ml amp BP',
  'Methylergometrine 0.2mg/ml, 1ml inj USP',
  // DIURETICS (1)
  'Frusemide 10mg/ml, 2ml inj BP',
  // ANAESTHETICS (16)
  'Propofol 1gm inj BP',
  'Propofol inj (1% w/v) BP',
  'Ketamine 50mg/ml 10ml inj BP',
  'Haloperidol 5mg/ml, 1ml inj',
  'Bupivacaine 0.25% (950mg/20ml) inj BP',
  'Thiopental 0.5gm inj BP',
  'Lignocaine injection BP',
  'Lidocaine injection',
  'Bupivacaine 0.5% inj BP',
  'Vecuronium bromide 4mg & 10mg',
  'Pancuronium bromide BP 4mg',
  'Ropivacaine hydrochloride 40/150/200',
  'Atracurium besylate USP 10mg/ml',
  'Suxamethonium chloride 100mg/2ml inj',
  'Neostigmine inj 0.5mg/ml BP',
  'Lignocaine hydrochloride & dextrose inj USP',
  'Lidocaine 2% + adrenaline inj',
  'Bupivacaine rachi anaesthesia',
  // COAGULANTS & ANTI-COAGULANTS (3)
  'Vitamin K1 (Phytonadione) 10mg/ml inj BP',
  'Heparinate sodium inj 5000IU/ml',
  'Ethamsylate 250mg/2ml inj',
  // ANTI-EPILEPTIC (2)
  'Phenobarbital inj 100mg/ml, 2ml amp',
  'Phenobarbital 200mg/ml',
];

export function isInjectable(name: string): boolean {
  return name.toLowerCase().includes(' inj');
}

export function searchMedications(query: string, excludeNames: string[]): string[] {
  if (query.length < 2) return [];
  const q = query.toLowerCase();
  return MEDICATIONS
    .filter((med) => med.toLowerCase().includes(q) && !excludeNames.includes(med))
    .slice(0, 6);
}

export type PrescriptionForm = 'Tablets' | 'Syrup' | 'Injection';
export type InjectionRoute = 'IM' | 'IV' | 'SC';

export function formatDosage(form: PrescriptionForm, quantity: number, timesPerDay: number, days: number, route?: string): string {
  if (form === 'Injection') {
    return `${route || 'IM'}, ${timesPerDay} time${timesPerDay !== 1 ? 's' : ''} per day \u00D7 ${days} day${days !== 1 ? 's' : ''}`;
  }
  const unit = form === 'Syrup' ? 'ml' : `tablet${quantity !== 1 ? 's' : ''}`;
  const prefix = form === 'Syrup' ? `Take ${quantity}ml` : `Take ${quantity} ${unit}`;
  return `${prefix} \u00D7 ${timesPerDay} time${timesPerDay !== 1 ? 's' : ''} per day \u00D7 ${days} day${days !== 1 ? 's' : ''}`;
}
