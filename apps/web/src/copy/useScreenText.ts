import { useCallback, useSyncExternalStore } from "react";
import type { CopyScreen } from "./defineScreen";
import { screenText, screenTextRevision, subscribeScreenText } from "./store";

/** A screen's sentences, redrawn when a fresher bundle or a preview arrives. */
export function useScreenText<Local extends string>(screen: CopyScreen<string, Local>) {
  const revision = useSyncExternalStore(subscribeScreenText, screenTextRevision);
  return useCallback(
    (local: Local, values?: Readonly<Record<string, string | number>>) => screenText(screen, local, values),
    // The revision is the dependency that matters: a new bundle must hand callers a new function.
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [screen, revision],
  );
}
