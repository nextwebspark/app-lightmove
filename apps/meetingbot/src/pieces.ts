import { RunGoneError, type NotetakerApi, type PieceKind } from "./api.js";

/**
 * Uploads each captured piece in the order it was made, numbering them per kind, and retries one that failed —
 * the server replaces a piece sent twice, so a retry after a lost answer costs nothing.
 */
export class PieceUploader {
  private readonly next: Record<PieceKind, number> = { audio: 1, captions: 1 };
  private queue: Promise<void> = Promise.resolve();
  private failed = 0;
  private readonly api: NotetakerApi;
  private readonly sleep: (ms: number) => Promise<void>;
  private readonly attempts: number;

  constructor(api: NotetakerApi, sleep: (ms: number) => Promise<void>, attempts = 4) {
    this.api = api;
    this.sleep = sleep;
    this.attempts = attempts;
  }

  enqueue(kind: PieceKind, content: Uint8Array): void {
    if (content.byteLength === 0) {
      return;
    }
    const sequence = this.next[kind]++;
    this.queue = this.queue.then(() => this.uploadWithRetries(kind, sequence, content));
  }

  /** Waits for every piece queued so far; answers how many could not be delivered. */
  async drain(): Promise<number> {
    await this.queue;
    return this.failed;
  }

  private async uploadWithRetries(kind: PieceKind, sequence: number, content: Uint8Array): Promise<void> {
    for (let attempt = 1; attempt <= this.attempts; attempt++) {
      try {
        await this.api.upload(kind, sequence, content);
        return;
      } catch (error) {
        if (error instanceof RunGoneError || attempt === this.attempts) {
          this.failed++;
          console.warn(`piece ${kind} ${sequence} not delivered: ${(error as Error).message}`);
          return;
        }
        await this.sleep(1_000 * 2 ** (attempt - 1));
      }
    }
  }
}
