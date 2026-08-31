import { cleanText, httpUrlOrNull, withoutEmpty } from "../extractedCompany";
import {
  emailOrNull,
  isLinkedInProfileUrl,
  type ExtractedCareerEntry,
  type ExtractedPerson,
  type PersonExtractor,
} from "../extractedPerson";

/**
 * `linkedin.com/in/*` — where an executive's own account of their career is, and the most fragile
 * source there is.
 *
 * Generated class names, two layouts (signed in and signed out), and no stability guarantee, so every
 * field is a chain of increasingly generic strategies and nothing matches on an exact class. A chain
 * that stops matching yields null and the merge falls through — fewer fields, never a wrong one.
 *
 * Both fixtures in `__fixtures__` are what these were written against. When LinkedIn changes, add a
 * fixture rather than replacing one: a strategy that still works for some users must not be deleted
 * because a newer layout appeared.
 */
export const linkedInProfileExtractor: PersonExtractor = (document) => {
  if (!isLinkedInProfilePage(document)) {
    return {};
  }
  const roles = readExperience(document);
  const [current, ...previous] = roles;

  const extracted: Partial<ExtractedPerson> = {
    fullName: cleanText(
      textOf(document, 'h1[class*="top-card-layout__title"]')
        ?? textOf(document, "main h1")
        ?? textOf(document, "h1"),
    ),
    title: cleanText(
      textOf(document, 'h2[class*="top-card-layout__headline"]')
        ?? textOf(document, '[class*="text-body-medium"]'),
    ) ?? current?.title ?? null,
    employerName: current?.company ?? null,
    location: cleanText(
      textOf(document, '[class*="top-card__subline-item"]')
        ?? textOf(document, '[class*="text-body-small"][class*="inline"]'),
    ),
    tenure: current?.period ?? null,
    linkedinUrl: canonicalProfileUrl(document),
    email: emailOrNull(hrefOf(document, 'a[href^="mailto:"]')),
    career: previous,
  };

  return withoutEmpty(extracted);
};

/**
 * Keyed on the host the browser actually loaded, never on `canonical`/`og:url` — those are page-supplied,
 * so any site could otherwise declare itself a LinkedIn profile and be read by this extractor.
 */
function isLinkedInProfilePage(document: Document): boolean {
  const hostname = document.location?.hostname ?? "";
  return /(^|\.)linkedin\.com$/i.test(hostname) && (document.location?.pathname ?? "").startsWith("/in/");
}

function canonicalProfileUrl(document: Document): string | null {
  const candidates = [
    document.querySelector('link[rel="canonical"]')?.getAttribute("href"),
    document.querySelector('meta[property="og:url"]')?.getAttribute("content"),
    document.location?.href,
  ];
  for (const candidate of candidates) {
    const url = httpUrlOrNull(candidate);
    if (url && isLinkedInProfileUrl(url)) {
      return url;
    }
  }
  return null;
}

/**
 * The experience list, most recent first — which is the order LinkedIn renders it in, and the reason
 * the first entry is read as the current role.
 *
 * A role's three parts sit in three sibling elements with no stable class between them, so the shape
 * is what this keys on: a heading for the title, the next line for the company, and a date range
 * recognised by looking like one.
 */
function readExperience(document: Document): ExtractedCareerEntry[] {
  const section = experienceSection(document);
  if (!section) {
    return [];
  }
  const entries: ExtractedCareerEntry[] = [];
  for (const item of section.querySelectorAll("li")) {
    const lines = visibleLines(item);
    if (lines.length === 0) {
      continue;
    }
    const period = lines.find(isDateRange) ?? null;
    const rest = lines.filter((line) => line !== period);
    const entry = { title: rest[0] ?? null, company: rest[1] ?? null, period };
    if (entry.title || entry.company) {
      entries.push(entry);
    }
  }
  return entries;
}

function experienceSection(document: Document): Element | null {
  const byId = document.querySelector("#experience")?.closest("section");
  if (byId) {
    return byId;
  }
  for (const section of document.querySelectorAll("section")) {
    const heading = cleanText(section.querySelector("h2, h3")?.textContent)?.toLowerCase();
    if (heading?.startsWith("experience")) {
      return section;
    }
  }
  return null;
}

/**
 * The text of each leaf element, de-duplicated.
 *
 * LinkedIn renders every visible string twice — once for sighted users and once inside an
 * `aria-hidden`/visually-hidden twin — so reading `textContent` off the row yields each line doubled.
 */
function visibleLines(item: Element): string[] {
  const lines: string[] = [];
  for (const node of item.querySelectorAll("span, h3, h4, p, div")) {
    if (node.querySelector("span, h3, h4, p, div")) {
      continue;
    }
    const text = cleanText(node.textContent);
    if (text && !lines.includes(text)) {
      lines.push(text);
    }
  }
  return lines;
}

/** "Mar 2021 - Present · 4 yrs 5 mos", "2016 – 2021", "Jan 2012 - Dec 2015". */
function isDateRange(line: string): boolean {
  return /\b(19|20)\d{2}\b/.test(line) && /[-–—]|present/i.test(line);
}

function textOf(document: Document, selector: string): string | null {
  return document.querySelector(selector)?.textContent ?? null;
}

function hrefOf(document: Document, selector: string): string | null {
  return document.querySelector(selector)?.getAttribute("href") ?? null;
}
