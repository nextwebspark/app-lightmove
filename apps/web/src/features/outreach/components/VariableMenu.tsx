import { useState } from "react";
import { Popover } from "../../../components/ui/Popover";
import { cn } from "../../../lib/cn";
import type { RecipientTokens } from "../api/sequenceApi";
import { tokenNameOf, type SequenceTokenOption } from "../lib/sequenceTokens";
import { chipOf, VARIABLE_LABELS } from "../lib/variableChips";

const GROUP_OF: Record<string, "person" | "position" | "you"> = {
  firstName: "person",
  currentTitle: "person",
  currentCompany: "person",
  location: "person",
  positionTitle: "position",
  senderFirstName: "you",
  bookingLink: "you",
};

/**
 * The composer's Variable button (`Outreach.dc.html`, `variables=filled`): each variable by its name with this
 * executive's value beside it, so what goes in is seen before it does, and one with nothing on file says so. It
 * replaces the Insert bar's row of `{{…}}` chips.
 */
export function VariableMenu({
  options,
  tokens,
  forWhom,
  disabled,
  onInsert,
}: {
  options: readonly SequenceTokenOption[];
  tokens: RecipientTokens | null;
  forWhom: string;
  disabled?: boolean;
  onInsert: (token: string) => void;
}) {
  return (
    <Popover
      label="Insert a variable"
      title="Insert a variable"
      align="left"
      placement="auto"
      width={340}
      hasPopup="menu"
      disabled={disabled}
      triggerClassName="inline-flex items-center gap-1.5 rounded-u-control px-2 py-1 text-note font-medium text-u-text2 hover:bg-u-raised hover:text-u-text disabled:opacity-50"
      trigger={() => (
        <>
          <span aria-hidden className="font-mono text-note font-semibold tracking-tighter">
            {"{ }"}
          </span>
          Variable
        </>
      )}
    >
      {(close) => (
        <VariableList
          options={options}
          tokens={tokens}
          forWhom={forWhom}
          onPick={(token) => {
            close();
            onInsert(token);
          }}
        />
      )}
    </Popover>
  );
}

/** The list itself: found by typing, grouped by whose it is, each with the value it would fill. */
export function VariableList({
  options,
  tokens,
  forWhom,
  onPick,
}: {
  options: readonly SequenceTokenOption[];
  tokens: RecipientTokens | null;
  forWhom: string;
  onPick: (token: string) => void;
}) {
  const [query, setQuery] = useState("");
  const typed = query.trim().toLowerCase();
  const shown = options.filter((option) => {
    const name = tokenNameOf(option);
    return !typed || (VARIABLE_LABELS[name] ?? name).toLowerCase().includes(typed) || name.toLowerCase().includes(typed);
  });
  const groups = [
    { label: forWhom, items: shown.filter((option) => GROUP_OF[tokenNameOf(option)] === "person") },
    { label: "This position", items: shown.filter((option) => GROUP_OF[tokenNameOf(option)] === "position") },
    { label: "You", items: shown.filter((option) => GROUP_OF[tokenNameOf(option)] === "you") },
  ].filter((group) => group.items.length > 0);

  return (
    <div role="menu" aria-label="Insert a variable" className="flex flex-col">
      <input
        autoFocus
        value={query}
        onChange={(event) => setQuery(event.target.value)}
        placeholder="Find a variable"
        aria-label="Find a variable"
        className="m-1 rounded-u-control border border-u-border bg-u-raised px-2.5 py-1.5 text-note text-u-text outline-none focus:border-u-accent"
      />
      {groups.length === 0 && <p className="p-2 text-note text-u-text3">No variable matches “{query.trim()}”.</p>}
      {groups.map((group) => (
        <div key={group.label}>
          <p className="px-2 pb-1 pt-2 type-micro-label text-u-text3">{group.label}</p>
          {group.items.map((option) => {
            const name = tokenNameOf(option);
            const chip = tokens ? chipOf(name, null, tokens, forWhom) : null;
            const hasValue = chip !== null && chip.kind !== "blank";
            return (
              <button
                key={name}
                type="button"
                role="menuitem"
                title={option.tip}
                onClick={() => onPick(option.token)}
                className="flex w-full items-center gap-3 rounded-u-control px-2 py-1.5 text-start hover:bg-u-raised"
              >
                <span className="flex-none text-note font-medium text-u-text">{VARIABLE_LABELS[name] ?? name}</span>
                <span
                  className={cn(
                    "min-w-0 flex-1 truncate text-end text-note",
                    tokens && !hasValue ? "text-u-offlimits" : "text-u-text3",
                  )}
                >
                  {tokens ? (hasValue ? chip.text : "Nothing on file") : ""}
                </span>
              </button>
            );
          })}
        </div>
      ))}
    </div>
  );
}
