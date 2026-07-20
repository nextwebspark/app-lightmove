/**
 * The live match estimate: how many companies the current scope (sector + company size) selects. While
 * the scope is settling — suggestions loading, then the count recomputing — a row of pulsing dots stands
 * in, so the number lands once instead of jumping from the direct-only total to the full one.
 */
export function EstimateBanner({
  count,
  loading,
  onGoToSourcing,
}: {
  count: number | undefined;
  loading: boolean;
  onGoToSourcing?: () => void;
}) {
  const showCount = !loading && count !== undefined;
  return (
    <div className="mb-[18px] flex items-center gap-[14px] rounded-[10px] border border-line-soft bg-panel2 px-[18px] py-3">
      <div className="flex min-w-[52px] items-center font-mono text-[26px] font-bold text-amber">
        {showCount ? count.toLocaleString("en-US") : <LoadingDots />}
      </div>
      <div className="flex-1">
        <div className="font-sans text-[13px] font-semibold">companies match the current criteria</div>
        <div className="mt-px font-mono text-[11px] text-text3">
          updates live as you edit · Sourcing reads these criteria directly
        </div>
      </div>
      {onGoToSourcing ? (
        <button
          type="button"
          onClick={onGoToSourcing}
          className="flex flex-none items-center gap-[7px] rounded-[8px] border border-amber-btn bg-amber-btn px-[13px] py-[7px] font-sans text-[13px] font-semibold text-[#141414] hover:brightness-105"
        >
          Go to sourcing
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4">
            <path d="M5 12h14M13 6l6 6-6 6" />
          </svg>
        </button>
      ) : null}
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
