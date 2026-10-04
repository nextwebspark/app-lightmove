import { ContactChannels } from "../../../contactlookup/components/ContactPanel";
import type { PersonRecord } from "../../api/types";

/** The Contact tab: every email and phone on file, read here and edited from a position's own drawer. */
export function PersonContactTab({ person }: { person: PersonRecord }) {
  return (
    <section aria-label="Contact">
      <ContactChannels contacts={person.contacts} linkedinUrl={person.linkedinUrl} />
      {person.doNotContact && (
        <p className="mt-2 text-note text-u-offlimits">Do not contact is set — lookups are off for this person.</p>
      )}
    </section>
  );
}
