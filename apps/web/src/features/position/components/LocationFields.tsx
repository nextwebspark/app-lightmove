import type { ReactNode } from "react";
import { CountryField } from "../../../components/ui/CountryField";
import type { PositionDetails } from "../api/types";
import { BRIEF_LIMITS } from "../lib/briefLimits";
import { Eyebrow, UnderlineField } from "./BriefFields";

/**
 * The role's location: city as free text, country from the shared vocabulary (so Strategy can compare). Each half
 * has its own provenance glyph, since a document may fill one and undoing it must not touch the other.
 */
export function LocationFields({
  city,
  country,
  cityMarker,
  countryMarker,
  onChange,
}: {
  city: string | null;
  country: string | null;
  cityMarker?: ReactNode;
  countryMarker?: ReactNode;
  onChange: (patch: Pick<Partial<PositionDetails>, "locationCity" | "locationCountry">) => void;
}) {
  return (
    <div className="grid grid-cols-1 gap-x-6 gap-y-3 sm:grid-cols-2">
      <div className="min-w-0">
        <Eyebrow className="mb-1.5">City</Eyebrow>
        <div className="flex items-center gap-1.5">
          <UnderlineField
            aria-label="City"
            maxLength={BRIEF_LIMITS.locationCity}
            className="min-w-0 flex-1"
            value={city ?? ""}
            placeholder="City"
            onChange={(event) => onChange({ locationCity: event.target.value || null })}
          />
          {cityMarker}
        </div>
      </div>
      <div className="min-w-0">
        <Eyebrow className="mb-1.5">Country</Eyebrow>
        <div className="flex items-center gap-1.5">
          <div className="min-w-0 flex-1">
            <CountryField
              variant="uncava"
              listId="brief-country"
              value={country ?? ""}
              placeholder="Country"
              onChange={(chosen) => onChange({ locationCountry: chosen || null })}
            />
          </div>
          {countryMarker}
        </div>
      </div>
    </div>
  );
}
