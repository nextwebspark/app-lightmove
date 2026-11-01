import { ApiRequestError } from "../../../lib/apiClient";
import { codeOf, messageFor } from "../../../lib/errorCodes";

/** Why the last Save and verify did not save: the provider said no, or something else did. */
export type SaveFailure = { refusedWith: string | null } | { message: string };

/**
 * Sorts a failed save: the provider's refusal and any other failure are the form's to say, while a refusal the server
 * puts on a field goes there, and leaves nothing for the form.
 */
export function failureOf<TValues extends object>(
  error: unknown,
  values: TValues,
  onField: (field: keyof TValues & string, message: string) => void,
): SaveFailure | null {
  if (codeOf(error) === "INTEGRATION_KEYS_REFUSED") {
    return { refusedWith: error instanceof ApiRequestError ? (error.problem.providerError ?? null) : null };
  }
  const fieldErrors = error instanceof ApiRequestError ? error.fieldErrors : {};
  const fields = Object.keys(fieldErrors).filter((field): field is keyof TValues & string => field in values);
  if (fields.length > 0) {
    fields.forEach((field) => onField(field, fieldErrors[field]));
    return null;
  }
  return { message: messageFor(error) };
}
