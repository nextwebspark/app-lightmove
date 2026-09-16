import { DetailGrid, DetailTile, DrawerSection } from "../../../components/ui/DetailList";
import { candidateStatusStyle } from "../../candidates/lib/candidateVocabulary";
import type { CompensationBand, Disclosure, ReportRemuneration } from "../api/types";
import { formatCompactMoney } from "../lib/figures";
import { DrawerNote, ReportDrawer } from "./ReportDrawer";

function againstBand(currency: string, value: number, band: CompensationBand | null): string {
  if (band === null) return "no band in the brief";
  if (value < band.low) return `${formatCompactMoney(currency, band.low - value)} below floor`;
  if (value > band.high) return `${formatCompactMoney(currency, value - band.high)} above ceiling`;
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
  const currency = remuneration.currency;
  const rows = d
    ? [
        ["Company", d.company ?? "—"],
        ["Title", d.title ?? "—"],
        ["Country", d.country ?? "—"],
        ["Nationality", d.nationality ?? "—"],
        ["Vs. our package band", againstBand(currency, d.totalPackage, remuneration.packageBand)],
        ["Vs. our fixed band", againstBand(currency, d.fixed, remuneration.fixedBand)],
      ]
    : [];
  return (
    <ReportDrawer
      open={d !== null}
      onClose={onClose}
      eyebrow="Disclosed compensation"
      title={d?.fullName ?? ""}
      subtitle={d ? [d.title, d.company].filter(Boolean).join(" · ") : ""}
    >
      {d && (
        <>
          <DrawerSection title="Disclosed">
            <DetailGrid>
              <DetailTile label="Total package" value={formatCompactMoney(currency, d.totalPackage)} />
              <DetailTile label="Fixed" value={formatCompactMoney(currency, d.fixed)} />
              <DetailTile label="Status" value={candidateStatusStyle(d.status).label} full />
            </DetailGrid>
          </DrawerSection>
          <DrawerSection title="Current placement">
            {rows.map(([label, value]) => (
              <div key={label} className="flex justify-between gap-3 border-b border-u-border py-[7px] text-[12.5px] text-u-text2 last:border-b-0">
                <span>{label}</span>
                <b className="text-right font-semibold text-u-text">{value}</b>
              </div>
            ))}
          </DrawerSection>
          {d.note && (
            <DrawerSection title="Note">
              <DrawerNote label="From the research">{d.note}</DrawerNote>
            </DrawerSection>
          )}
        </>
      )}
    </ReportDrawer>
  );
}
