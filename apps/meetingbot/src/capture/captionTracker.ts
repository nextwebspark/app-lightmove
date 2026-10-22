import type { CaptionEntry } from "../platforms/meetScreen.js";

/** One speaker's turn as the captions told it, kept by id so a later piece's longer version replaces it. */
export interface CaptionLine {
  id: number;
  speaker: string;
  text: string;
  startedAt: string;
  updatedAt: string;
}

/**
 * Turns repeated readings of the on-screen captions into lines. A turn's text grows in place, so a reading whose
 * last block is the same speaker with text that starts as the open line did updates it; anything else opens a
 * line. Each piece carries the lines that changed since the last one.
 */
export class CaptionTracker {
  private readonly lines: CaptionLine[] = [];
  private readonly changed = new Set<number>();
  private nextId = 1;

  observe(entries: CaptionEntry[], now: Date): void {
    for (const entry of entries) {
      const text = entry.text.trim();
      if (!text) {
        continue;
      }
      const recent = this.lines.slice(-entries.length - 1).reverse()
        .find((line) => line.speaker === entry.speaker && (text.startsWith(prefixOf(line.text))
          || line.text.startsWith(prefixOf(text))));
      if (recent) {
        if (text !== recent.text) {
          recent.text = text;
          recent.updatedAt = now.toISOString();
          this.changed.add(recent.id);
        }
        continue;
      }
      const line = { id: this.nextId++, speaker: entry.speaker, text, startedAt: now.toISOString(),
        updatedAt: now.toISOString() };
      this.lines.push(line);
      this.changed.add(line.id);
    }
  }

  /** The lines changed since the last call, as a piece's JSON; empty when nothing changed. */
  takePiece(): Uint8Array {
    if (this.changed.size === 0) {
      return new Uint8Array();
    }
    const lines = this.lines.filter((line) => this.changed.has(line.id));
    this.changed.clear();
    return new TextEncoder().encode(JSON.stringify({ lines }));
  }
}

/** Captions rewrite their last few words as they settle, so lines are matched on how they began. */
function prefixOf(text: string): string {
  return text.slice(0, Math.min(24, Math.max(1, Math.floor(text.length / 2))));
}
