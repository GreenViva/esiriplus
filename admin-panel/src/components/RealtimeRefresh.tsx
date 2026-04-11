"use client";

import { useEffect, useRef, useCallback } from "react";
import { createClient } from "@/lib/supabase/client";

interface Props {
  /** Tables to subscribe to for changes */
  tables: string[];
  /** Unique channel name */
  channelName: string;
  /** Callback fired when a change is detected (debounced). If omitted, uses router.refresh(). */
  onUpdate?: () => void;
}

/**
 * Invisible component that subscribes to Supabase Realtime
 * postgres_changes on the given tables and invokes a callback
 * when a change is detected. Uses targeted refetch instead of
 * full page reload to preserve scroll position, form state, and filters.
 */
export default function RealtimeRefresh({ tables, channelName, onUpdate }: Props) {
  const tablesKey = tables.join(",");
  const refreshTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const onUpdateRef = useRef(onUpdate);
  onUpdateRef.current = onUpdate;

  const handleChange = useCallback(() => {
    if (refreshTimer.current) clearTimeout(refreshTimer.current);
    refreshTimer.current = setTimeout(() => {
      if (onUpdateRef.current) {
        onUpdateRef.current();
      } else {
        window.location.reload();
      }
    }, 500);
  }, []);

  useEffect(() => {
    const supabase = createClient();

    let channel = supabase.channel(channelName);

    for (const table of tablesKey.split(",")) {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      channel = (channel as any).on(
        "postgres_changes",
        { event: "*", schema: "public", table },
        handleChange,
      );
    }

    channel.subscribe((status, err) => {
      if (status === "CHANNEL_ERROR") {
        console.warn(`[Realtime] ${channelName}: channel error — ensure Realtime is enabled for tables:`, tables, err);
      } else if (status === "TIMED_OUT") {
        console.warn(`[Realtime] ${channelName}: subscription timed out, retrying…`);
        supabase.removeChannel(channel);
      }
    });

    return () => {
      if (refreshTimer.current) clearTimeout(refreshTimer.current);
      supabase.removeChannel(channel);
    };
  }, [tablesKey, channelName, handleChange]);

  return null;
}
