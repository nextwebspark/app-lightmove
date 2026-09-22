import { useState, type FormEvent } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { cn } from "../../../lib/cn";
import type { DiscoveryAnswer } from "../api/types";

/**
 * The question AI Research is asked, drawn over the grid it is about to replace.
 *
 * <p><b>It holds its own state and never touches the saved filter.</b> That is the trap this screen
 * is built around: bands, sector groups and market segments are the universe's own vocabulary,
 * chosen so a SQL predicate can be built from them, and a question put to a web search is a
 * sentence. A panel that wrote into the mandate's filter would leave a consultant with a saved
 * search nobody asked for and an answer that honoured neither half.
 */
export function AiResearchPanel({
  answer,
  searching,
  failure,
  searchesLeftToday,
  onSearch,
  onClose,
}: {
  /** The last answer, if there is one. Its presence collapses the panel to a header. */
  answer: DiscoveryAnswer | null;
  searching: boolean;
  /** Already turned into a sentence by `messageFor` — this never switches on a code itself. */
  failure: string | null;
  searchesLeftToday: number | null;
  onSearch: (question: string, country: string) => void;
  onClose: () => void;
}) {
  const [question, setQuestion] = useState("");
  const [country, setCountry] = useState("");

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault();
    if (!question.trim() || searching) return;
    onSearch(question.trim(), country.trim());
  };

  return (
    <section
      aria-label="AI Research"
      className="absolute inset-x-0 top-0 z-20 border-b border-line bg-panel shadow-[0_8px_24px_-12px_rgba(0,0,0,0.25)]"
    >
      <form onSubmit={handleSubmit} className="flex flex-col gap-3 p-4">
        <div className="flex items-center gap-2">
          <Icon d={ICONS.sparkle} size={14} className="text-ai" />
          <h2 className="font-mono text-[11px] font-semibold uppercase tracking-[0.06em] text-text2">
            AI Research
          </h2>
          <span className="ms-auto flex items-center gap-3">
            {searchesLeftToday !== null && (
              <span className="font-mono text-[11px] text-text3">
                {searchesLeftToday} left today
              </span>
            )}
            <button
              type="button"
              onClick={onClose}
              className="rounded-md p-1 text-text3 hover:bg-line-soft hover:text-text"
              aria-label="Close AI Research"
            >
              <Icon d={ICONS.close} size={14} />
            </button>
          </span>
        </div>

        <label className="flex flex-col gap-1.5">
          <span className="font-sans text-[12px] text-text2">
            What are you looking for that the universe does not carry?
          </span>
          <textarea
            value={question}
            onChange={(event) => setQuestion(event.target.value)}
            rows={2}
            maxLength={500}
            placeholder="Independent power producers across the GCC with a renewables arm"
            className="resize-none rounded-md border border-line bg-bg px-3 py-2 font-sans text-[13px] text-text placeholder:text-text3 focus:border-ai focus:outline-none"
          />
        </label>

        <div className="flex flex-wrap items-end gap-3">
          <label className="flex flex-col gap-1.5">
            <span className="font-sans text-[12px] text-text2">Country (optional)</span>
            <input
              value={country}
              onChange={(event) => setCountry(event.target.value)}
              maxLength={100}
              placeholder="Saudi Arabia"
              className="w-56 rounded-md border border-line bg-bg px-3 py-1.5 font-sans text-[13px] text-text placeholder:text-text3 focus:border-ai focus:outline-none"
            />
          </label>

          <button
            type="submit"
            disabled={searching || !question.trim()}
            className={cn(
              "inline-flex items-center gap-2 rounded-md border border-[#4f46e5] px-4 py-2",
              "bg-[linear-gradient(90deg,#6366f1,#3b82f6)] font-sans text-[13px] font-semibold text-white",
              "disabled:cursor-not-allowed disabled:opacity-50",
            )}
          >
            <Icon d={ICONS.sparkle} size={13} />
            {searching ? "Searching the web…" : "Search"}
          </button>

          {answer && !searching && (
            <p className="font-mono text-[11px] text-text3">
              {answer.companies.length} found ·{" "}
              {answer.mode === "GROUNDED_PROSE_EXTRACTED"
                ? "searched the web, then read in a second pass"
                : answer.mode === "UNAVAILABLE"
                  ? "the research provider could not be reached"
                  : "searched the web"}
            </p>
          )}
        </div>

        {failure && (
          <p role="alert" className="font-sans text-[12px] text-red">
            {failure}
          </p>
        )}

        {/* Said on the screen rather than only in the code, because a consultant reading an empty
            Employees cell deserves to know it is a fact about our records and not a bug. */}
        <p className="font-sans text-[11.5px] text-text3">
          Figures come from our own records. A company we hold nothing on arrives with its name and
          empty columns rather than a guess.
        </p>
      </form>
    </section>
  );
}
