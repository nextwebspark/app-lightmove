import { useEffect, useRef, useState, type ChangeEvent, type KeyboardEvent } from "react";
import { TextArea } from "../../../components/ui";
import { Avatar } from "../../../components/ui/Avatar";
import { cn } from "../../../lib/cn";
import { mentionKey, mentionLabel, mentionsPresent, type Mention } from "../lib/mentions";
import type { TeammateOption } from "../lib/team";
import { MentionChip } from "./NoteBody";
import { RolePill } from "./TaskBits";

interface Picker {
  /** Where the typed "@" sits, or -1 when the toolbar button opened the list. */
  start: number;
  query: string;
}

type PickerOption = { mention: Mention; teammate: TeammateOption | null };

/**
 * A note's textarea that mentions the position's team (`Position.dc.html`, "Mention picker"): "@" typed, or
 * the toolbar's @, lists `@team` and everyone on the team but me; picking writes `@Name` and adds them to
 * Notifies. `teammates` null is a note about no position, which offers no picker at all.
 */
export function MentionTextArea({
  value,
  onChange,
  mentions,
  onMentionsChange,
  teammates,
  ariaLabel,
  placeholder,
  rows,
  maxLength,
  className,
  onKeyDown,
}: {
  value: string;
  onChange: (text: string) => void;
  mentions: readonly Mention[];
  onMentionsChange: (mentions: Mention[]) => void;
  teammates: readonly TeammateOption[] | null;
  ariaLabel: string;
  placeholder?: string;
  rows?: number;
  maxLength?: number;
  className?: string;
  onKeyDown?: (event: KeyboardEvent<HTMLTextAreaElement>) => void;
}) {
  const box = useRef<HTMLDivElement>(null);
  const [picker, setPicker] = useState<Picker | null>(null);
  const [active, setActive] = useState(0);
  const offered = teammates !== null;
  const others = (teammates ?? []).filter((teammate) => !teammate.isMe);

  const query = picker?.query.toLowerCase() ?? "";
  const options: PickerOption[] = [
    ...("team".startsWith(query) ? [{ mention: { kind: "team" } as const, teammate: null }] : []),
    ...others
      .filter((teammate) => teammate.fullName.toLowerCase().includes(query))
      .map((teammate) => ({
        mention: { kind: "user" as const, userId: teammate.userId, name: teammate.fullName },
        teammate,
      })),
  ];
  const present = mentionsPresent(value, mentions);

  useEffect(() => {
    if (!picker) return;
    const closeOnOutside = (event: MouseEvent) => {
      if (!box.current?.contains(event.target as Node)) setPicker(null);
    };
    document.addEventListener("mousedown", closeOnOutside);
    return () => document.removeEventListener("mousedown", closeOnOutside);
  }, [picker]);

  const textarea = () => box.current?.querySelector("textarea") ?? null;

  const open = (next: Picker) => {
    setPicker(next);
    setActive(0);
  };

  const handleChange = (event: ChangeEvent<HTMLTextAreaElement>) => {
    const next = event.target.value;
    const caret = event.target.selectionStart ?? next.length;
    onChange(next);
    if (!offered) return;
    const typedOne = next.length === value.length + 1;
    if (typedOne && next[caret - 1] === "@" && (caret === 1 || /\s/.test(next[caret - 2]))) {
      open({ start: caret - 1, query: "" });
      return;
    }
    if (!picker || picker.start < 0) return;
    const typed = next.slice(picker.start + 1, caret);
    if (next[picker.start] !== "@" || caret <= picker.start || /\s/.test(typed)) setPicker(null);
    else setPicker({ ...picker, query: typed });
  };

  const pick = (mention: Mention) => {
    const label = `${mentionLabel(mention)} `;
    const field = textarea();
    let next: string;
    let caret: number;
    if (picker && picker.start >= 0 && value[picker.start] === "@") {
      next = value.slice(0, picker.start) + label + value.slice(picker.start + 1 + picker.query.length);
      caret = picker.start + label.length;
    } else {
      const at = field?.selectionStart ?? value.length;
      const before = value.slice(0, at);
      const gap = before === "" || /\s$/.test(before) ? "" : " ";
      next = before + gap + label + value.slice(at);
      caret = before.length + gap.length + label.length;
    }
    onChange(next);
    if (!mentions.some((known) => mentionKey(known) === mentionKey(mention))) onMentionsChange([...mentions, mention]);
    setPicker(null);
    window.setTimeout(() => {
      field?.focus();
      field?.setSelectionRange(caret, caret);
    });
  };

  const handleKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (picker && options.length > 0) {
      if (event.key === "ArrowDown" || event.key === "ArrowUp") {
        event.preventDefault();
        const step = event.key === "ArrowDown" ? 1 : -1;
        setActive((index) => (index + step + options.length) % options.length);
        return;
      }
      if (event.key === "Enter" || event.key === "Tab") {
        event.preventDefault();
        pick(options[Math.min(active, options.length - 1)].mention);
        return;
      }
    }
    if (picker && event.key === "Escape") {
      event.preventDefault();
      event.stopPropagation();
      setPicker(null);
      return;
    }
    onKeyDown?.(event);
  };

  const avatarOf = (mention: Mention) =>
    mention.kind === "user" ? (others.find((teammate) => teammate.userId === mention.userId)?.avatarUrl ?? null) : null;

  return (
    <div ref={box} className="relative">
      <TextArea
        value={value}
        onChange={handleChange}
        onKeyDown={handleKeyDown}
        rows={rows}
        maxLength={maxLength}
        aria-label={ariaLabel}
        placeholder={placeholder}
        className={className}
      />
      {offered && (
        <div className="mt-1.5 flex items-center">
          <button
            type="button"
            aria-label="Mention a teammate"
            aria-expanded={picker !== null}
            title="Mention a teammate (type @)"
            onMouseDown={(event) => event.preventDefault()}
            onClick={() => (picker ? setPicker(null) : open({ start: -1, query: "" }))}
            className={cn(
              "grid size-6 place-items-center rounded-u-control text-body font-semibold text-u-text3 hover:text-u-text",
              picker && "bg-u-raised",
            )}
          >
            @
          </button>
        </div>
      )}
      {offered && picker && (
        <div
          onMouseDown={(event) => event.preventDefault()}
          className="absolute left-0 top-full z-20 mt-1 w-[300px] max-w-full rounded-u-chip border border-u-border-strong bg-u-surface p-1 shadow-u-e3"
        >
          <ul role="listbox" aria-label="Mention someone on this position's team" className="max-h-64 overflow-y-auto">
            {options.map((option, index) => {
              const selected = index === active;
              const rowClass = cn(
                "flex cursor-pointer items-center gap-2.5 rounded-u-control px-2 py-1.5",
                selected && "bg-u-raised",
              );
              if (option.mention.kind === "team") {
                return (
                  <li
                    key="team"
                    role="option"
                    aria-selected={selected}
                    onMouseEnter={() => setActive(index)}
                    onClick={() => pick(option.mention)}
                    className={cn(rowClass, "mb-1 border-b border-u-border")}
                  >
                    <span className="flex flex-none">
                      {others.slice(0, 2).map((teammate, stacked) => (
                        <Avatar
                          key={teammate.userId}
                          id={teammate.userId}
                          name={teammate.fullName}
                          src={teammate.avatarUrl}
                          size="xs"
                          className={cn("border-2 border-u-surface", stacked > 0 && "-ms-2")}
                        />
                      ))}
                    </span>
                    <span className="flex-1 text-body font-semibold text-u-text">@team</span>
                    <span className="text-meta text-u-text3">Everyone on this position</span>
                  </li>
                );
              }
              const teammate = option.teammate!;
              return (
                <li
                  key={teammate.userId}
                  role="option"
                  aria-selected={selected}
                  onMouseEnter={() => setActive(index)}
                  onClick={() => pick(option.mention)}
                  className={rowClass}
                >
                  <Avatar id={teammate.userId} name={teammate.fullName} src={teammate.avatarUrl} size="sm" />
                  <span className="min-w-0 flex-1 truncate text-body font-medium text-u-text">{teammate.fullName}</span>
                  <RolePill role={teammate.role} />
                </li>
              );
            })}
          </ul>
          {options.length === 0 && (
            <p className="px-2 py-1.5 text-note text-u-text3">No one on this position's team matches.</p>
          )}
          <p className="mt-1 border-t border-u-border px-2 pb-1 pt-1.5 text-meta text-u-text3">
            Only this position's team. They're told who mentioned them, never what the note says.
          </p>
        </div>
      )}
      {present.length > 0 && (
        <div className="mt-2 flex flex-wrap items-center gap-1.5 border-t border-u-border pt-2">
          <span className="text-meta text-u-text3">Notifies</span>
          {present.map((mention) => (
            <MentionChip
              key={mentionKey(mention)}
              mention={mention}
              avatarUrl={avatarOf(mention)}
              onRemove={() =>
                onMentionsChange(mentions.filter((known) => mentionKey(known) !== mentionKey(mention)))
              }
            />
          ))}
        </div>
      )}
    </div>
  );
}
