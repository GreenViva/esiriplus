"use client";

import { useCallback, useEffect, useState } from "react";
import { createClient } from "@/lib/supabase/client";

interface TzLocation {
  id: string;
  name: string;
  level: "region" | "district" | "ward" | "street";
  parent_id: string | null;
}

export interface LocationSelection {
  region: string | null;
  district: string | null;
  ward: string | null;
  street: string | null;
}

interface Props {
  value: LocationSelection;
  onChange: (value: LocationSelection) => void;
  /** When true, user can leave fields empty (=> offer applies to whole parent). */
  allowAnyLevel?: boolean;
}

/**
 * Cascading Region → District → Ward → Street picker, sourced from the
 * tz_locations table. Each level loads lazily when its parent is selected.
 * The admin can stop at any level — leaving a level "(All …)" means the
 * resulting offer targets everything below that level.
 */
export default function LocationPicker({ value, onChange, allowAnyLevel = true }: Props) {
  const [regions, setRegions] = useState<TzLocation[]>([]);
  const [districts, setDistricts] = useState<TzLocation[]>([]);
  const [wards, setWards] = useState<TzLocation[]>([]);
  const [streets, setStreets] = useState<TzLocation[]>([]);
  const [loading, setLoading] = useState({ regions: true, districts: false, wards: false, streets: false });

  const supabase = createClient();

  // ── Load regions once ──────────────────────────────────────────────────
  useEffect(() => {
    (async () => {
      const { data } = await supabase
        .from("tz_locations")
        .select("id, name, level, parent_id")
        .eq("level", "region")
        .order("sort_order", { ascending: true });
      setRegions((data as TzLocation[]) ?? []);
      setLoading((s) => ({ ...s, regions: false }));
    })();
  }, [supabase]);

  // Helper: fetch children of a named parent at a given level
  const fetchChildren = useCallback(
    async (parentName: string, parentLevel: TzLocation["level"], childLevel: TzLocation["level"]) => {
      const { data: parent } = await supabase
        .from("tz_locations")
        .select("id")
        .eq("level", parentLevel)
        .ilike("name", parentName)
        .maybeSingle();
      if (!parent) return [];
      const { data } = await supabase
        .from("tz_locations")
        .select("id, name, level, parent_id")
        .eq("level", childLevel)
        .eq("parent_id", (parent as { id: string }).id)
        .order("name", { ascending: true });
      return (data as TzLocation[]) ?? [];
    },
    [supabase],
  );

  // ── When region changes, load its districts ────────────────────────────
  useEffect(() => {
    setDistricts([]);
    setWards([]);
    setStreets([]);
    if (!value.region) return;
    setLoading((s) => ({ ...s, districts: true }));
    fetchChildren(value.region, "region", "district").then((d) => {
      setDistricts(d);
      setLoading((s) => ({ ...s, districts: false }));
    });
  }, [value.region, fetchChildren]);

  // ── When district changes, load its wards ──────────────────────────────
  useEffect(() => {
    setWards([]);
    setStreets([]);
    if (!value.district) return;
    setLoading((s) => ({ ...s, wards: true }));
    fetchChildren(value.district, "district", "ward").then((w) => {
      setWards(w);
      setLoading((s) => ({ ...s, wards: false }));
    });
  }, [value.district, fetchChildren]);

  // ── When ward changes, load its streets ────────────────────────────────
  useEffect(() => {
    setStreets([]);
    if (!value.ward) return;
    setLoading((s) => ({ ...s, streets: true }));
    fetchChildren(value.ward, "ward", "street").then((s) => {
      setStreets(s);
      setLoading((l) => ({ ...l, streets: false }));
    });
  }, [value.ward, fetchChildren]);

  // ── Change handlers (always reset descendants) ─────────────────────────
  function setRegion(v: string) {
    onChange({ region: v || null, district: null, ward: null, street: null });
  }
  function setDistrict(v: string) {
    onChange({ ...value, district: v || null, ward: null, street: null });
  }
  function setWard(v: string) {
    onChange({ ...value, ward: v || null, street: null });
  }
  function setStreet(v: string) {
    onChange({ ...value, street: v || null });
  }

  return (
    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
      {/* Region */}
      <Field label="Region" required>
        <select
          value={value.region ?? ""}
          onChange={(e) => setRegion(e.target.value)}
          disabled={loading.regions}
          className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm bg-white focus:border-brand-teal focus:outline-none disabled:bg-gray-50"
        >
          <option value="">Select region…</option>
          {regions.map((r) => (
            <option key={r.id} value={r.name}>{r.name}</option>
          ))}
        </select>
      </Field>

      {/* District */}
      <Field label="District" hint={allowAnyLevel ? "Leave empty = whole region" : undefined}>
        <select
          value={value.district ?? ""}
          onChange={(e) => setDistrict(e.target.value)}
          disabled={!value.region || loading.districts}
          className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm bg-white focus:border-brand-teal focus:outline-none disabled:bg-gray-50 disabled:text-gray-400"
        >
          <option value="">
            {!value.region ? "Pick a region first" : loading.districts ? "Loading…" : `All districts in ${value.region}`}
          </option>
          {districts.map((d) => (
            <option key={d.id} value={d.name}>{d.name}</option>
          ))}
        </select>
      </Field>

      {/* Ward */}
      <Field label="Ward" hint={allowAnyLevel ? "Leave empty = whole district" : undefined}>
        <select
          value={value.ward ?? ""}
          onChange={(e) => setWard(e.target.value)}
          disabled={!value.district || loading.wards}
          className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm bg-white focus:border-brand-teal focus:outline-none disabled:bg-gray-50 disabled:text-gray-400"
        >
          <option value="">
            {!value.district
              ? "Pick a district first"
              : loading.wards
              ? "Loading…"
              : wards.length === 0
              ? `No wards seeded for ${value.district} yet`
              : `All wards in ${value.district}`}
          </option>
          {wards.map((w) => (
            <option key={w.id} value={w.name}>{w.name}</option>
          ))}
        </select>
      </Field>

      {/* Street */}
      <Field label="Street" hint={allowAnyLevel ? "Leave empty = whole ward" : undefined}>
        <select
          value={value.street ?? ""}
          onChange={(e) => setStreet(e.target.value)}
          disabled={!value.ward || loading.streets}
          className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm bg-white focus:border-brand-teal focus:outline-none disabled:bg-gray-50 disabled:text-gray-400"
        >
          <option value="">
            {!value.ward
              ? "Pick a ward first"
              : loading.streets
              ? "Loading…"
              : streets.length === 0
              ? `No streets seeded for ${value.ward} yet`
              : `All streets in ${value.ward}`}
          </option>
          {streets.map((s) => (
            <option key={s.id} value={s.name}>{s.name}</option>
          ))}
        </select>
      </Field>

      {/* Scope summary */}
      <div className="md:col-span-2 text-xs text-gray-500 bg-gray-50 rounded-lg px-3 py-2">
        <span className="font-semibold">Offer scope:</span>{" "}
        {summarizeScope(value)}
      </div>
    </div>
  );
}

function summarizeScope(v: LocationSelection): string {
  const parts = [v.region, v.district, v.ward, v.street].filter(Boolean);
  if (parts.length === 0) return "No location selected.";
  const joined = parts.join(" → ");
  const depth = parts.length;
  const coverage =
    depth === 1 ? `every patient in ${v.region}` :
    depth === 2 ? `every patient in ${v.district} district` :
    depth === 3 ? `every patient in ${v.ward} ward` :
    `patients on ${v.street} street`;
  return `${joined} (${coverage}).`;
}

function Field({
  label,
  children,
  required,
  hint,
}: {
  label: string;
  children: React.ReactNode;
  required?: boolean;
  hint?: string;
}) {
  return (
    <div>
      <label className="block text-xs font-semibold text-gray-700 uppercase tracking-wider mb-1.5">
        {label} {required && <span className="text-red-500">*</span>}
        {hint && <span className="font-normal text-gray-400 normal-case ml-1">({hint})</span>}
      </label>
      {children}
    </div>
  );
}
