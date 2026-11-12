// Ported from apps/web/src/features/candidates/components/NoteKindMark.tsx; keep the two in step.
import { Icon } from "../../components/Icon";
import { LinkedInMark } from "../../components/NetworkMark";
import { NOTE_GLYPHS, noteKindOf } from "../lib/noteKinds";

/**
 * A note's kind as a glyph: the composer's chips and a note's card. LinkedIn is the network's own mark, never a stroke
 * glyph, drawn a step narrower because the bug fills its box where a stroke glyph keeps a margin.
 */
export function NoteKindMark({ kind, size, className }: { kind: string | null | undefined; size: number; className?: string }) {
  const known = noteKindOf(kind);
  if (known === "linkedin") {
    return <LinkedInMark size={Math.round(size * 0.85)} className={className} />;
  }
  return <Icon d={NOTE_GLYPHS[known]} size={size} className={className} />;
}
