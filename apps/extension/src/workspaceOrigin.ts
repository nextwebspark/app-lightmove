import { DEVELOPMENT_WORKSPACE_ORIGIN, PRODUCTION_WORKSPACE_ORIGIN } from "./workspaceOrigins";

/** Where this build of the extension talks to. See `workspaceOrigins.ts` for why the choice is here. */
export const workspaceOrigin = import.meta.env.PROD
  ? PRODUCTION_WORKSPACE_ORIGIN
  : DEVELOPMENT_WORKSPACE_ORIGIN;
