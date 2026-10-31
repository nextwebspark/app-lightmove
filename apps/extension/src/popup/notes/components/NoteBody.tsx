// Ported from apps/web/src/features/tasks/components/NoteBody.tsx; keep the two in step.
import { Fragment } from "react";
import { Avatar } from "../../components/Avatar";
import { cn } from "../../lib/cn";
import { parseNoteBody, type Mention } from "../lib/mentions";

/** A note's words with its mentions drawn as chips — never the raw token. */
export function NoteBody({ body, className }: { body: string; className?: string }) {
  return (
    <p className={className}>
      {parseNoteBody(body).map((segment, index) =>
        segment.kind === "text" ? (
          <Fragment key={index}>{segment.text}</Fragment>
        ) : (
          <MentionChip key={index} mention={segment.mention} />
        ),
      )}
    </p>
  );
}

/** One mention: the teammate's avatar and @name on the accent tint; `@team` alone for everyone. */
export function MentionChip({
  mention,
  avatarUrl,
  onRemove,
}: {
  mention: Mention;
  avatarUrl?: string | null;
  onRemove?: () => void;
}) {
  const name = mention.kind === "user" ? mention.name : "team";
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1 rounded-full bg-u-accent-tint py-px pe-2 align-middle text-note font-medium text-u-accent",
        mention.kind === "user" ? "ps-0.5" : "ps-2",
      )}
    >
      {mention.kind === "user" && <Avatar id={mention.userId} name={mention.name} src={avatarUrl} size="xs" />}
      {`@${name}`}
      {onRemove && (
        <button
          type="button"
          aria-label={`Remove @${name}`}
          onClick={onRemove}
          className="ps-0.5 opacity-70 hover:opacity-100"
        >
          ×
        </button>
      )}
    </span>
  );
}
