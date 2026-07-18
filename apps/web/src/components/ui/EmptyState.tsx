import type { ReactNode } from "react";

/** The mockups' centered empty state: amber icon tile, title, one line of copy, optional action. */
export function EmptyState({
  icon,
  title,
  body,
  children,
}: {
  icon: ReactNode;
  title: string;
  body: string;
  children?: ReactNode;
}) {
  return (
    <div className="flex animate-fade-up flex-col items-center justify-center px-5 pb-[70px] pt-[90px] text-center">
      <div className="mb-[18px] grid size-[52px] place-items-center rounded-[14px] bg-amber-dim text-amber">
        {icon}
      </div>
      <div className="mb-1.5 text-[19px] font-semibold">{title}</div>
      <div className="mb-[22px] max-w-[420px] text-[13px] text-text2">{body}</div>
      {children}
    </div>
  );
}
