import { pageKeyOf } from "./linkedInUrls";
import type { PageSubject } from "./readPageSubject";

/**
 * Whether a read describes the page whose address it was taken at — the fix for the panel showing the previous
 * profile: LinkedIn's `pushState` flips the address before the DOM and title, so this checks the content, not the URL.
 */
export type SettleEvidence = "settled" | "waiting" | "displaced";

/** The last read the reader is confident about, so a name repeating across pages can be recognised. */
export interface PreviousRead {
  pageKey: string;
  name: string;
  /** The tab title it was read under; absent from a read a version without it stored. */
  title?: string | null;
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
  // that was read at a different page is the previous profile still on screen — unless the tab title has
  // moved on, which LinkedIn does only once the new page has mounted. Without that, two namesakes visited
  // back to back always blanked the second's name and left Add disabled. Identical titles still blank: the
  // safe way round, the consultant types a name rather than filing someone under the wrong one.
  if (previous && previous.pageKey !== pageKey && previous.name === name && !hasTitleMovedOn(read, previous)) {
    return "displaced";
  }

  return "settled";
}

function hasTitleMovedOn(read: PageSubject, previous: PreviousRead): boolean {
  return Boolean(previous.title && read.documentTitle && previous.title !== read.documentTitle);
}

/** Whichever half of the read the page is about, or nothing when it named nobody. */
export function nameReadFrom(read: PageSubject): string | null {
  if (read.subject === "person") {
    return read.person.fullName;
  }
  return read.subject === "company" ? read.company.companyName : null;
}
