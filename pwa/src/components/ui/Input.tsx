'use client';

import { forwardRef } from 'react';

interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  error?: string;
  icon?: React.ReactNode;
}

export const Input = forwardRef<HTMLInputElement, InputProps>(
  ({ label, error, icon, className = '', ...props }, ref) => {
    return (
      <div className="w-full">
        {label && (
          <label className="block text-sm font-semibold text-black mb-1.5">
            {label}
            {props.required && <span className="text-[var(--error-red)] ml-0.5">*</span>}
          </label>
        )}
        <div className="relative">
          {icon && (
            <div className="absolute left-3 top-1/2 -translate-y-1/2 text-[var(--brand-teal)]">
              {icon}
            </div>
          )}
          <input
            ref={ref}
            className={`
              w-full h-11 px-3 text-sm text-black placeholder-gray-400
              border border-[var(--card-border)] rounded-xl
              focus:outline-none focus:border-[var(--brand-teal)] focus:ring-1 focus:ring-[var(--brand-teal)]
              transition-colors
              ${icon ? 'pl-10' : ''}
              ${error ? 'border-[var(--error-red)]' : ''}
              ${className}
            `}
            {...props}
          />
        </div>
        {error && <p className="mt-1 text-xs text-[var(--error-red)]">{error}</p>}
      </div>
    );
  },
);
Input.displayName = 'Input';

interface TextAreaProps extends React.TextareaHTMLAttributes<HTMLTextAreaElement> {
  label?: string;
  error?: string;
}

export const TextArea = forwardRef<HTMLTextAreaElement, TextAreaProps>(
  ({ label, error, className = '', ...props }, ref) => {
    return (
      <div className="w-full">
        {label && (
          <label className="block text-sm font-semibold text-black mb-1.5">
            {label}
            {props.required && <span className="text-[var(--error-red)] ml-0.5">*</span>}
          </label>
        )}
        <textarea
          ref={ref}
          className={`
            w-full px-3 py-2.5 text-sm text-black placeholder-gray-400
            border border-[var(--card-border)] rounded-xl resize-none
            focus:outline-none focus:border-[var(--brand-teal)] focus:ring-1 focus:ring-[var(--brand-teal)]
            transition-colors min-h-[80px]
            ${error ? 'border-[var(--error-red)]' : ''}
            ${className}
          `}
          {...props}
        />
        {error && <p className="mt-1 text-xs text-[var(--error-red)]">{error}</p>}
      </div>
    );
  },
);
TextArea.displayName = 'TextArea';
