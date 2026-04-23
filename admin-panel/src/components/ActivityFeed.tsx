"use client";

import { useEffect, useMemo, useState } from "react";

export interface ActivityItem {
  id: string;
  type: "payment" | "verification" | "registration" | "consultation" | "admin";
  title: string;
  subtitle?: string;
  timestamp: string;
}

const typeConfig: Record<ActivityItem["type"], { color: string; bgColor: string; icon: string }> = {
  payment: { color: "text-emerald-500", bgColor: "bg-emerald-50", icon: "dollar" },
  verification: { color: "text-green-500", bgColor: "bg-green-50", icon: "check" },
  registration: { color: "text-blue-500", bgColor: "bg-blue-50", icon: "user" },
  consultation: { color: "text-orange-500", bgColor: "bg-orange-50", icon: "chat" },
  admin: { color: "text-purple-500", bgColor: "bg-purple-50", icon: "shield" },
};

// LocalStorage keys — per-browser dismissal state. We store the IDs of
// dismissed activities (hidden from the feed) and a snapshot of those
// items so the recycle bin can show them even when the server no longer
// returns the underlying row (e.g. a payment record purged by cron).
const LS_DISMISSED = "esiri-admin-dismissed-activities-v1";
const LS_BIN = "esiri-admin-bin-activities-v1";

interface BinEntry extends ActivityItem {
  dismissedAt: string;
}

function relativeTime(iso: string): string {
  const diff = Date.now() - new Date(iso).getTime();
  const minutes = Math.floor(diff / 60000);
  if (minutes < 1) return "just now";
  if (minutes < 60) return `about ${minutes} minutes ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `about ${hours} hours ago`;
  const days = Math.floor(hours / 24);
  return `${days} day${days > 1 ? "s" : ""} ago`;
}

function ActivityIcon({ type }: { type: ActivityItem["type"] }) {
  const config = typeConfig[type];

  if (config.icon === "dollar") {
    return (
      <svg className={`h-4 w-4 ${config.color}`} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
        <path strokeLinecap="round" strokeLinejoin="round" d="M12 6v12m-3-2.818l.879.659c1.171.879 3.07.879 4.242 0 1.172-.879 1.172-2.303 0-3.182C13.536 12.219 12.768 12 12 12c-.725 0-1.45-.22-2.003-.659-1.106-.879-1.106-2.303 0-3.182s2.9-.879 4.006 0l.415.33M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
      </svg>
    );
  }
  if (config.icon === "check") {
    return (
      <svg className={`h-4 w-4 ${config.color}`} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
        <path strokeLinecap="round" strokeLinejoin="round" d="M9 12.75L11.25 15 15 9.75M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
      </svg>
    );
  }
  if (config.icon === "user") {
    return (
      <svg className={`h-4 w-4 ${config.color}`} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
        <path strokeLinecap="round" strokeLinejoin="round" d="M19 7.5v3m0 0v3m0-3h3m-3 0h-3m-2.25-4.125a3.375 3.375 0 11-6.75 0 3.375 3.375 0 016.75 0zM4 19.235v-.11a6.375 6.375 0 0112.75 0v.109A12.318 12.318 0 0110.374 21c-2.331 0-4.512-.645-6.374-1.766z" />
      </svg>
    );
  }
  if (config.icon === "chat") {
    return (
      <svg className={`h-4 w-4 ${config.color}`} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
        <path strokeLinecap="round" strokeLinejoin="round" d="M20.25 8.511c.884.284 1.5 1.128 1.5 2.097v4.286c0 1.136-.847 2.1-1.98 2.193-.34.027-.68.052-1.02.072v3.091l-3-3c-1.354 0-2.694-.055-4.02-.163a2.115 2.115 0 01-.825-.242m9.345-8.334a2.126 2.126 0 00-.476-.095 48.64 48.64 0 00-8.048 0c-1.131.094-1.976 1.057-1.976 2.192v4.286c0 .837.46 1.58 1.155 1.951m9.345-8.334V6.637c0-1.621-1.152-3.026-2.76-3.235A48.455 48.455 0 0011.25 3c-2.115 0-4.198.137-6.24.402-1.608.209-2.76 1.614-2.76 3.235v6.226c0 1.621 1.152 3.026 2.76 3.235.577.075 1.157.14 1.74.194V21l4.155-4.155" />
      </svg>
    );
  }
  // shield (admin)
  return (
    <svg className={`h-4 w-4 ${config.color}`} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M9 12.75L11.25 15 15 9.75m-3-7.036A11.959 11.959 0 013.598 6 11.99 11.99 0 003 9.749c0 5.592 3.824 10.29 9 11.623 5.176-1.332 9-6.03 9-11.622 0-1.31-.21-2.571-.598-3.751h-.152c-3.196 0-6.1-1.248-8.25-3.285z" />
    </svg>
  );
}

interface ActivityFeedProps {
  items: ActivityItem[];
  /** Items per page. Defaults to 10 — tall enough to feel like a feed,
   *  short enough that a page fits without scrolling on most screens. */
  pageSize?: number;
}

export default function ActivityFeed({ items, pageSize = 10 }: ActivityFeedProps) {
  const [page, setPage] = useState(1);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [dismissed, setDismissed] = useState<Set<string>>(new Set());
  const [bin, setBin] = useState<BinEntry[]>([]);
  const [binOpen, setBinOpen] = useState(false);
  const [hydrated, setHydrated] = useState(false);

  // Load dismissal state from localStorage once on mount. We wait for
  // hydration before rendering anything that depends on the persisted
  // state — otherwise SSR/CSR mismatches cause React to bail.
  useEffect(() => {
    try {
      const dismissedRaw = localStorage.getItem(LS_DISMISSED);
      const binRaw = localStorage.getItem(LS_BIN);
      if (dismissedRaw) setDismissed(new Set(JSON.parse(dismissedRaw) as string[]));
      if (binRaw) setBin(JSON.parse(binRaw) as BinEntry[]);
    } catch {
      /* corrupt JSON — ignore, start clean */
    }
    setHydrated(true);
  }, []);

  // Persist on change
  useEffect(() => {
    if (!hydrated) return;
    try {
      localStorage.setItem(LS_DISMISSED, JSON.stringify(Array.from(dismissed)));
    } catch { /* quota / private mode */ }
  }, [dismissed, hydrated]);

  useEffect(() => {
    if (!hydrated) return;
    try {
      localStorage.setItem(LS_BIN, JSON.stringify(bin));
    } catch { /* quota / private mode */ }
  }, [bin, hydrated]);

  const visibleItems = useMemo(
    () => items.filter((item) => !dismissed.has(item.id)),
    [items, dismissed],
  );

  const totalPages = Math.max(1, Math.ceil(visibleItems.length / pageSize));
  const clampedPage = Math.min(page, totalPages);
  const pageItems = visibleItems.slice((clampedPage - 1) * pageSize, clampedPage * pageSize);

  function toggleSelect(id: string) {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  function selectAllOnPage() {
    setSelected((prev) => {
      const next = new Set(prev);
      for (const item of pageItems) next.add(item.id);
      return next;
    });
  }

  function clearSelection() {
    setSelected(new Set());
  }

  function deleteSelected() {
    if (selected.size === 0) return;
    const now = new Date().toISOString();
    const toBin = items.filter((i) => selected.has(i.id)).map<BinEntry>((i) => ({ ...i, dismissedAt: now }));
    setDismissed((prev) => new Set([...prev, ...selected]));
    setBin((prev) => {
      const byId = new Map(prev.map((e) => [e.id, e]));
      for (const e of toBin) byId.set(e.id, e);
      return Array.from(byId.values());
    });
    setSelected(new Set());
  }

  function restoreFromBin(ids: string[]) {
    setDismissed((prev) => {
      const next = new Set(prev);
      for (const id of ids) next.delete(id);
      return next;
    });
    setBin((prev) => prev.filter((e) => !ids.includes(e.id)));
  }

  function deletePermanentlyFromBin(ids: string[]) {
    setBin((prev) => prev.filter((e) => !ids.includes(e.id)));
    // Keep them in `dismissed` so the feed never surfaces them again.
  }

  const allOnPageSelected = pageItems.length > 0 && pageItems.every((i) => selected.has(i.id));

  return (
    <>
      <div className="bg-white rounded-xl border border-gray-100 shadow-sm p-6 relative">
        <div className="flex items-center gap-2 mb-5">
          <svg className="h-5 w-5 text-brand-teal" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M3.75 13.5l10.5-11.25L12 10.5h8.25L9.75 21.75 12 13.5H3.75z" />
          </svg>
          <h2 className="text-base font-semibold text-gray-900">Recent Activity</h2>
          {hydrated && bin.length > 0 && (
            <button
              type="button"
              onClick={() => setBinOpen(true)}
              className="ml-auto inline-flex items-center gap-1 text-xs text-gray-500 hover:text-brand-teal"
              title="Recycle bin"
            >
              <TrashIcon className="h-4 w-4" />
              Bin ({bin.length})
            </button>
          )}
        </div>

        {/* Selection action bar — sticky above the list when something's selected */}
        {selected.size > 0 && (
          <div className="flex items-center justify-between gap-3 mb-3 px-3 py-2 rounded-lg bg-brand-teal/5 border border-brand-teal/20">
            <p className="text-sm text-brand-teal font-medium">
              {selected.size} selected
            </p>
            <div className="flex gap-2">
              <button
                type="button"
                onClick={clearSelection}
                className="px-3 py-1.5 text-xs font-medium text-gray-700 bg-white border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors"
              >
                Clear
              </button>
              <button
                type="button"
                onClick={deleteSelected}
                className="inline-flex items-center gap-1 px-3 py-1.5 text-xs font-semibold text-white bg-red-500 hover:bg-red-600 rounded-lg transition-colors"
              >
                <TrashIcon className="h-3.5 w-3.5" />
                Delete
              </button>
            </div>
          </div>
        )}

        {visibleItems.length === 0 ? (
          <p className="text-sm text-gray-400 py-4">No recent activity.</p>
        ) : (
          <>
            {/* Select-all toggle (page-scoped) */}
            {pageItems.length > 1 && (
              <div className="flex items-center gap-2 mb-2 px-3 text-xs">
                <input
                  type="checkbox"
                  checked={allOnPageSelected}
                  onChange={() => allOnPageSelected
                    ? setSelected((prev) => {
                        const next = new Set(prev);
                        for (const i of pageItems) next.delete(i.id);
                        return next;
                      })
                    : selectAllOnPage()
                  }
                  className="h-4 w-4 rounded border-gray-300 text-brand-teal focus:ring-brand-teal"
                />
                <span className="text-gray-500">Select all on this page</span>
              </div>
            )}

            <ul className="space-y-1">
              {pageItems.map((item) => {
                const config = typeConfig[item.type];
                const isSelected = selected.has(item.id);
                return (
                  <li
                    key={item.id}
                    className={`group flex items-center gap-3 px-3 py-2.5 rounded-lg transition-colors cursor-pointer ${
                      isSelected
                        ? "bg-brand-teal/10 ring-1 ring-brand-teal/30"
                        : "hover:bg-gray-50"
                    }`}
                    onClick={() => toggleSelect(item.id)}
                  >
                    <input
                      type="checkbox"
                      checked={isSelected}
                      onChange={() => toggleSelect(item.id)}
                      onClick={(e) => e.stopPropagation()}
                      className="h-4 w-4 rounded border-gray-300 text-brand-teal focus:ring-brand-teal flex-shrink-0"
                    />
                    <div className={`w-8 h-8 rounded-full flex items-center justify-center flex-shrink-0 ${config.bgColor}`}>
                      <ActivityIcon type={item.type} />
                    </div>
                    <div className="flex-1 min-w-0">
                      <p className="text-sm font-medium text-gray-900 truncate">
                        {item.title}
                      </p>
                      <p className="text-xs text-gray-400">
                        {item.subtitle && <span className="text-gray-500">{item.subtitle} &middot; </span>}
                        {relativeTime(item.timestamp)}
                      </p>
                    </div>
                  </li>
                );
              })}
            </ul>
          </>
        )}

        {totalPages > 1 && (
          <div className="flex items-center justify-between mt-4 pt-4 border-t border-gray-100">
            <p className="text-xs text-gray-400">
              Page {clampedPage} of {totalPages} · {visibleItems.length} total
            </p>
            <div className="flex gap-2">
              <button
                type="button"
                onClick={() => setPage((p) => Math.max(1, p - 1))}
                disabled={clampedPage <= 1}
                className="px-3 py-1.5 text-xs font-medium text-gray-700 bg-white border border-gray-200 rounded-lg hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
              >
                Previous
              </button>
              <button
                type="button"
                onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
                disabled={clampedPage >= totalPages}
                className="px-3 py-1.5 text-xs font-medium text-gray-700 bg-white border border-gray-200 rounded-lg hover:bg-gray-50 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
              >
                Next
              </button>
            </div>
          </div>
        )}
      </div>

      {/* Floating recycle-bin FAB — bottom-right, always accessible */}
      {hydrated && (
        <button
          type="button"
          onClick={() => setBinOpen(true)}
          className="fixed bottom-6 right-6 z-40 h-14 w-14 rounded-full bg-white shadow-lg border border-gray-200 hover:shadow-xl hover:border-brand-teal flex items-center justify-center transition-all"
          title="Recycle bin"
        >
          <TrashIcon className="h-6 w-6 text-gray-600" />
          {bin.length > 0 && (
            <span className="absolute -top-1 -right-1 min-w-[22px] h-[22px] px-1.5 rounded-full bg-red-500 text-white text-xs font-bold flex items-center justify-center shadow">
              {bin.length > 99 ? "99+" : bin.length}
            </span>
          )}
        </button>
      )}

      {binOpen && (
        <RecycleBinModal
          entries={bin}
          onClose={() => setBinOpen(false)}
          onRestore={restoreFromBin}
          onDeletePermanently={deletePermanentlyFromBin}
        />
      )}
    </>
  );
}

/* ── Recycle bin modal ─────────────────────────────────────────────────── */

function RecycleBinModal({
  entries,
  onClose,
  onRestore,
  onDeletePermanently,
}: {
  entries: BinEntry[];
  onClose: () => void;
  onRestore: (ids: string[]) => void;
  onDeletePermanently: (ids: string[]) => void;
}) {
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const sorted = [...entries].sort((a, b) => b.dismissedAt.localeCompare(a.dismissedAt));

  function toggle(id: string) {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  const allSelected = sorted.length > 0 && sorted.every((e) => selected.has(e.id));

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
      onClick={onClose}
    >
      <div
        className="bg-white rounded-2xl shadow-2xl max-w-2xl w-full max-h-[80vh] overflow-hidden flex flex-col"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between px-5 py-4 border-b border-gray-100">
          <div className="flex items-center gap-3">
            <TrashIcon className="h-5 w-5 text-gray-500" />
            <div>
              <h2 className="text-lg font-bold text-gray-900">Recycle Bin</h2>
              <p className="text-xs text-gray-400 mt-0.5">
                {sorted.length} dismissed activit{sorted.length === 1 ? "y" : "ies"}
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close"
            className="text-gray-400 hover:text-gray-600 p-1 rounded-lg hover:bg-gray-100"
          >
            <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        {sorted.length > 0 && (
          <div className="flex items-center justify-between gap-2 px-5 py-3 border-b border-gray-100 bg-gray-50/50">
            <label className="flex items-center gap-2 text-sm text-gray-700">
              <input
                type="checkbox"
                checked={allSelected}
                onChange={() => setSelected(allSelected ? new Set() : new Set(sorted.map((e) => e.id)))}
                className="h-4 w-4 rounded border-gray-300 text-brand-teal focus:ring-brand-teal"
              />
              Select all
            </label>
            {selected.size > 0 && (
              <div className="flex gap-2">
                <button
                  type="button"
                  onClick={() => { onRestore(Array.from(selected)); setSelected(new Set()); }}
                  className="px-3 py-1.5 text-xs font-semibold bg-brand-teal text-white rounded-lg hover:bg-brand-teal-dark transition-colors"
                >
                  Restore ({selected.size})
                </button>
                <button
                  type="button"
                  onClick={() => { onDeletePermanently(Array.from(selected)); setSelected(new Set()); }}
                  className="px-3 py-1.5 text-xs font-semibold bg-red-500 text-white rounded-lg hover:bg-red-600 transition-colors"
                >
                  Delete permanently
                </button>
              </div>
            )}
          </div>
        )}

        <ul className="overflow-y-auto divide-y divide-gray-50">
          {sorted.length === 0 ? (
            <li className="px-5 py-12 text-center text-gray-400">Bin is empty.</li>
          ) : (
            sorted.map((entry) => {
              const config = typeConfig[entry.type];
              const isSelected = selected.has(entry.id);
              return (
                <li
                  key={entry.id}
                  className={`flex items-center gap-3 px-5 py-3 cursor-pointer transition-colors ${
                    isSelected ? "bg-brand-teal/5" : "hover:bg-gray-50"
                  }`}
                  onClick={() => toggle(entry.id)}
                >
                  <input
                    type="checkbox"
                    checked={isSelected}
                    onChange={() => toggle(entry.id)}
                    onClick={(e) => e.stopPropagation()}
                    className="h-4 w-4 rounded border-gray-300 text-brand-teal focus:ring-brand-teal flex-shrink-0"
                  />
                  <div className={`w-8 h-8 rounded-full flex items-center justify-center flex-shrink-0 ${config.bgColor}`}>
                    <ActivityIcon type={entry.type} />
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-medium text-gray-900 truncate">{entry.title}</p>
                    <p className="text-xs text-gray-400">
                      Deleted {relativeTime(entry.dismissedAt)}
                    </p>
                  </div>
                </li>
              );
            })
          )}
        </ul>
      </div>
    </div>
  );
}

function TrashIcon({ className = "" }: { className?: string }) {
  return (
    <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
      <path strokeLinecap="round" strokeLinejoin="round" d="M14.74 9l-.346 9m-4.788 0L9.26 9m9.968-3.21c.342.052.682.107 1.022.166m-1.022-.165L18.16 19.673a2.25 2.25 0 01-2.244 2.077H8.084a2.25 2.25 0 01-2.244-2.077L4.772 5.79m14.456 0a48.108 48.108 0 00-3.478-.397m-12 .562c.34-.059.68-.114 1.022-.165m0 0a48.11 48.11 0 013.478-.397m7.5 0v-.916c0-1.18-.91-2.164-2.09-2.201a51.964 51.964 0 00-3.32 0c-1.18.037-2.09 1.022-2.09 2.201v.916m7.5 0a48.667 48.667 0 00-7.5 0" />
    </svg>
  );
}
