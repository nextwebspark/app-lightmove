import { lazy, Suspense } from "react";
import { NoteBody } from "../../tasks/components/NoteBody";
import type { NoteFormat } from "../lib/noteFormat";

const NoteRichText = lazy(() => import("./NoteRichText"));

/**
 * A note's words as they were written: a plain note as typed, line breaks kept; a formatted one through the rich
 * renderer, loaded on first need and shown plain until then.
 */
export function NoteText({ body, format, className }: { body: string; format: NoteFormat; className?: string }) {
  const plain = <NoteBody body={body} className={`whitespace-pre-wrap ${className ?? ""}`} />;
  if (format !== "markdown") return plain;
  return (
    <Suspense fallback={plain}>
      <NoteRichText body={body} className={className} />
    </Suspense>
  );
}
