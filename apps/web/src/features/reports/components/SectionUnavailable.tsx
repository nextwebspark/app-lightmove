/**
 * What a section says when nothing backs it yet. The mockup fills these from mapped executives —
 * mapping velocity, diversity, interest, captured compensation, confidence — and that table does not
 * exist. Saying so is the point: a zero here would be read as a finding about the search rather than
 * a fact about the product, which is exactly the lie an empty state over a refused read tells.
 */
export function SectionUnavailable({ body }: { body: string }) {
  return (
    <div className="rounded-[10px] border border-dashed border-line bg-panel2 px-[18px] py-6">
      <div className="font-mono text-[10px] font-semibold uppercase tracking-[0.12em] text-text3">
        Not measured yet
      </div>
      <p className="mt-2 max-w-[560px] text-[13px] leading-[1.6] text-text2">{body}</p>
    </div>
  );
}
