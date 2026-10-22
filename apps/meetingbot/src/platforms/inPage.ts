import type { Page } from "puppeteer-core";

/**
 * Runs one of the self-contained readers in `meetScreen.ts` in the page, with the page's own document first and
 * the given arguments after. Sent as source text, which is why those functions may use nothing from outside.
 */
export function inPage<Result>(page: Page, reader: (doc: Document, ...args: never[]) => Result,
                               ...args: unknown[]): Promise<Result> {
  const call = `(${reader.toString()})(document${args.map((arg) => `, ${JSON.stringify(arg)}`).join("")})`;
  return page.evaluate(call) as Promise<Result>;
}
