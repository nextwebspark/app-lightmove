import { useEffect, useRef, useState } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { useEscapeKey } from "../../../lib/useEscapeKey";
import { useAssistant } from "../AssistantProvider";

const STARTERS = [
  "Top 10 IPP operators in Saudi Arabia",
  "Which companies in my universe have no executives mapped?",
  "Show the market by country for this filter",
];

/**
 * The assistant, docked beside the page rather than over it.
 *
 * <p><b>Not a {@link Drawer}.</b> That one is `role="dialog"` with `aria-modal` behind a scrim,
 * which is right for reading one record and wrong here: the point is to have the assistant open
 * *while* you tick rows in the grid next to it. A scrim would make the page unclickable and
 * `aria-modal` would tell a screen reader the rest of it does not exist. So this is
 * `role="complementary"`, with no scrim and no focus trap, and the page underneath stays live.
 *
 * <p>It holds no state worth keeping — {@link AssistantProvider} does — so remounting it as the
 * user crosses between layouts costs nothing.
 */
export function AssistantPanel({ contextLabel }: { contextLabel: string }) {
  const { open, toggledByUser, closeAssistant } = useAssistant();
  const [draft, setDraft] = useState("");
  const composer = useRef<HTMLTextAreaElement>(null);

  useEscapeKey(open, closeAssistant);

  // The composer rather than the close button: somebody who just pressed "AI Research" wants to
  // type. Guarded on the toggle so restoring a remembered panel never steals the caret.
  useEffect(() => {
    if (open && toggledByUser) composer.current?.focus();
  }, [open, toggledByUser]);

  if (!open) return null;

  return (
    <aside
      role="complementary"
      aria-label="Uncava Assistant"
      className="ms-2.5 hidden w-[400px] flex-none animate-slide-in-end flex-col overflow-hidden rounded-[10px] border border-line bg-panel lg:flex"
    >
      <div className="flex-none border-b border-line px-3 py-2.5">
        <div className="flex items-center gap-2">
          <span className="grid h-[26px] w-[26px] flex-none place-items-center rounded-[7px] bg-[linear-gradient(135deg,var(--color-ai),var(--color-ai2))] text-white">
            <Icon d={ICONS.sparkle} size={14} />
          </span>
          <span className="font-sans text-[13px] font-semibold text-text">Assistant</span>
          <button
            type="button"
            onClick={closeAssistant}
            title="Close"
            aria-label="Close the assistant"
            className="ms-auto grid h-7 w-7 place-items-center rounded-md text-text3 transition hover:bg-panel2 hover:text-text"
          >
            <Icon d={ICONS.close} size={15} />
          </button>
        </div>

        {/* Which mandate the conversation is about. A thread keeps the context it was asked in, so
            this states the screen's, not wherever the reader has since navigated. */}
        <div className="mt-2.5 flex w-fit max-w-full items-center gap-1.5 rounded-md bg-panel2 px-2 py-1 font-mono text-[11px] text-text3">
          <Icon d="M12 2 3 7l9 5 9-5-9-5Z" size={11} className="flex-none text-amber" />
          <span className="truncate">{contextLabel}</span>
        </div>
      </div>

      <div className="flex min-h-0 flex-1 flex-col justify-center gap-4 overflow-y-auto px-3 py-4">
        <div className="text-center">
          <p className="font-sans text-[13px] text-text2">Ask about this market.</p>
          <p className="mt-1 font-mono text-[11px] text-text3">
            It reads the company universe and this mandate&apos;s own rows.
          </p>
        </div>
        <div>
          {STARTERS.map((starter) => (
            <button
              key={starter}
              type="button"
              onClick={() => setDraft(starter)}
              className="mb-1.5 block w-full rounded-lg border border-dashed border-line px-2.5 py-2 text-start font-sans text-xs text-text2 transition hover:border-solid hover:border-ai hover:bg-ai-soft hover:text-text"
            >
              {starter}
            </button>
          ))}
        </div>
      </div>

      <div className="flex-none border-t border-line px-3 pb-3 pt-2.5">
        <div className="rounded-[10px] border border-line bg-panel2 px-2.5 py-2">
          <textarea
            ref={composer}
            value={draft}
            onChange={(event) => setDraft(event.target.value)}
            placeholder="Ask about this market..."
            rows={2}
            aria-label="Ask the assistant"
            className="w-full resize-none border-none bg-transparent font-sans text-[13px] leading-[1.5] text-text outline-none"
          />
          <div className="mt-1 flex items-center gap-2">
            <span className="font-mono text-[10px] text-text3">Answers arrive next</span>
            <button
              type="button"
              disabled
              title="Asking arrives with the next change"
              className="ms-auto grid h-[26px] w-[26px] place-items-center rounded-md border-none bg-[linear-gradient(135deg,var(--color-ai),var(--color-ai2))] opacity-40"
            >
              <Icon d={ICONS.arrowUp} size={13} className="text-white" />
            </button>
          </div>
        </div>
      </div>
    </aside>
  );
}
