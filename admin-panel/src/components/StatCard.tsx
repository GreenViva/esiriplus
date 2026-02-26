import Link from "next/link";

interface StatCardProps {
  label: string;
  value: string | number;
  icon: React.ReactNode;
  iconBg: string;
  href?: string;
}

export default function StatCard({ label, value, icon, iconBg, href }: StatCardProps) {
  const card = (
    <div className={`bg-white rounded-xl border border-gray-100 shadow-sm p-5${href ? " hover:border-gray-200 hover:shadow-md transition-all" : ""}`}>
      <div className={`w-10 h-10 rounded-xl flex items-center justify-center mb-3 ${iconBg}`}>
        {icon}
      </div>
      <p className="text-2xl font-bold text-gray-900">{value}</p>
      <p className="text-xs text-gray-400 mt-0.5">{label}</p>
    </div>
  );

  if (href) {
    return <Link href={href}>{card}</Link>;
  }

  return card;
}
