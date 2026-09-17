import { lazy, Suspense, useState } from "react";
import type { PlacedHub, TalentHub } from "../api/types";

// Lazy, so a report read by somebody who never scrolls to the map does not download a map library.
const HubGlobe = lazy(() => import("./HubGlobe"));

/**
 * The country map beside the country bars, drawn only where this deployment can draw one.
 *
 * <p>Three ways it declines, and all of them fall back to the bars alone rather than to a hole: no
 * Mapbox token configured, no country the geocoder has placed yet, or a browser with no WebGL. The
 * bars are the chapter's actual claim; the map is how it reads faster.
 */
export function HubMapPanel({
  hubs,
  accessToken,
  selectedCountry,
  onSelect,
}: {
  hubs: TalentHub[];
  accessToken: string;
  selectedCountry: string | null;
  onSelect: (country: string) => void;
}) {
  const [unsupported, setUnsupported] = useState(false);
  const placed = hubs.filter((hub): hub is PlacedHub => hub.point !== null);

  if (unsupported || placed.length === 0) {
    return null;
  }

  return (
    <div className="aspect-[100/62] overflow-hidden rounded-[10px] bg-u-sunken">
      <Suspense fallback={<div className="size-full animate-pulse bg-u-sunken" />}>
        <HubGlobe
          accessToken={accessToken}
          hubs={placed}
          selectedCountry={selectedCountry}
          onSelect={onSelect}
          onUnsupported={() => setUnsupported(true)}
        />
      </Suspense>
    </div>
  );
}
