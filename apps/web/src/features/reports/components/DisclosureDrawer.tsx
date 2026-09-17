import type { CompensationBand, Disclosure, ReportRemuneration } from "../api/types";
import { formatCompactMoney } from "../lib/figures";
import { DrawerContext, DrawerKpi, DrawerKpis, DrawerRow, DrawerSection, ReportDrawer, StatusPill } from "./ReportDrawer";

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
          <DrawerSection>
            <DrawerKpis columns={2}>
              <DrawerKpi value={formatCompactMoney(currency, d.totalPackage)} label="Total package" />
              <DrawerKpi value={formatCompactMoney(currency, d.fixed)} label="Total fixed" />
            </DrawerKpis>
          </DrawerSection>
          <DrawerSection label="Current placement">
            <DrawerRow label="Status">
              <StatusPill status={d.status} />
            </DrawerRow>
            <DrawerRow label="Company">{d.company ?? "—"}</DrawerRow>
            <DrawerRow label="Title">{d.title ?? "—"}</DrawerRow>
            <DrawerRow label="Country">{d.country ?? "—"}</DrawerRow>
            <DrawerRow label="Nationality">{d.nationality ?? "—"}</DrawerRow>
            <DrawerRow label="Vs. our package band">{againstBand(currency, d.totalPackage, remuneration.packageBand)}</DrawerRow>
            <DrawerRow label="Vs. our fixed band">{againstBand(currency, d.fixed, remuneration.fixedBand)}</DrawerRow>
          </DrawerSection>
          {d.note && <DrawerContext label="Note">{d.note}</DrawerContext>}
        </>
      )}
    </ReportDrawer>
  );
}
