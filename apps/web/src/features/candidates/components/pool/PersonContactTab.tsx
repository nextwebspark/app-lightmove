import { Icon, ICONS } from "../../../../components/layout/Icon";
import type { PersonRecord } from "../../api/types";

/** The Contact tab: every email and phone on file, read here and edited from a position's own drawer. */
export function PersonContactTab({ person }: { person: PersonRecord }) {
  return (
    <section aria-label="Contact">
      {person.contacts.emails.length === 0 && person.contacts.phones.length === 0 ? (
        <p className="text-[13px] text-u-text3">No email or phone on file.</p>
      ) : (
        <ul className="flex flex-col gap-1.5">
          {person.contacts.emails.map((email) => (
            <ContactLine key={email.address} icon={ICONS.mail} value={email.address} kind={email.kind} verified={email.verified} />
          ))}
          {person.contacts.phones.map((phone) => (
            <ContactLine key={phone.number} icon={ICONS.phone} value={phone.number} kind={phone.kind} verified={phone.verified} />
          ))}
        </ul>
      )}
      {person.doNotContact && (
        <p className="mt-2 text-[12px] text-u-offlimits">Do not contact is set — lookups are off for this person.</p>
      )}
    </section>
  );
}

function ContactLine({
  icon,
  value,
  kind,
  verified,
}: {
  icon: string;
  value: string;
  kind: "work" | "personal" | null;
  verified: boolean;
}) {
  return (
    <li className="flex items-center gap-2 text-[13px] text-u-text2">
      <Icon d={icon} size={13} className="text-u-text3" />
      <span className="min-w-0 truncate">{value}</span>
      {kind && (
        <span
          className={`rounded-[4px] px-1.5 py-px font-mono text-[9.5px] font-semibold uppercase ${
            kind === "work" ? "bg-u-adjacent-tint text-u-adjacent" : "bg-u-accent-tint text-u-accent"
          }`}
        >
          {kind}
        </span>
      )}
      {verified && (
        <span title="Verified" className="text-u-direct">
          <Icon d={ICONS.check} size={12} />
        </span>
      )}
    </li>
  );
}
