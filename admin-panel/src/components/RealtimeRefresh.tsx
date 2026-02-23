"use client";

import { useEffect, useRef } from "react";
import { useRouter } from "next/navigation";
import { createClient } from "@/lib/supabase/client";

interface Props {
  /** Tables to subscribe to for changes */
  tables: string[];
  /** Unique channel name */
  channelName: string;
}

/**
 * Invisible component that subscribes to Supabase Realtime
 * postgres_changes on the given tables and calls router.refresh()
 * whenever a change is detected. This keeps Server Components
 * in sync across multiple browser tabs / users.
 */
export default function RealtimeRefresh({ tables, channelName }: Props) {
  const router = useRouter();
  // Stable ref to avoid re-subscribing on every render
  const tablesKey = tables.join(",");
  const refreshTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    const supabase = createClient();

    let channel = supabase.channel(channelName);

    for (const table of tablesKey.split(",")) {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      channel = (channel as any).on(
        "postgres_changes",
        { event: "*", schema: "public", table },
        () => {
          // Debounce: batch rapid changes into a single refresh
          if (refreshTimer.current) clearTimeout(refreshTimer.current);
          refreshTimer.current = setTimeout(() => {
            router.refresh();
          }, 500);
        },
      );
    }

    channel.subscribe();

    return () => {
      if (refreshTimer.current) clearTimeout(refreshTimer.current);
      supabase.removeChannel(channel);
    };
  }, [tablesKey, channelName, router]);

  return null;
}
