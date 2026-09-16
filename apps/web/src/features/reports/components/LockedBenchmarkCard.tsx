import { Icon, ICONS } from "../../../components/layout/Icon";

/**
 * A chapter's cross-mandate benchmark, named and marked as not built.
 *
 * <p>Every figure in this report comes from one mandate's own rows, so none of it answers "is that
 * high?". Saying so where the comparison would sit is better than a silent gap, which reads as
 * though the question was never asked.
 *
 * <p>Deliberately no progress count. "2 of 5 mandates" would be a figure nothing computes, and a
 * fabricated number on a page whose whole claim is that it invents none would cost more than the
 * card is worth.
 */
export function LockedBenchmarkCard({ children }: { children: React.ReactNode }) {
  return (
    <div className="mt-3.5 rounded-[10px] border border-dashed border-u-border-strong bg-u-raised px-[18px] py-4">
      <div className="flex items-center gap-2.5">
        <Icon d={ICONS.lock} size={15} className="flex-none text-u-text3" />
        <div className="text-[12.5px] text-u-text2">{children}</div>
      </div>
    </div>
  );
}
