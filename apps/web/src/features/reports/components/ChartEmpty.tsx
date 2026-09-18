import type { ReactNode } from "react";

/** What a chart says when its slice holds nothing, so a gap reads as a statement and not a fault. */
export function ChartEmpty({ children }: { children: ReactNode }) {
  return <div className="py-[30px] text-center text-[12.5px] text-u-text3">{children}</div>;
}
