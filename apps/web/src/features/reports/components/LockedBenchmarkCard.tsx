import type { ReactNode } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { ReportPanel } from "./ReportCard";

/**
 * A chapter's cross-mandate benchmark, named and marked as not built.
 *
 * <p>Deliberately no progress count. "2 of 5 mandates" would be a figure nothing computes, and a
 * fabricated number on a page whose whole claim is that it invents none would cost more than the
 * card is worth.
 */
export function LockedBenchmarkCard({ children }: { children: ReactNode }) {
  return (
    <ReportPanel dashed>
      <div className="flex items-center gap-2.5">
        <Icon d={ICONS.lock} size={15} className="flex-none text-u-text3" />
        <div className="text-[12.5px] text-u-text2 [&_b]:font-bold [&_b]:text-u-text">{children}</div>
      </div>
    </ReportPanel>
  );
}
