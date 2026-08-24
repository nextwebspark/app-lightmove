import { DEVELOPMENT_WORKSPACE_ORIGIN, PRODUCTION_WORKSPACE_ORIGIN } from "./buildTargets";

/**
 * Where this build of the extension talks to.
 *
 * Browser-side only. The manifest makes the same choice from its build mode; see `buildTargets.ts`
 * for why the two cannot be one module.
 */
export const workspaceOrigin = import.meta.env.PROD
  ? PRODUCTION_WORKSPACE_ORIGIN
  : DEVELOPMENT_WORKSPACE_ORIGIN;
