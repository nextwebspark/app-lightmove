import { lazy, Suspense, useState } from "react";
import { Skeleton } from "../../../components/ui";
import type { TalentHub } from "../api/types";

// Lazy, so a report read by somebody who never scrolls to the map does not download a map library.
const HubGlobe = lazy(() => import("./HubGlobe"));

/**
 * The hub map beside the hub bars, drawn only where this deployment can draw one.
 *
 * <p>Three ways it declines, and all of them fall back to the bars alone rather than to a hole: no
 * Mapbox token configured, no hub the geocoder has placed yet, or a browser with no WebGL. The bars
 * are the chapter's actual claim; the map is how it reads faster.
 */
export function HubMapPanel({
  hubs,
  accessToken,
  selectedCity,
  onSelect,
}: {
  hubs: TalentHub[];
  accessToken: string;
  selectedCity: string | null;
  onSelect: (city: string) => void;
}) {
  const [unsupported, setUnsupported] = useState(false);
  const placed = hubs.filter((hub) => hub.point !== null);

  if (unsupported || placed.length === 0) {
    return null;
  }

  return (
    <div className="h-[280px] overflow-hidden rounded-[10px] border border-line-soft">
      <Suspense fallback={<Skeleton className="size-full" />}>
        <HubGlobe
          accessToken={accessToken}
          hubs={placed}
          selectedCity={selectedCity}
          onSelect={onSelect}
          onUnsupported={() => setUnsupported(true)}
        />
      </Suspense>
    </div>
  );
}
