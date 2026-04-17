"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
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
  max_redemptions: number | null;
  redemption_count: number;
  expires_at: string | null;
  is_active: boolean;
  created_at: string;
  terminated_at: string | null;
  deleted_at: string | null;
}

function formatLocationPath(o: LocationOffer): string {
  return [o.region, o.district, o.ward, o.street].filter(Boolean).join(" → ");
}

type OfferStatus = "active" | "expired" | "terminated";
function offerStatus(o: LocationOffer): OfferStatus {
  if (!o.is_active) return "terminated";
  if (o.expires_at && new Date(o.expires_at).getTime() <= Date.now()) return "expired";
  return "active";
}

function formatTimeRemaining(expiresAt: string): string {
  const diffMs = new Date(expiresAt).getTime() - Date.now();
  if (diffMs <= 0) {
    const pastMs = Math.abs(diffMs);
    const hours = Math.floor(pastMs / 3_600_000);
    return hours < 24 ? `expired ${hours}h ago` : `expired ${Math.floor(hours / 24)}d ago`;
  }
  const hours = Math.floor(diffMs / 3_600_000);
  if (hours < 1) return `< 1h left`;
  if (hours < 24) return `${hours}h left`;
  const days = Math.floor(hours / 24);
  const remHours = hours % 24;
  return remHours === 0 ? `${days}d left` : `${days}d ${remHours}h left`;
}

export default function OffersPage() {
  const [allOffers, setAllOffers] = useState<LocationOffer[]>([]);
  const [loading, setLoading] = useState(true);
  const [terminateOffer, setTerminateOffer] = useState<LocationOffer | null>(null);
  const [terminating, setTerminating] = useState(false);
  const [deleteOffer, setDeleteOffer] = useState<LocationOffer | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [binOpen, setBinOpen] = useState(false);
  const [hardDeleteOffer, setHardDeleteOffer] = useState<LocationOffer | null>(null);
  const [hardDeleting, setHardDeleting] = useState(false);

  const load = useCallback(async () => {
    const supabase = createClient();
    const { data } = await supabase
      .from("location_offers")
      .select("*")
      .order("created_at", { ascending: false });
    setAllOffers((data as LocationOffer[]) ?? []);
    setLoading(false);
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  const offers = useMemo(() => allOffers.filter((o) => !o.deleted_at), [allOffers]);
  const deletedOffers = useMemo(() => allOffers.filter((o) => o.deleted_at), [allOffers]);

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

  // Soft-delete: move to recycle bin
  async function handleSoftDelete() {
    if (!deleteOffer) return;
    setDeleting(true);
    const supabase = createClient();
    await supabase
      .from("location_offers")
      .update({ deleted_at: new Date().toISOString(), is_active: false })
      .eq("offer_id", deleteOffer.offer_id);
    setDeleting(false);
    setDeleteOffer(null);
    await load();
  }

  // Restore: clear deleted_at (keeps is_active=false — admin must reactivate explicitly)
  async function handleRestore(offer: LocationOffer) {
    const supabase = createClient();
    await supabase
      .from("location_offers")
      .update({ deleted_at: null })
      .eq("offer_id", offer.offer_id);
    await load();
  }

  // Permanent delete: cascades redemption rows. Only from bin, with confirmation.
  async function handleHardDelete() {
    if (!hardDeleteOffer) return;
    setHardDeleting(true);
    const supabase = createClient();
    await supabase
      .from("location_offers")
      .delete()
      .eq("offer_id", hardDeleteOffer.offer_id);
    setHardDeleting(false);
    setHardDeleteOffer(null);
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
    <div className="relative min-h-[calc(100vh-100px)]">
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
                    <td className="px-5 py-4 text-gray-700 text-xs">
                      {o.max_redemptions != null ? (
                        <div className="min-w-[80px]">
                          <div className="flex justify-between mb-0.5">
                            <span className="font-semibold">{o.redemption_count}</span>
                            <span className="text-gray-400">/ {o.max_redemptions}</span>
                          </div>
                          <div className="h-1 bg-gray-100 rounded overflow-hidden">
                            <div
                              className="h-full bg-brand-teal"
                              style={{ width: `${Math.min(100, (o.redemption_count / o.max_redemptions) * 100)}%` }}
                            />
                          </div>
                        </div>
                      ) : (
                        <span>{o.redemption_count} <span className="text-gray-400">/ ∞</span></span>
                      )}
                    </td>
                    <td className="px-5 py-4">
                      {(() => {
                        const st = offerStatus(o);
                        if (st === "active") {
                          return (
                            <div>
                              <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-green-50 text-green-700">Active</span>
                              {o.expires_at && (
                                <div className="text-[10px] text-gray-500 mt-0.5">{formatTimeRemaining(o.expires_at)}</div>
                              )}
                            </div>
                          );
                        }
                        if (st === "expired") {
                          return (
                            <div>
                              <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-amber-50 text-amber-700">Expired</span>
                              {o.expires_at && (
                                <div className="text-[10px] text-gray-500 mt-0.5">{formatTimeRemaining(o.expires_at)}</div>
                              )}
                            </div>
                          );
                        }
                        return (
                          <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold bg-gray-100 text-gray-500">Terminated</span>
                        );
                      })()}
                    </td>
                    <td className="px-5 py-4 text-gray-500 text-xs">{formatDate(o.created_at)}</td>
                    <td className="px-5 py-4">
                      <div className="flex gap-2 items-center">
                        {(() => {
                          const st = offerStatus(o);
                          if (st === "active") {
                            return (
                              <button
                                onClick={() => setTerminateOffer(o)}
                                className="text-xs px-3 py-1.5 rounded-lg bg-amber-50 text-amber-700 hover:bg-amber-100 font-medium"
                              >
                                Terminate
                              </button>
                            );
                          }
                          if (st === "terminated") {
                            return (
                              <button
                                onClick={() => toggleActive(o)}
                                className="text-xs px-3 py-1.5 rounded-lg bg-brand-teal/10 text-brand-teal hover:bg-brand-teal/20 font-medium"
                              >
                                Reactivate
                              </button>
                            );
                          }
                          return null;
                        })()}
                        <button
                          onClick={() => setDeleteOffer(o)}
                          className="text-xs px-2 py-1.5 rounded-lg text-red-600 hover:bg-red-50 font-medium inline-flex items-center gap-1"
                          title="Move to recycle bin"
                        >
                          <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.8}>
                            <path strokeLinecap="round" strokeLinejoin="round" d="M14.74 9l-.346 9m-4.788 0L9.26 9m9.968-3.21c.342.052.682.107 1.022.166m-1.022-.165L18.16 19.673a2.25 2.25 0 01-2.244 2.077H8.084a2.25 2.25 0 01-2.244-2.077L4.772 5.79m14.456 0a48.108 48.108 0 00-3.478-.397m-12 .562c.34-.059.68-.114 1.022-.165m0 0a48.11 48.11 0 013.478-.397m7.5 0v-.916c0-1.18-.91-2.164-2.09-2.201a51.964 51.964 0 00-3.32 0c-1.18.037-2.09 1.022-2.09 2.201v.916m7.5 0a48.667 48.667 0 00-7.5 0" />
                          </svg>
                          Delete
                        </button>
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

      {/* Floating recycle bin button */}
      <button
        onClick={() => setBinOpen(true)}
        className="fixed bottom-6 right-6 z-40 flex items-center gap-2 bg-white border border-gray-200 rounded-full shadow-lg px-4 py-3 text-sm font-medium text-gray-700 hover:bg-gray-50 hover:border-brand-teal transition-colors"
        title="View deleted offers"
      >
        <svg className="h-5 w-5 text-gray-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.8}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M14.74 9l-.346 9m-4.788 0L9.26 9m9.968-3.21c.342.052.682.107 1.022.166m-1.022-.165L18.16 19.673a2.25 2.25 0 01-2.244 2.077H8.084a2.25 2.25 0 01-2.244-2.077L4.772 5.79m14.456 0a48.108 48.108 0 00-3.478-.397m-12 .562c.34-.059.68-.114 1.022-.165m0 0a48.11 48.11 0 013.478-.397m7.5 0v-.916c0-1.18-.91-2.164-2.09-2.201a51.964 51.964 0 00-3.32 0c-1.18.037-2.09 1.022-2.09 2.201v.916m7.5 0a48.667 48.667 0 00-7.5 0" />
        </svg>
        Recycle Bin
        {deletedOffers.length > 0 && (
          <span className="inline-flex items-center justify-center h-5 min-w-[20px] rounded-full bg-red-500 text-white text-[10px] font-bold px-1">
            {deletedOffers.length}
          </span>
        )}
      </button>

      {/* Terminate modal */}
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

      {/* Soft-delete (move to bin) modal */}
      <Modal open={!!deleteOffer} onClose={() => setDeleteOffer(null)} title="Delete Offer">
        <p className="text-sm text-gray-600">
          Move &ldquo;{deleteOffer?.title}&rdquo; to the recycle bin? It will be hidden from this list
          and stop matching new bookings. You can restore it anytime from the recycle bin.
        </p>
        <div className="flex justify-end gap-3 mt-5">
          <button
            onClick={() => setDeleteOffer(null)}
            className="px-4 py-2 rounded-xl border border-gray-200 text-sm font-medium text-gray-700 hover:bg-gray-50"
          >
            Cancel
          </button>
          <button
            onClick={handleSoftDelete}
            disabled={deleting}
            className="px-4 py-2 rounded-xl bg-red-600 text-white text-sm font-medium hover:bg-red-700 disabled:opacity-50"
          >
            {deleting ? "Deleting..." : "Move to Bin"}
          </button>
        </div>
      </Modal>

      {/* Recycle bin modal */}
      <RecycleBinModal
        open={binOpen}
        onClose={() => setBinOpen(false)}
        offers={deletedOffers}
        onRestore={handleRestore}
        onHardDeleteRequest={(o) => setHardDeleteOffer(o)}
      />

      {/* Hard-delete confirmation */}
      <Modal open={!!hardDeleteOffer} onClose={() => setHardDeleteOffer(null)} title="Delete Permanently">
        <p className="text-sm text-gray-600">
          Permanently delete &ldquo;{hardDeleteOffer?.title}&rdquo;? This cannot be undone. Associated
          redemption history for this offer will also be removed.
        </p>
        <div className="flex justify-end gap-3 mt-5">
          <button
            onClick={() => setHardDeleteOffer(null)}
            className="px-4 py-2 rounded-xl border border-gray-200 text-sm font-medium text-gray-700 hover:bg-gray-50"
          >
            Cancel
          </button>
          <button
            onClick={handleHardDelete}
            disabled={hardDeleting}
            className="px-4 py-2 rounded-xl bg-red-600 text-white text-sm font-medium hover:bg-red-700 disabled:opacity-50"
          >
            {hardDeleting ? "Deleting..." : "Delete Forever"}
          </button>
        </div>
      </Modal>
    </div>
  );
}

// ── Recycle Bin modal ───────────────────────────────────────────────────────

function RecycleBinModal({
  open,
  onClose,
  offers,
  onRestore,
  onHardDeleteRequest,
}: {
  open: boolean;
  onClose: () => void;
  offers: LocationOffer[];
  onRestore: (o: LocationOffer) => void;
  onHardDeleteRequest: (o: LocationOffer) => void;
}) {
  if (!open) return null;
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4" onClick={onClose}>
      <div
        className="bg-white rounded-2xl shadow-2xl w-full max-w-3xl max-h-[85vh] flex flex-col"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between px-6 py-4 border-b border-gray-100">
          <div className="flex items-center gap-3">
            <svg className="h-5 w-5 text-gray-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.8}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M14.74 9l-.346 9m-4.788 0L9.26 9m9.968-3.21c.342.052.682.107 1.022.166m-1.022-.165L18.16 19.673a2.25 2.25 0 01-2.244 2.077H8.084a2.25 2.25 0 01-2.244-2.077L4.772 5.79m14.456 0a48.108 48.108 0 00-3.478-.397m-12 .562c.34-.059.68-.114 1.022-.165m0 0a48.11 48.11 0 013.478-.397m7.5 0v-.916c0-1.18-.91-2.164-2.09-2.201a51.964 51.964 0 00-3.32 0c-1.18.037-2.09 1.022-2.09 2.201v.916m7.5 0a48.667 48.667 0 00-7.5 0" />
            </svg>
            <div>
              <h2 className="text-lg font-semibold text-gray-900">Recycle Bin</h2>
              <p className="text-xs text-gray-400">
                {offers.length === 0 ? "Empty" : `${offers.length} deleted offer${offers.length === 1 ? "" : "s"}`}
              </p>
            </div>
          </div>
          <button onClick={onClose} className="p-1 hover:bg-gray-100 rounded-lg">
            <svg className="h-5 w-5 text-gray-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        <div className="flex-1 overflow-y-auto p-6">
          {offers.length === 0 ? (
            <div className="text-center py-12 text-gray-400 text-sm">
              The recycle bin is empty. Deleted offers appear here and can be restored anytime.
            </div>
          ) : (
            <div className="space-y-3">
              {offers.map((o) => (
                <div key={o.offer_id} className="border border-gray-100 rounded-xl p-4 flex items-start justify-between gap-4">
                  <div className="min-w-0 flex-1">
                    <p className="font-medium text-gray-900 truncate">{o.title}</p>
                    <p className="text-xs text-gray-500 mt-0.5">{formatLocationPath(o)}</p>
                    <p className="text-[11px] text-gray-400 mt-1">
                      Deleted {o.deleted_at ? formatDate(o.deleted_at) : ""}
                      {" · "}
                      {o.redemption_count} redemption{o.redemption_count === 1 ? "" : "s"}
                    </p>
                  </div>
                  <div className="flex gap-2 flex-shrink-0">
                    <button
                      onClick={() => onRestore(o)}
                      className="text-xs px-3 py-1.5 rounded-lg bg-brand-teal/10 text-brand-teal hover:bg-brand-teal/20 font-medium"
                    >
                      Restore
                    </button>
                    <button
                      onClick={() => onHardDeleteRequest(o)}
                      className="text-xs px-3 py-1.5 rounded-lg bg-red-50 text-red-600 hover:bg-red-100 font-medium"
                    >
                      Delete Forever
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
