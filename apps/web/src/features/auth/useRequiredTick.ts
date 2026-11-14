import { useState } from "react";
import { TERMS_REQUIRED_MESSAGE } from "../../lib/errorCodes";

/** The terms box outside a form: `consent` spreads onto `LegalConsent`, and `confirm` says why when it is unticked. */
export function useRequiredTick() {
  const [checked, setChecked] = useState(false);
  const [error, setError] = useState<string | undefined>();

  const onChange = (next: boolean) => {
    setChecked(next);
    setError(undefined);
  };

  const confirm = () => {
    if (!checked) setError(TERMS_REQUIRED_MESSAGE);
    return checked;
  };

  return { consent: { checked, onChange, error }, confirm };
}
