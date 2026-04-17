"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { createClient } from "@/lib/supabase/client";
import { formatDate } from "@/lib/utils";
import Modal from "@/components/ui/Modal";

interface LocationOffer {
  offer_id: string;
  title: string;
  description: string | null;
  region: string;
  district: string | null;
  ward: string | null;
  street: string | null;
  service_types: string[];
  tiers: string[];
  discount_type: "free" | "percent" | "fixed";
  discount_value: number;
  is_active: boolean;
  created_at: string;
  terminated_at: string | null;
}

function formatLocationPath(o: LocationOffer): string {
  return [o.region, o.district, o.ward, o.street].filter(Boolean).join(" → ");
}

interface RedemptionCount {
  offer_id: string;
  count: number;
}

export default function OffersPage() {
  const [offers, setOffers] = useState<LocationOffer[]>([]);
  const [redemptions, setRedemptions] = useState<Record<string, number>>({});
  const [loading, setLoading] = useState(true);
  const [terminateOffer, setTerminateOffer] = useState<LocationOffer | null>(null);
  const [terminating, setTerminating] = useState(false);

  const load = useCallback(async () => {
    const supabase = createClient();
    const [{ data: offersData }, { data: redemptionsData }] = await Promise.all([
      supabase
        .from("location_offers")
        .select("*")
        .order("created_at", { ascending: false }),
      supabase
        .from("location_offer_redemptions")
        .select("offer_id"),
    ]);
    setOffers((offersData as LocationOffer[]) ?? []);
    const counts: Record<string, number> = {};
    for (const r of (redemptionsData as { offer_id: string }[]) ?? []) {
      counts[r.offer_id] = (counts[r.offer_id] ?? 0) + 1;
    }
    setRedemptions(counts);
    setLoading(false);
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  async function handleTerminate() {
    if (!terminateOffer) return;
    setTerminating(true);
    const supabase = createClient();
    await supabase
      .from("location_offers")
      .update({ is_active: false })
      .eq("offer_id", terminateOffer.offer_id);
    setTerminating(false);
    setTerminateOffer(null);
    await load();
  }

  async function toggleActive(offer: LocationOffer) {
    const supabase = createClient();
    await supabase
      .from("location_offers")
      .update({ is_active: !offer.is_active })
      .eq("offer_id", offer.offer_id);
    await load();
  }

  function describeDiscount(o: LocationOffer): string {
    if (o.discount_type === "free")    return "Free";
    if (o.discount_type === "percent") return `${o.discount_value}% off`;
    return `TZS ${o.discount_value.toLocaleString()} off`;
  }

  if (loading) {
    return (
      <div className="flex items-center justify-center py-20">
        <div className="h-8 w-8 animate-spin rounded-full border-4 border-brand-teal border-t-transparent" />
      </div>
    );
  }

  return (
    <div>
      {/* Header */}
      <div className="flex items-start justify-between mb-6">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-brand-teal/10 flex items-center justify-center flex-shrink-0">
            <svg className="h-5 w-5 text-brand-teal" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M15 10.5a3 3 0 11-6 0 3 3 0 016 0z" />
              <path strokeLinecap="round" strokeLinejoin="round" d="M19.5 10.5c0 7.142-7.5 11.25-7.5 11.25S4.5 17.642 4.5 10.5a7.5 7.5 0 1115 0z" />
            </svg>
          </div>
          <div>
            <h1 className="text-2xl font-bold text-gray-900">Location Offers</h1>
            <p className="text-sm text-gray-400 mt-0.5">Grant discounts to patients in specific districts or wards</p>
          </div>
        </div>

        <Link
          href="/dashboard/offers/create"
          className="inline-flex items-center gap-2 px-5 py-2.5 bg-brand-teal text-white rounded-xl text-sm font-medium hover:bg-brand-teal-dark transition-colors"
        >
          <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M12 4.5v15m7.5-7.5h-15" />
          </svg>
          New Offer
        </Link>
      </div>

      {/* Offers table */}
      <div className="bg-white rounded-xl border border-gray-100 shadow-sm overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-gray-100">
                <th className="text-left px-5 py-3.5 text-xs font-semibold text-brand-teal uppercase tracking-wider">Title</th>
                <th className="text-left px-5 py-3.5 text-xs font-semibold text-brand-teal uppercase tracking-wider">Location</th>
                <th className="text-left px-5 py-3.5 text-xs font-semibold text-brand-teal uppercase tracking-wider">Scope</th>
                <th className="text-left px-5 py-3.5 text-xs font-semibold text-brand-teal uppercase tracking-wider">Discount</th>
                <th className="text-left px-5 py-3.5 text-xs font-semibold text-brand-teal uppercase tracking-wider">Redeemed</th>
                <th className="text-left px-5 py-3.5 text-xs font-semibold text-brand-teal uppercase tracking-wider">Status</th>
                <th className="text-left px-5 py-3.5 text-xs font-semibold text-brand-teal uppercase tracking-wider">Created</th>
                <th className="text-left px-5 py-3.5 text-xs font-semibold text-brand-teal uppercase tracking-wider">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-50">
              {offers.map((o) => {
                const scope = [
                  o.service_types.length === 0 ? "All services" : `${o.service_types.length} service${o.service_types.length === 1 ? "" : "s"}`,
                  o.tiers.length === 0 ? "All tiers" : o.tiers.join(", "),
                ].join(" · ");
                return (
                  <tr key={o.offer_id} className="hover:bg-gray-50/50">
                    <td className="px-5 py-4">
                      <p className="text-gray-900 font-medium">{o.title}</p>
                      {o.description && <p className="text-xs text-gray-500 mt-0.5 max-w-xs truncate">{o.description}</p>}
                    </td>
                    <td className="px-5 py-4 text-gray-700 text-xs">
                      {formatLocationPath(o)}
                    </td>
                    <td className="px-5 py-4 text-gray-500 text-xs">{scope}</td>
                    <td className="px-5 py-4">
                      <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-emerald-50 text-emerald-700">
                        {describeDiscount(o)}
                      </span>
                    </td>
                    <td className="px-5 py-4 text-gray-700">{redemptions[o.offer_id] ?? 0}</td>
                    <td className="px-5 py-4">
                      {o.is_active ? (
                        <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-green-50 text-green-700">Active</span>
                      ) : (
                        <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-gray-100 text-gray-500">Terminated</span>
                      )}
                    </td>
                    <td className="px-5 py-4 text-gray-500 text-xs">{formatDate(o.created_at)}</td>
                    <td className="px-5 py-4">
                      <div className="flex gap-2">
                        {o.is_active ? (
                          <button
                            onClick={() => setTerminateOffer(o)}
                            className="text-xs px-3 py-1.5 rounded-lg bg-red-50 text-red-600 hover:bg-red-100 font-medium"
                          >
                            Terminate
                          </button>
                        ) : (
                          <button
                            onClick={() => toggleActive(o)}
                            className="text-xs px-3 py-1.5 rounded-lg bg-brand-teal/10 text-brand-teal hover:bg-brand-teal/20 font-medium"
                          >
                            Reactivate
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                );
              })}
              {offers.length === 0 && (
                <tr>
                  <td colSpan={8} className="px-5 py-12 text-center text-gray-400">
                    No offers yet. Create one to grant free or discounted consultations to a specific district.
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>

      <Modal open={!!terminateOffer} onClose={() => setTerminateOffer(null)} title="Terminate Offer">
        <p className="text-sm text-gray-600">
          Terminate &ldquo;{terminateOffer?.title}&rdquo;? Patients in
          {" "}{terminateOffer ? formatLocationPath(terminateOffer) : ""}{" "}
          will pay full price from their next booking onward. Existing redemptions are preserved.
        </p>
        <div className="flex justify-end gap-3 mt-5">
          <button
            onClick={() => setTerminateOffer(null)}
            className="px-4 py-2 rounded-xl border border-gray-200 text-sm font-medium text-gray-700 hover:bg-gray-50"
          >
            Cancel
          </button>
          <button
            onClick={handleTerminate}
            disabled={terminating}
            className="px-4 py-2 rounded-xl bg-red-600 text-white text-sm font-medium hover:bg-red-700 disabled:opacity-50"
          >
            {terminating ? "Terminating..." : "Terminate"}
          </button>
        </div>
      </Modal>
    </div>
  );
}
