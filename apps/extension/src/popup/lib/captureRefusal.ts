/**
 * A refusal the popup can say something specific about.
 *
 * An `Error` rather than a plain object: everything else in the extension narrows failures with
 * `instanceof Error`, and a thrown literal has no stack and no name — it surfaces as
 * `Uncaught (in promise) Object` if it ever escapes React Query.
 */
export class CaptureRefusal extends Error {
  readonly code: string;

  constructor(code: string, message: string) {
    super(message);
    this.name = "CaptureRefusal";
    this.code = code;
  }
}
