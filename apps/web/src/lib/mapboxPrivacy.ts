import type { MapOptions } from "mapbox-gl";

/** Spread into every `mapboxgl.Map`: Mapbox otherwise reports each viewer's device and timings to events.mapbox.com (#929). */
export const MAPBOX_PRIVACY_OPTIONS = {
  performanceMetricsCollection: false,
} as const satisfies Partial<MapOptions>;
