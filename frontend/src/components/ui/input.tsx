import * as React from 'react';
import { cn } from '@/lib/utils';
const Input = React.forwardRef<HTMLInputElement, React.ComponentProps<'input'>>(({ className, type, ...props }, ref) => (
  <input type={type} ref={ref} className={cn('flex h-12 w-full rounded-lg border border-border bg-white px-3 text-sm outline-none placeholder:text-slate-400 focus-visible:ring-2 focus-visible:ring-teal-500 disabled:opacity-50', className)} {...props}/>
));
Input.displayName = 'Input';
export { Input };
