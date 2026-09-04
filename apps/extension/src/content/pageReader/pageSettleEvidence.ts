import { pageKeyOf } from "./linkedInUrls";
import type { PageSubject } from "./readPageSubject";

/**
 * Whether a read describes the page whose address it was taken at.
 *
 * <b>This is the fix for the panel showing the previous profile.</b> LinkedIn navigates with
 * `pushState`, so the address bar and `document.location` flip to the new profile at the same instant
 * while the DOM and the tab title still describe the previous one. Comparing one address to another —
 * which is all the reader used to do — can never catch that, because the address is not what lags.
 * This asks a different question: is there anything about the *content* saying it belongs elsewhere?
 */
export type SettleEvidence = "settled" | "waiting" | "displaced";

/** The last read the reader is confident about, so a name repeating across pages can be recognised. */
export interface PreviousRead {
  pageKey: string;
  name: string;
}

export function settleEvidenceOf(read: PageSubject, previous: PreviousRead | null): SettleEvidence {
  const pageKey = pageKeyOf(read.pageUrl);
  if (!pageKey) {
    return "settled";
  }

  // The head still declares another profile: the router has moved the address and not yet the page.
  // Only ever evidence *against* a read — a page-supplied URL agreeing with the address bar proves
  // nothing, since any page can claim anything.
  const declaredKey = pageKeyOf(read.declaredUrl);
  if (declaredKey && declaredKey !== pageKey) {
    return "displaced";
  }

  const name = nameReadFrom(read);
  if (!name) {
    // Nothing read yet. Absence is not evidence of staleness — plenty of layouts simply render late.
    return "waiting";
  }

  // The signed-in layout declares no canonical at all, so this is what carries it there: the same name
  // that was read at a different page is the previous profile still on screen, not a coincidence.
  // (Two people of the same name across one navigation would blank the field. Rare, and the safe way
  // round — the consultant types a name rather than filing someone under the wrong one.)
  if (previous && previous.pageKey !== pageKey && previous.name === name) {
    return "displaced";
  }

  return "settled";
}

/** Whichever half of the read the page is about, or nothing when it named nobody. */
export function nameReadFrom(read: PageSubject): string | null {
  if (read.subject === "person") {
    return read.person.fullName;
  }
  return read.subject === "company" ? read.company.companyName : null;
}
