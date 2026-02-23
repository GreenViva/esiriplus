"use client";

import { ButtonHTMLAttributes } from "react";

type Variant = "primary" | "danger" | "outline";

const variantStyles: Record<Variant, string> = {
  primary:
    "bg-brand-teal text-white hover:bg-brand-teal-dark",
  danger:
    "bg-red-600 text-white hover:bg-red-700",
  outline:
    "border border-gray-300 text-gray-700 hover:bg-gray-50",
};

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  loading?: boolean;
}

export default function Button({
  variant = "primary",
  loading = false,
  disabled,
  children,
  className = "",
  ...props
}: ButtonProps) {
  return (
    <button
      disabled={disabled || loading}
      className={`inline-flex items-center justify-center px-4 py-2 rounded-md text-sm font-medium transition-colors disabled:opacity-50 ${variantStyles[variant]} ${className}`}
      {...props}
    >
      {loading && (
        <svg
          className="animate-spin -ml-1 mr-2 h-4 w-4"
          fill="none"
          viewBox="0 0 24 24"
        >
          <circle
            className="opacity-25"
            cx="12"
            cy="12"
            r="10"
            stroke="currentColor"
            strokeWidth="4"
          />
          <path
            className="opacity-75"
            fill="currentColor"
            d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"
          />
        </svg>
      )}
      {children}
    </button>
  );
}
