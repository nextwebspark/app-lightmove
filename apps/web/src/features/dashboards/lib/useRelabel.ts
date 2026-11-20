import { useCallback } from "react";
import { useWorkspaceVocabulary } from "../../workspace/lib/vocabulary";

/**
 * The server names the workspace's units "business unit"; an agency workspace calls them clients. Its wording is
 * the SPA's to choose, so every library label passes through here.
 */
export function useRelabel(): (text: string) => string {
  const vocabulary = useWorkspaceVocabulary();
  return useCallback(
    (text: string) =>
      text
        .replaceAll("Business units", vocabulary.units)
        .replaceAll("business units", vocabulary.unitsLower)
        .replaceAll("Business unit", vocabulary.unit)
        .replaceAll("business unit", vocabulary.unitLower),
    [vocabulary],
  );
}
