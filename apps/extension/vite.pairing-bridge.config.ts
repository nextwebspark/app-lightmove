import { iifeBundle } from "./vite.iife";

/** The pairing content script. Declared in the manifest, so it runs itself — no footer needed. */
export default iifeBundle({
  entry: "src/content/pairingBridge.ts",
  fileName: "pairing-bridge.js",
  globalName: "lightMovePairingBridge",
});
