import type { CopyScreen } from "../defineScreen";
import { ERROR_SCREENS } from "./errors";

/**
 * Every screen group a super admin can edit, in the order Settings → Screen text lists them. Imported only by the
 * editor and the catalogue contract test, never by a screen: each screen imports its own group.
 */
export const SCREEN_TEXT_CATALOG: readonly CopyScreen[] = [...ERROR_SCREENS];
