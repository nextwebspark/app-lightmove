import { CompensationSummary } from "../CompensationSummary";
import type { PersonRecord } from "../../api/types";

/** The Compensation tab: the one package on file, read here and edited from a position's own drawer. */
export function PersonCompensationTab({ person }: { person: PersonRecord }) {
  return (
    <section aria-label="Compensation">
      <CompensationSummary compensation={person.compensation} />
      <p className="mt-2 text-note text-u-text3">
        One package on file for this person, read by every position they are in.
      </p>
    </section>
  );
}
