import { mergeExtractedPerson, type ExtractedPerson } from "./extractedPerson";
import { linkedInProfileExtractor } from "./extractors/linkedInProfileExtractor";
import { structuredPersonExtractor } from "./extractors/structuredPersonExtractor";

/**
 * Reads the person off a page, by every extractor that has something to say about them.
 *
 * Order is priority, most specific first: the merge takes the first non-empty value per field, so a
 * later extractor fills gaps and never overwrites. Adding a reader to the end of this list cannot make
 * an already-working page worse.
 */
export function readPersonFromPage(document: Document): ExtractedPerson {
  return mergeExtractedPerson([
    linkedInProfileExtractor(document),
    structuredPersonExtractor(document),
  ]);
}
