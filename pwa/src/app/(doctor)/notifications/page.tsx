'use client';

import { useState, useEffect } from 'react';
import {
  Bell,
  MessageSquare,
  DollarSign,
  Shield,
  Check,
  CheckCheck,
} from 'lucide-react';
import { Card, Badge, Button } from '@/components/ui';
import BackButton from '@/components/ui/BackButton';
import { useAuthStore } from '@/store/auth';
import { useSupabase } from '@/hooks/useSupabase';
import type { AppNotification } from '@/types';

function formatTimeAgo(ts: number) {
  const diff = Date.now() - ts;
  const mins = Math.floor(diff / 60000);
  if (mins < 1) return 'just now';
  if (mins < 60) return `${mins}m ago`;
  const hrs = Math.floor(mins / 60);
  if (hrs < 24) return `${hrs}h ago`;
  const days = Math.floor(hrs / 24);
  if (days < 7) return `${days}d ago`;
  return new Date(ts).toLocaleDateString('en-GB', { day: 'numeric', month: 'short' });
}

function getNotificationIcon(type: string) {
  switch (type) {
    case 'consultation_request':
    case 'consultation':
      return <MessageSquare size={18} className="text-[var(--brand-teal)]" />;
    case 'payment':
    case 'earnings':
      return <DollarSign size={18} className="text-[var(--royal-gold)]" />;
    case 'admin':
    case 'system':
      return <Shield size={18} className="text-[var(--royal-purple)]" />;
    default:
      return <Bell size={18} className="text-[var(--subtitle-grey)]" />;
  }
}

function getNotificationBg(type: string) {
  switch (type) {
    case 'consultation_request':
    case 'consultation':
      return 'bg-[var(--brand-teal)]/10';
    case 'payment':
    case 'earnings':
      return 'bg-[var(--royal-gold)]/10';
    case 'admin':
    case 'system':
      return 'bg-[var(--royal-purple)]/10';
    default:
      return 'bg-gray-100';
  }
}

export default function NotificationsPage() {
  const { session } = useAuthStore();
  const db = useSupabase();
  const [notifications, setNotifications] = useState<AppNotification[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadNotifications();
  }, []);

  async function loadNotifications() {
    if (!db || !session?.user?.id) {
      setLoading(false);
      return;
    }
    try {
      const { data } = await db
        .from('notifications')
        .select('*')
        .eq('user_id', session.user.id)
        .order('created_at', { ascending: false });
      const rows = (data ?? []) as Array<{
        notification_id: string;
        title: string;
        body: string;
        type: string;
        is_read: boolean;
        created_at: string;
      }>;
      setNotifications(rows.map((r) => ({
        notificationId: r.notification_id,
        title: r.title ?? '',
        body: r.body ?? '',
        type: r.type ?? '',
        isRead: r.is_read ?? false,
        createdAt: new Date(r.created_at).getTime(),
      })));
    } catch {
      // empty
    } finally {
      setLoading(false);
    }
  }

  function persistMarkRead(ids: string[]) {
    const token = useAuthStore.getState().session?.accessToken;
    fetch('/api/notifications/mark-read', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ notification_ids: ids, token }),
    }).catch(() => {});
  }

  async function markAsRead(notificationId: string) {
    setNotifications((prev) =>
      prev.map((n) =>
        n.notificationId === notificationId ? { ...n, isRead: true } : n,
      ),
    );
    persistMarkRead([notificationId]);
  }

  async function markAllRead() {
    const unreadIds = notifications.filter((n) => !n.isRead).map((n) => n.notificationId);
    if (unreadIds.length === 0) return;
    setNotifications((prev) => prev.map((n) => ({ ...n, isRead: true })));
    persistMarkRead(unreadIds);
  }

  const unreadCount = notifications.filter((n) => !n.isRead).length;

  if (loading) {
    return (
      <div className="flex items-center justify-center min-h-[60vh]">
        <div className="w-8 h-8 border-3 border-[var(--brand-teal)] border-t-transparent rounded-full animate-spin" />
      </div>
    );
  }

  return (
    <div className="px-4 lg:px-8 py-6 max-w-4xl mx-auto">
      <BackButton href="/dashboard" />
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-[var(--brand-teal)]/10 flex items-center justify-center">
            <Bell size={20} className="text-[var(--brand-teal)]" />
          </div>
          <div>
            <h1 className="text-xl font-bold text-black">Notifications</h1>
            <p className="text-xs text-[var(--subtitle-grey)]">
              {unreadCount > 0 ? `${unreadCount} unread` : 'All caught up'}
            </p>
          </div>
        </div>
        {unreadCount > 0 && (
          <Button size="sm" variant="ghost" onClick={markAllRead}>
            <CheckCheck size={14} className="mr-1" /> Mark all read
          </Button>
        )}
      </div>

      {/* Notification list */}
      {notifications.length === 0 ? (
        <div className="text-center py-16">
          <div className="w-16 h-16 rounded-full bg-gray-100 flex items-center justify-center mx-auto mb-4">
            <Bell size={28} className="text-gray-400" />
          </div>
          <h3 className="text-base font-bold text-black mb-1">No notifications</h3>
          <p className="text-sm text-[var(--subtitle-grey)]">
            You are all caught up
          </p>
        </div>
      ) : (
        <div className="space-y-2">
          {notifications.map((n) => (
            <div
              key={n.notificationId}
              className={`flex items-start gap-3 p-4 rounded-2xl border transition-colors ${
                n.isRead
                  ? 'bg-white border-[var(--card-border)]'
                  : 'bg-[var(--brand-teal)]/5 border-[var(--brand-teal)]/20'
              }`}
            >
              <div className={`w-10 h-10 rounded-full ${getNotificationBg(n.type)} flex items-center justify-center shrink-0`}>
                {getNotificationIcon(n.type)}
              </div>
              <div className="flex-1 min-w-0">
                <p className="text-sm font-semibold text-black">{n.title}</p>
                <p className="text-xs text-[var(--subtitle-grey)] mt-0.5 line-clamp-2">{n.body}</p>
                <p className="text-[10px] text-[var(--subtitle-grey)] mt-1.5">{formatTimeAgo(n.createdAt)}</p>
              </div>
              {!n.isRead && (
                <button
                  onClick={() => markAsRead(n.notificationId)}
                  className="p-1.5 rounded-lg hover:bg-gray-100 shrink-0"
                  title="Mark as read"
                >
                  <Check size={14} className="text-[var(--brand-teal)]" />
                </button>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
