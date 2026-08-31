import { cleanText, httpUrlOrNull, withoutEmpty } from "../extractedCompany";
import {
  emailOrNull,
  isLinkedInProfileUrl,
  type ExtractedCareerEntry,
  type ExtractedPerson,
  type PersonExtractor,
} from "../extractedPerson";
import { hrefOf, textOf } from "./dom";

/**
 * `linkedin.com/in/*` — an executive's own account of their career, and the most fragile source there
 * is: generated class names and two live layouts, signed in and signed out. Every field is a fallback
 * chain; a broken one yields null. Extend a chain with a new fixture beside the old ones.
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
 * The experience list, most recent first, which is why the first entry is the current role. Keyed on
 * shape rather than class: the title line, the company line, and a date range recognised by looking
 * like one.
 */
function readExperience(document: Document): ExtractedCareerEntry[] {
  const section = experienceSection(document);
  if (!section) {
    return [];
  }
  const entries: ExtractedCareerEntry[] = [];
  for (const item of positionItems(section)) {
    const grouping = groupedEmployerOf(item);
    const lines = visibleLines(grouping ? firstChildBlock(item) : item);
    if (lines.length === 0) {
      continue;
    }
    const period = lines.find(isDateRange) ?? null;
    const rest = lines.filter((line) => line !== period);
    // Under a grouping the row carries the role only — the employer is on the wrapper above it, and
    // reading rest[1] there would take the *next* line of the role and file it as the company.
    const entry = grouping
      ? { title: rest[0] ?? null, company: grouping, period }
      : { title: rest[0] ?? null, company: rest[1] ?? null, period };
    if (entry.title || entry.company) {
      entries.push(entry);
    }
  }
  return entries;
}

/**
 * The rows that are a position, which is not every `<li>`: two roles at one employer nest inside an
 * outer `li` naming the company, so taking every `li` yields that wrapper as a position with the
 * fields swapped, and each real role twice.
 */
function positionItems(section: Element): Element[] {
  return [...section.querySelectorAll("li")].filter((item) => !item.querySelector("li"));
}

/** The employer named by a grouping wrapper above this row, if this row sits inside one. */
function groupedEmployerOf(item: Element): string | null {
  const wrapper = item.parentElement?.closest("li");
  if (!wrapper) {
    return null;
  }
  return visibleLines(firstChildBlock(wrapper)).find((line) => !isDateRange(line)) ?? null;
}

/**
 * The row's own text, excluding anything nested inside it — for a wrapper, the employer heading rather
 * than every role underneath.
 */
function firstChildBlock(item: Element): Element {
  const clone = item.cloneNode(true) as Element;
  clone.querySelectorAll("ul, ol").forEach((nested) => nested.remove());
  return clone;
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
