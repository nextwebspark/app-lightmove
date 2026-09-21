import type { ReactNode } from "react";
import { CountryField } from "../../../components/ui/CountryField";
import type { PositionDetails } from "../api/types";
import { FieldBlock, UnderlineField } from "./BriefFields";

/**
 * Where the role sits, in two halves: the city as free text, the country from the served vocabulary
 * every other country box in the app reads — so the brief spells a country exactly as the mandate's
 * companies and executives do, and the Strategy filter can compare them. Free text survives in both.
 *
 * A document reading only ever proposes the one free-text line (see `LOCATION_LENS` in
 * `lib/documentFill.ts`), so `marker` — the city's own provenance glyph — has nothing to pair with the
 * country half.
 */
export function LocationFields({
  city,
  country,
  marker,
  onChange,
}: {
  city: string | null;
  country: string | null;
  marker?: ReactNode;
  onChange: (patch: Pick<Partial<PositionDetails>, "locationCity" | "locationCountry">) => void;
}) {
  return (
    <FieldBlock label="Location" aside={marker}>
      <div className="grid grid-cols-1 gap-x-6 gap-y-3 sm:grid-cols-2">
        <UnderlineField
          aria-label="City"
          value={city ?? ""}
          placeholder="City"
          onChange={(event) => onChange({ locationCity: event.target.value || null })}
        />
        <CountryField
          variant="uncava"
          listId="brief-country"
          value={country ?? ""}
          placeholder="Country"
          onChange={(chosen) => onChange({ locationCountry: chosen || null })}
        />
      </div>
    </FieldBlock>
  );
}
