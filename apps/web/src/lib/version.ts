/**
 * The release this build is.
 *
 * Stamped once, by the Dockerfile's APP_VERSION build arg, into both halves of the image — the bundle
 * through vite's `define` and the API through an env var it reports at /actuator/info. One input, so
 * the two cannot disagree. Every local build reads `dev`.
 */
export const APP_VERSION = __APP_VERSION__;
