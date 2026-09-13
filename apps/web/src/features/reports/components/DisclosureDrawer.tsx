import { DetailGrid, DetailTile, DrawerSection } from "../../../components/ui/DetailList";
import type { CompensationBand, Disclosure, ReportRemuneration } from "../api/types";
import { formatMoneyK } from "../lib/figures";
import { OUTCOME_LABEL } from "./CompensationStrip";
import { DrawerNote, ReportDrawer } from "./ReportDrawer";

function againstBand(value: number, band: CompensationBand): string {
  if (value < band.lowK) return `${formatMoneyK(band.lowK - value)} below floor`;
  if (value > band.highK) return `${formatMoneyK(value - band.highK)} above ceiling`;
  return "within band";
}

/** One dot of the compensation strip opened: the person, both figures, and how each sits against us. */
export function DisclosureDrawer({
  disclosure,
  remuneration,
  onClose,
}: {
  disclosure: Disclosure | null;
  remuneration: ReportRemuneration;
  onClose: () => void;
}) {
  const d = disclosure;
  const rows = d
    ? [
        ["Company", d.company],
        ["Title", d.title],
        ["Country", d.country],
        ["Nationality", d.nationality],
        ["Vs. our package band", againstBand(d.packageK, remuneration.packageBand)],
        ["Vs. our fixed band", againstBand(d.fixedK, remuneration.fixedBand)],
      ]
    : [];
  return (
    <ReportDrawer
      open={d !== null}
      onClose={onClose}
      eyebrow="Verified disclosure"
      title={d?.name ?? ""}
      subtitle={d ? `${d.title} · ${d.company}` : ""}
    >
      {d && (
        <>
          <DrawerSection title="Disclosed">
            <DetailGrid>
              <DetailTile label="Total package" value={formatMoneyK(d.packageK)} />
              <DetailTile label="Total fixed" value={formatMoneyK(d.fixedK)} />
              <DetailTile label="Status" value={OUTCOME_LABEL[d.outcome]} full />
            </DetailGrid>
          </DrawerSection>
          <DrawerSection title="Current placement">
            {rows.map(([label, value]) => (
              <div key={label} className="flex justify-between gap-3 border-b border-line-soft py-[7px] text-[12.5px] text-text2 last:border-b-0">
                <span>{label}</span>
                <b className="text-right font-semibold text-text">{value}</b>
              </div>
            ))}
          </DrawerSection>
          <DrawerSection title="Note">
            <DrawerNote label="From the conversation">{d.note}</DrawerNote>
          </DrawerSection>
        </>
      )}
    </ReportDrawer>
  );
}
