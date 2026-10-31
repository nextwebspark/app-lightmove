// Ported from apps/web/src/features/notes/components/NoteText.tsx; keep the two in step.
import { lazy, Suspense } from "react";
import { NoteBody } from "./NoteBody";
import type { NoteFormat } from "../lib/noteFormat";

const NoteRichText = lazy(() => import("./NoteRichText"));

/**
 * A note's words as they were written: a plain note as typed, line breaks kept; a formatted one through the rich
 * renderer, loaded on first need. Until it arrives a formatted note holds its place unseen, rather than flashing its
 * stored "**" and "\." at the reader.
 */
export function NoteText({ body, format, className }: { body: string; format: NoteFormat; className?: string }) {
  const plain = <NoteBody body={body} className={`whitespace-pre-wrap ${className ?? ""}`} />;
  if (format !== "markdown") return plain;
  return (
    <Suspense fallback={<NoteBody body={body} className={`invisible whitespace-pre-wrap ${className ?? ""}`} />}>
      <NoteRichText body={body} className={className} />
    </Suspense>
  );
}
