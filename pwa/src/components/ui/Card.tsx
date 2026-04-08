'use client';

interface CardProps {
  children: React.ReactNode;
  className?: string;
  onClick?: () => void;
  padding?: boolean;
}

export function Card({ children, className = '', onClick, padding = true }: CardProps) {
  return (
    <div
      className={`
        bg-white border border-[var(--card-border)] rounded-2xl shadow-sm
        ${onClick ? 'cursor-pointer hover:shadow-md transition-shadow active:scale-[0.99]' : ''}
        ${padding ? 'p-4' : ''}
        ${className}
      `}
      onClick={onClick}
    >
      {children}
    </div>
  );
}

export function GradientCard({
  children,
  gradient = 'teal',
  className = '',
  onClick,
}: {
  children: React.ReactNode;
  gradient?: 'teal' | 'royal' | 'agent';
  className?: string;
  onClick?: () => void;
}) {
  const gradients = {
    teal: 'from-[var(--brand-teal)] to-[#1A7A6E]',
    royal: 'from-[var(--royal-purple)] to-[#7C3AED]',
    agent: 'from-[var(--royal-gold)] to-[var(--agent-orange)]',
  };

  return (
    <div
      className={`
        bg-gradient-to-r ${gradients[gradient]} rounded-2xl text-white
        ${onClick ? 'cursor-pointer hover:shadow-lg transition-shadow active:scale-[0.99]' : ''}
        ${className}
      `}
      onClick={onClick}
    >
      {children}
    </div>
  );
}
