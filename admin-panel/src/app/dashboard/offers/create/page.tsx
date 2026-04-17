"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { createClient } from "@/lib/supabase/client";
import { SERVICE_TYPES, TIERS } from "@/lib/tzLocations";
import LocationPicker, { type LocationSelection } from "@/components/LocationPicker";

export default function CreateOfferPage() {
  const router = useRouter();
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [location, setLocation] = useState<LocationSelection>({
    region: null, district: null, ward: null, street: null,
  });
  const [serviceTypes, setServiceTypes] = useState<string[]>([]);
  const [tiers, setTiers] = useState<string[]>([]);
  const [discountType, setDiscountType] = useState<"free" | "percent" | "fixed">("free");
  const [discountValue, setDiscountValue] = useState<number>(0);
  const [limitRedemptions, setLimitRedemptions] = useState(false);
  const [maxRedemptions, setMaxRedemptions] = useState<number>(100);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function toggleIn(list: string[], value: string, setter: (v: string[]) => void) {
    setter(list.includes(value) ? list.filter((v) => v !== value) : [...list, value]);
  }

  async function submit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);

    if (!title.trim()) {
      setError("Title is required.");
      return;
    }
    if (!location.region) {
      setError("Pick a region (at minimum) for this offer.");
      return;
    }
    if (discountType === "percent" && (discountValue < 1 || discountValue > 100)) {
      setError("Percent discount must be between 1 and 100.");
      return;
    }
    if (discountType === "fixed" && discountValue <= 0) {
      setError("Fixed discount must be greater than 0.");
      return;
    }
    if (limitRedemptions && (!Number.isFinite(maxRedemptions) || maxRedemptions < 1)) {
      setError("Redemption limit must be a positive whole number.");
      return;
    }

    setSubmitting(true);
    const supabase = createClient();
    const { data: { user } } = await supabase.auth.getUser();
    const { error: err } = await supabase.from("location_offers").insert({
      title: title.trim(),
      description: description.trim() || null,
      region: location.region,
      district: location.district,
      ward: location.ward,
      street: location.street,
      service_types: serviceTypes,
      tiers,
      discount_type: discountType,
      discount_value: discountType === "free" ? 0 : discountValue,
      max_redemptions: limitRedemptions ? Math.round(maxRedemptions) : null,
      is_active: true,
      created_by: user?.id ?? null,
    });

    setSubmitting(false);
    if (err) {
      setError(err.message);
      return;
    }
    router.push("/dashboard/offers");
  }

  return (
    <div className="max-w-3xl">
      <div className="mb-6">
        <Link href="/dashboard/offers" className="text-sm text-brand-teal hover:underline">
          ← Back to offers
        </Link>
        <h1 className="text-2xl font-bold text-gray-900 mt-2">Create Location Offer</h1>
        <p className="text-sm text-gray-400 mt-0.5">
          Pick any level of location — region only, or drill all the way down to a street.
        </p>
      </div>

      <form onSubmit={submit} className="bg-white rounded-xl border border-gray-100 shadow-sm p-6 space-y-6">
        {/* Title */}
        <div>
          <label className="block text-xs font-semibold text-gray-700 uppercase tracking-wider mb-1.5">Title</label>
          <input
            type="text"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder="e.g. Free consultations for Ubungo residents"
            className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:border-brand-teal focus:outline-none"
            required
          />
        </div>

        {/* Description */}
        <div>
          <label className="block text-xs font-semibold text-gray-700 uppercase tracking-wider mb-1.5">Description (optional)</label>
          <textarea
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            rows={2}
            className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:border-brand-teal focus:outline-none"
          />
        </div>

        {/* Location — cascading picker */}
        <div>
          <label className="block text-xs font-semibold text-gray-700 uppercase tracking-wider mb-3">
            Location Scope
          </label>
          <LocationPicker value={location} onChange={setLocation} />
        </div>

        {/* Service types */}
        <div>
          <label className="block text-xs font-semibold text-gray-700 uppercase tracking-wider mb-1.5">
            Service Types <span className="font-normal text-gray-400 normal-case">(none selected = all services)</span>
          </label>
          <div className="flex flex-wrap gap-2">
            {SERVICE_TYPES.map((s) => (
              <button
                key={s.value}
                type="button"
                onClick={() => toggleIn(serviceTypes, s.value, setServiceTypes)}
                className={`px-3 py-1.5 rounded-lg text-xs font-medium border transition-colors ${
                  serviceTypes.includes(s.value)
                    ? "bg-brand-teal text-white border-brand-teal"
                    : "bg-white text-gray-700 border-gray-200 hover:border-brand-teal"
                }`}
              >
                {s.label}
              </button>
            ))}
          </div>
        </div>

        {/* Tiers */}
        <div>
          <label className="block text-xs font-semibold text-gray-700 uppercase tracking-wider mb-1.5">
            Tiers <span className="font-normal text-gray-400 normal-case">(none selected = all tiers)</span>
          </label>
          <div className="flex gap-2">
            {TIERS.map((t) => (
              <button
                key={t.value}
                type="button"
                onClick={() => toggleIn(tiers, t.value, setTiers)}
                className={`px-4 py-1.5 rounded-lg text-xs font-medium border transition-colors ${
                  tiers.includes(t.value)
                    ? "bg-brand-teal text-white border-brand-teal"
                    : "bg-white text-gray-700 border-gray-200 hover:border-brand-teal"
                }`}
              >
                {t.label}
              </button>
            ))}
          </div>
        </div>

        {/* Discount */}
        <div>
          <label className="block text-xs font-semibold text-gray-700 uppercase tracking-wider mb-1.5">Discount</label>
          <div className="grid grid-cols-3 gap-2">
            {(["free", "percent", "fixed"] as const).map((t) => (
              <button
                key={t}
                type="button"
                onClick={() => setDiscountType(t)}
                className={`px-3 py-2 rounded-lg text-sm font-medium border transition-colors ${
                  discountType === t
                    ? "bg-brand-teal text-white border-brand-teal"
                    : "bg-white text-gray-700 border-gray-200 hover:border-brand-teal"
                }`}
              >
                {t === "free" ? "Free" : t === "percent" ? "% Off" : "Fixed TZS off"}
              </button>
            ))}
          </div>
          {discountType !== "free" && (
            <div className="mt-3">
              <input
                type="number"
                value={discountValue}
                onChange={(e) => setDiscountValue(Number(e.target.value))}
                min={1}
                max={discountType === "percent" ? 100 : undefined}
                placeholder={discountType === "percent" ? "e.g. 50 for 50% off" : "e.g. 2000 for TZS 2,000 off"}
                className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:border-brand-teal focus:outline-none"
              />
            </div>
          )}
        </div>

        {/* Redemption cap */}
        <div>
          <label className="flex items-center gap-2 cursor-pointer">
            <input
              type="checkbox"
              checked={limitRedemptions}
              onChange={(e) => setLimitRedemptions(e.target.checked)}
              className="h-4 w-4 rounded border-gray-300 text-brand-teal focus:ring-brand-teal"
            />
            <span className="text-xs font-semibold text-gray-700 uppercase tracking-wider">
              Limit number of patients who can redeem
            </span>
          </label>
          {limitRedemptions ? (
            <div className="mt-3 space-y-1">
              <input
                type="number"
                value={maxRedemptions}
                onChange={(e) => setMaxRedemptions(Number(e.target.value))}
                min={1}
                placeholder="e.g. 100"
                className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:border-brand-teal focus:outline-none"
              />
              <p className="text-xs text-gray-500">
                The offer will self-terminate once <b>{maxRedemptions || 0}</b> patient
                {maxRedemptions === 1 ? "" : "s"} from this location have redeemed it.
              </p>
            </div>
          ) : (
            <p className="mt-2 text-xs text-gray-400">Unlimited — every qualifying patient gets the discount.</p>
          )}
        </div>

        {error && (
          <div className="text-sm text-red-600 bg-red-50 border border-red-200 rounded-lg px-3 py-2">
            {error}
          </div>
        )}

        <div className="flex justify-end gap-3 pt-2">
          <Link
            href="/dashboard/offers"
            className="px-4 py-2 rounded-xl border border-gray-200 text-sm font-medium text-gray-700 hover:bg-gray-50"
          >
            Cancel
          </Link>
          <button
            type="submit"
            disabled={submitting}
            className="px-5 py-2 rounded-xl bg-brand-teal text-white text-sm font-medium hover:bg-brand-teal-dark disabled:opacity-50"
          >
            {submitting ? "Creating..." : "Create Offer"}
          </button>
        </div>
      </form>
    </div>
  );
}
