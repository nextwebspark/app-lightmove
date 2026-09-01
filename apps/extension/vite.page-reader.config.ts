import { iifeBundle } from "./vite.iife";

/**
 * The page reader, injected on demand by the service worker rather than declared in the manifest —
 * which is what lets the extension read a page without holding a standing permission on every site.
 *
 * The footer is how its answer gets back: it calls the reader and leaves the result as the script's
 * completion value, which Chrome hands to the worker as `InjectionResult.result`.
 */
export default iifeBundle({
  entry: "src/content/pageReader/readPageSubject.ts",
  fileName: "page-reader.js",
  globalName: "lightMovePageReader",
  footer: "lightMovePageReader.readPageSubject(document);",
});
