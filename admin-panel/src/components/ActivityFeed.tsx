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
}

export default function ActivityFeed({ items }: ActivityFeedProps) {
  return (
    <div className="bg-white rounded-xl border border-gray-100 shadow-sm p-6">
      <div className="flex items-center gap-2 mb-5">
        <svg className="h-5 w-5 text-brand-teal" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M3.75 13.5l10.5-11.25L12 10.5h8.25L9.75 21.75 12 13.5H3.75z" />
        </svg>
        <h2 className="text-base font-semibold text-gray-900">Recent Activity</h2>
      </div>

      {items.length === 0 ? (
        <p className="text-sm text-gray-400 py-4">No recent activity.</p>
      ) : (
        <ul className="space-y-1">
          {items.map((item) => {
            const config = typeConfig[item.type];
            return (
              <li
                key={item.id}
                className="flex items-center gap-3 px-3 py-2.5 rounded-lg hover:bg-gray-50 transition-colors"
              >
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
      )}
    </div>
  );
}
