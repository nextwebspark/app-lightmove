import { ApiRequestError } from "./apiClient";
import { messageFor } from "./errorCodes";

/**
 * Splits a failed write into per-field messages (inline) and one form-level banner; a `VALIDATION_FAILED` keeps its
 * field. `serverFieldNames` maps server names to this form's, row indices stripped (`requests[7].email` →
 * `requests.email`).
 */
export function fieldErrorsFrom<Name extends string>(
  error: unknown,
  serverFieldNames: Record<string, Name>,
): { fields: Partial<Record<Name, string>>; formMessage: string | null } {
  const fields: Partial<Record<Name, string>> = {};
  const unattributable: string[] = [];

  if (error instanceof ApiRequestError) {
    for (const [serverKey, message] of Object.entries(error.fieldErrors)) {
      const name = serverFieldNames[serverKey] ?? serverFieldNames[withoutRowIndices(serverKey)];
      if (name) {
        fields[name] = message;
        continue;
      }
      unattributable.push(message);
    }
  }

  // The banner survives an attributed field beside it: one response can carry both a rejection this
  // form renders and one it does not, and dropping the second shows the user the half they can fix,
  // then refuses them again for a reason nothing ever named.
  const attributedAny = Object.keys(fields).length > 0;
  return { fields, formMessage: unattributable[0] ?? (attributedAny ? null : messageFor(error)) };
}

const withoutRowIndices = (serverKey: string) => serverKey.replace(/\[\d+\]/g, "");
