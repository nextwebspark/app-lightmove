import type { ReactNode } from "react";
import { CountryField } from "../../../components/ui/CountryField";
import type { PositionDetails } from "../api/types";
import { Eyebrow, UnderlineField } from "./BriefFields";

/**
 * Where the role sits, in two halves: the city as free text, the country from the served vocabulary
 * every other country box in the app reads — so the brief spells a country exactly as the mandate's
 * companies and executives do, and the Strategy filter can compare them. Free text survives in both.
 *
 * Each half carries its own provenance glyph, because each is filled on its own: a document naming
 * only "Saudi Arabia" fills the country and leaves the city for a person, and undoing one half must
 * not disturb the other.
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
