import { useEffect, useLayoutEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { useComboboxList } from "../../../lib/useComboboxList";
import type { PositionTemplate } from "../api/types";
import { SENIORITY_LABELS } from "../lib/labels";
import { suggestionsFor } from "../lib/roleTitleSuggestions";
import { CheckedInput } from "./CheckedInput";

/**
 * The role title: a free-text field that suggests the templates the brief can be drafted from.
 *
 * Free text is the point. A mandate is titled "Group CFO – Energy Division" as often as it is titled
 * "Chief Financial Officer", and the title is what the whole workspace calls this search — so the
 * seventeen templates are offered, never imposed. Nothing is highlighted until somebody arrows into
 * the list or hovers it, which is what keeps Enter on a typed title from committing the suggestion
 * sitting underneath it.
 *
 * Picking one does two things, and they are separate on purpose: the title becomes the template's,
 * through the ordinary details write, and the brief is redrafted from that template.
 *
 * <p>The list is portalled to the body and positioned from the input's measured rect, for
 * {@link Popover}'s reason: an `absolute` panel is clipped by the first ancestor that scrolls, and
 * this combobox sits inside the New-project modal, which does. It used to be the modal that gave way
 * — it was handed `overflow-visible` so the list could escape — and a dialog that cannot scroll spills
 * its own footer onto the backdrop once the form grows. The list gives way instead.
 */
export function RoleTitleCombobox({
  value,
  templates,
  busy,
  invalid,
  onChange,
  onPick,
}: {
  value: string;
  templates: PositionTemplate[];
  busy: boolean;
  /** Marks the typed title as rejected — a red border, and no green check claiming otherwise. */
  invalid?: boolean;
  onChange: (roleTitle: string) => void;
  onPick: (template: PositionTemplate) => void;
}) {
  const matches = suggestionsFor(templates, value);

  const list = useComboboxList({
    optionCount: matches.length,
    autoHighlightFirst: false,
    onCommit: (index) => {
      const choice = matches[index];
      if (choice) onPick(choice);
    },
  });

  const showList = list.open && matches.length > 0;

  const anchorRef = useRef<HTMLDivElement>(null);
  const [anchor, setAnchor] = useState<ListAnchor | null>(null);

  // Before paint, so the list never renders at the wrong place first. Re-measured as the field's
  // content changes, since a wrapped value moves the edge the list hangs from.
  useLayoutEffect(() => {
    if (!showList) {
      setAnchor(null);
      return;
    }
    const rect = anchorRef.current?.getBoundingClientRect();
    if (rect) setAnchor({ top: rect.bottom + LIST_GAP, left: rect.left, width: rect.width });
  }, [showList, value, matches.length]);

  const { setOpen } = list;
  useEffect(() => {
    if (!showList) return;
    // A scroll behind a fixed panel strands it, so it closes rather than drifting away from the input
    // — the same answer Popover gives, and the whole reason this list is portalled.
    const close = () => setOpen(false);
    window.addEventListener("scroll", close, true);
    window.addEventListener("resize", close);
    return () => {
      window.removeEventListener("scroll", close, true);
      window.removeEventListener("resize", close);
    };
  }, [showList, setOpen]);

  return (
    <div ref={anchorRef}>
      <CheckedInput
        role="combobox"
        invalid={invalid}
        aria-expanded={showList}
        aria-controls="role-title-suggestions"
        aria-autocomplete="list"
        aria-activedescendant={
          showList && list.active >= 0 ? `role-title-suggestions-${list.active}` : undefined
        }
        aria-busy={busy}
        autoComplete="off"
        value={value}
        placeholder="e.g. Chief Financial Officer"
        onChange={(event) => {
          onChange(event.target.value);
          list.setActive(-1);
          list.setOpen(true);
        }}
        {...list.inputHandlers}
      />

      {showList &&
        createPortal(
          <ul
            id="role-title-suggestions"
            role="listbox"
            aria-label="Role templates"
            style={anchor ?? undefined}
            // Above the modal's own z-[100]: portalled to the body, the list is the dialog's sibling
            // rather than its child, so it would otherwise paint underneath the surface it belongs to.
            // Still below the toast and the tooltip, which outrank every surface.
            className="fixed z-[105] max-h-72 overflow-auto rounded-[10px] border border-line bg-panel py-1 shadow-panel"
          >
            {matches.map((template, index) => (
              <li
                key={template.id}
                id={`role-title-suggestions-${index}`}
                role="option"
                aria-selected={index === list.active}
                onMouseDown={(event) => list.commitFromPointer(event, index)}
                onMouseEnter={() => list.setActive(index)}
                className={`flex cursor-pointer items-baseline gap-2.5 px-3 py-[7px] ${
                  index === list.active ? "bg-panel2 text-text" : "text-text2"
                }`}
              >
                <span className="truncate font-sans text-[13px] font-medium text-text">
                  {template.title}
                </span>
                <span className="min-w-0 flex-1 truncate text-right font-mono text-[10.5px] text-text3">
                  {SENIORITY_LABELS[template.seniority]}
                  {template.shared ? "" : " · yours"}
                </span>
              </li>
            ))}
          </ul>,
          document.body,
        )}
    </div>
  );
}

/** Where the list hangs, in viewport coordinates. */
interface ListAnchor {
  top: number;
  left: number;
  width: number;
}

/** Gap between the input's bottom edge and the list. */
const LIST_GAP = 4;
