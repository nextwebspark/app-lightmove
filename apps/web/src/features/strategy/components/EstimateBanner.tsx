/**
 * The live match estimate: how many companies the current sector scope selects. While the scope is
 * settling — suggestions loading, then the count recomputing — a row of pulsing dots stands in, so
 * the number lands once instead of jumping from the direct-only total to the full one.
 */
export function EstimateBanner({ count, loading }: { count: number | undefined; loading: boolean }) {
  const showCount = !loading && count !== undefined;
  return (
    <div className="mb-[18px] flex items-center gap-[14px] rounded-[10px] border border-line-soft bg-panel2 px-[18px] py-3">
      <div className="flex min-w-[52px] items-center font-mono text-[26px] font-bold text-amber">
        {showCount ? count.toLocaleString("en-US") : <LoadingDots />}
      </div>
      <div className="flex-1">
        <div className="font-sans text-[13px] font-semibold">companies match this scope</div>
        <div className="mt-px font-mono text-[11px] text-text3">
          updates as you refine the sector scope
        </div>
      </div>
    </div>
  );
}

/** Three amber dots pulsing in sequence — a quieter loading cue than a spinner in a number slot. */
function LoadingDots() {
  return (
    <span className="flex items-center gap-1.5" aria-label="Counting" role="status">
      {[0, 150, 300].map((delay) => (
        <span
          key={delay}
          className="inline-block h-1.5 w-1.5 rounded-full bg-amber animate-[pulse_1s_ease-in-out_infinite]"
          style={{ animationDelay: `${delay}ms` }}
        />
      ))}
    </span>
  );
}
