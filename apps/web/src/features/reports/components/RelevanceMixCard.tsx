import { relevanceMix } from "../lib/relevanceMix";
import { ReportCard } from "./ReportCard";
import { StackedBar } from "./StackedBar";

/**
 * How directly each mapped company competes for this mandate.
 *
 * <p><b>Not yet measured.</b> Nothing records a company's relevance, so the split is a fixed
 * illustration and the card says so twice — in the caption and in the note — rather than letting a
 * reader take three tidy bands for a finding. See `lib/relevanceMix.ts` for what it would take to
 * derive it.
 */
export function RelevanceMixCard({ universeCount }: { universeCount: number }) {
  const mix = relevanceMix(universeCount);
  if (universeCount === 0) {
    return null;
  }

  return (
    <ReportCard
      title="Relevance mix"
      caption="how directly each mapped company competes — illustrative, not yet measured"
      note={
        <>
          <b>These bands are not derived from your rows.</b> Nothing records today whether a company
          was reached directly, as an adjacency, or by inference, so this shows the shape the card
          will take rather than this mandate's own mix. Treat the bars as a placeholder.
        </>
      }
    >
      <StackedBar
        className="mt-4"
        segments={mix.bands.map((band) => ({
          label: band.label,
          count: band.count,
          fillClass: band.fillClass,
        }))}
      />
    </ReportCard>
  );
}
