import { useLayoutEffect, useRef, useState, type CSSProperties, type ReactNode } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { BriefPanel, FieldBlock } from "./BriefFields";

const CLAMPED_LINES = 2;

const CLAMP: CSSProperties = {
  display: "-webkit-box",
  WebkitBoxOrient: "vertical",
  WebkitLineClamp: CLAMPED_LINES,
  overflow: "hidden",
};

/**
 * The ideal-profile paragraph, drafted by the role template and read as prose: two lines with a Show
 * more, because a brief opens on it and the paragraph is not the field the eye should land on. It is
 * edited in place — the prose becomes a textarea and returns when it loses focus — through the same
 * autosave a keystroke anywhere else uses.
 */
export function IdealProfileField({
  value,
  marker,
  onChange,
}: {
  value: string | null;
  marker?: ReactNode;
  onChange: (narrative: string | null) => void;
}) {
  const [editing, setEditing] = useState(false);
  const [expanded, setExpanded] = useState(false);
  const [clipped, setClipped] = useState(false);
  const prose = useRef<HTMLParagraphElement>(null);
  const text = value?.trim() ?? "";

  // Whether there is more is measured from the element rather than counted in characters: how many
  // lines a paragraph takes depends on the column it is read in.
  useLayoutEffect(() => {
    const element = prose.current;
    if (!element || editing || expanded) return;
    setClipped(element.scrollHeight > element.clientHeight + 1);
  }, [text, editing, expanded]);

  return (
    <FieldBlock
      tone="inferred"
      aside={marker}
      label={
        <>
          Ideal profile — drafted from the brief <span aria-hidden="true">✦</span>
        </>
      }
    >
      {editing ? (
        <textarea
          autoFocus
          aria-label="Ideal profile"
          value={value ?? ""}
          rows={6}
          onChange={(event) => onChange(event.target.value || null)}
          onBlur={() => setEditing(false)}
          className="w-full resize-y rounded-[11px] border border-u-accent bg-u-surface px-4 py-3.5 text-[14px] leading-[1.7] text-u-text outline-none"
        />
      ) : (
        <BriefPanel className="px-4 py-3.5">
          <p
            ref={prose}
            style={expanded ? undefined : CLAMP}
            onClick={() => setEditing(true)}
            className={text ? "cursor-text text-[14px] leading-[1.7] text-u-text2" : "cursor-text text-[14px] leading-[1.7] text-u-text3"}
          >
            {text || "Describe the ideal profile — the person this mandate is looking for."}
          </p>
          <div className="mt-2 flex items-center gap-3">
            {(clipped || expanded) && (
              <button
                type="button"
                onClick={() => setExpanded((current) => !current)}
                className="text-[13px] font-semibold text-u-accent hover:underline"
              >
                {expanded ? "Show less" : "Show more"}
              </button>
            )}
            <button
              type="button"
              onClick={() => setEditing(true)}
              className="ms-auto inline-flex items-center gap-1.5 text-[12px] font-medium text-u-text3 hover:text-u-text"
            >
              <Icon d={ICONS.pencil} size={12} />
              Edit
            </button>
          </div>
        </BriefPanel>
      )}
    </FieldBlock>
  );
}
