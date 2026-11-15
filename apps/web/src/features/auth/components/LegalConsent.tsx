import { ConsentCheckbox } from "../../../components/ui";
import { PRIVACY_URL, TERMS_URL } from "../../../lib/links";

/**
 * The terms tick (#928): unticked, on the password signup, the invited signup, a first provider sign-in and the
 * re-acceptance sheet alike, so the account's recorded acceptance is always something the person did.
 */
export function LegalConsent({
  checked,
  onChange,
  error,
  className,
}: {
  checked: boolean;
  onChange: (checked: boolean) => void;
  error?: string;
  className?: string;
}) {
  return (
    <ConsentCheckbox checked={checked} onChange={onChange} error={error} className={className}>
      I agree to the{" "}
      <a href={TERMS_URL} target="_blank" rel="noopener noreferrer" className="text-u-accent hover:underline">
        Terms
        <span className="sr-only"> (opens in a new tab)</span>
      </a>{" "}
      and the{" "}
      <a href={PRIVACY_URL} target="_blank" rel="noopener noreferrer" className="text-u-accent hover:underline">
        Privacy Policy
        <span className="sr-only"> (opens in a new tab)</span>
      </a>
      .
    </ConsentCheckbox>
  );
}
