import { Icon } from "../../../components/layout/Icon";
import { NetworkMark } from "../../../components/ui/NetworkMark";
import { isBrandMark, NOTE_GLYPHS, noteKindOf } from "../lib/noteKinds";

/**
 * A note's kind as a glyph, the one drawing every screen asks for: the composer's chips, a note's card, the Activity
 * spine and Outreach's history. LinkedIn is the network's own mark, never a stroke glyph, and is drawn a step
 * narrower because the bug fills its box where a stroke glyph keeps a margin, so the two weigh the same side by side.
 */
export function NoteKindMark({
  kind,
  size,
  className,
}: {
  /** As a note carries it (`linkedin`) or as a timeline line does (`LINKEDIN`). */
  kind: string | null | undefined;
  size: number;
  className?: string;
}) {
  const known = noteKindOf(kind);
  if (isBrandMark(known)) {
    return <NetworkMark network="linkedin" size={Math.round(size * 0.85)} className={className} />;
  }
  return <Icon d={NOTE_GLYPHS[known]} size={size} className={className} />;
}
