import { ApiRequestError } from "./apiClient";
import { messageFor } from "./errorCodes";

/**
 * Splits a failed write into the messages that belong to a field and the one that belongs to the form.
 *
 * `Field`'s `error` renders inline, next to the input the user has to change; `FormError` is the
 * banner for a failure no single input owns — a conflict, a refusal, an unreachable server. A server
 * `VALIDATION_FAILED` already names the field it rejected, so sending it to the banner throws that
 * away and leaves "One or more fields are invalid" pointing at nothing.
 *
 * `keys` maps a server field name onto this form's, because the two differ wherever the request is
 * not shaped like the form: an invite posts a list of rows, so its email arrives as
 * `requests[0].email`.
 *
 * Forms built on react-hook-form don't need this — they route the same map through `setError`.
 */
export function fieldErrorsFrom<Name extends string>(
  error: unknown,
  keys: Record<string, Name>,
): { fields: Partial<Record<Name, string>>; formMessage: string | null } {
  const fields: Partial<Record<Name, string>> = {};
  const unattributable: string[] = [];

  if (error instanceof ApiRequestError) {
    for (const [serverKey, message] of Object.entries(error.fieldErrors)) {
      const name = keys[serverKey];
      if (name) {
        fields[name] = message;
        continue;
      }
      unattributable.push(message);
    }
  }

  if (Object.keys(fields).length > 0) return { fields, formMessage: null };

  // A rejected field this form does not render still carries a message the user needs, and its own
  // words beat the generic sentence VALIDATION_FAILED's detail would supply.
  return { fields, formMessage: unattributable[0] ?? messageFor(error) };
}
