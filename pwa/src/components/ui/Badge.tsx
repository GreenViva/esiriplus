'use client';

type BadgeVariant = 'teal' | 'purple' | 'gold' | 'red' | 'green' | 'gray';

interface BadgeProps {
  children: React.ReactNode;
  variant?: BadgeVariant;
  className?: string;
}

const variantStyles: Record<BadgeVariant, string> = {
  teal: 'bg-[var(--brand-teal)]/10 text-[var(--brand-teal)]',
  purple: 'bg-[var(--royal-purple)]/10 text-[var(--royal-purple)]',
  gold: 'bg-[var(--royal-gold)]/10 text-[var(--royal-gold)]',
  red: 'bg-[var(--error-red)]/10 text-[var(--error-red)]',
  green: 'bg-[var(--success-green)]/10 text-[var(--success-green)]',
  gray: 'bg-gray-100 text-[var(--subtitle-grey)]',
};

export function Badge({ children, variant = 'teal', className = '' }: BadgeProps) {
  return (
    <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold ${variantStyles[variant]} ${className}`}>
      {children}
    </span>
  );
}
