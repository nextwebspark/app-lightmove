import { cn } from "../../../lib/cn";
import type { Conversation } from "../api/conversationApi";
import { glanceOf, historyRangeOf, TONE_TEXT } from "../lib/conversationVocabulary";

/** The head of the history: what has passed with the person so far, when they were last touched, and the span shown. */
export function ConversationGlance({ conversation }: { conversation: Conversation }) {
  const glance = glanceOf(conversation.events, conversation.currentRun.run);
  const range = historyRangeOf(conversation.events);
  return (
    <div className="mb-3 flex flex-wrap items-baseline gap-x-3 gap-y-1">
      <h3 className="type-micro-label text-u-text3">Communication history</h3>
      {glance.facts.length > 0 && (
        <ul aria-label="At a glance" className="flex flex-wrap items-baseline gap-x-2.5 text-note text-u-text3">
          {glance.facts.map((fact) => (
            <li key={fact.noun}>
              <span className={cn("font-semibold", TONE_TEXT[fact.tone])}>{fact.count}</span> {fact.noun}
            </li>
          ))}
        </ul>
      )}
      <div className="ms-auto flex flex-wrap items-baseline gap-x-3">
        {glance.sequence && <span className="text-note text-u-signal">{glance.sequence}</span>}
        {glance.lastTouch && (
          <span className="font-mono text-eyebrow uppercase text-u-text3">Last touch {glance.lastTouch}</span>
        )}
        {range && <span className="font-mono text-eyebrow text-u-text3">{range}</span>}
      </div>
    </div>
  );
}
