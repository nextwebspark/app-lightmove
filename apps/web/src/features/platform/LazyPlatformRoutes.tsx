import { lazy } from "react";
import { PlatformUnavailable } from "./PlatformUnavailable";

// A chunk that fails to load (a deploy replaced it, the network dropped) says so: nothing above this route catches
// a rejected import, so without the fallback a tab left open across a deploy blanks the whole app.
export const LazyPlatformRoutes = lazy(() =>
  import("./PlatformRoutes").catch(() => ({ default: PlatformUnavailable })),
);
