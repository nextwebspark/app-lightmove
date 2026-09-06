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
 * `serverFieldNames` maps a server field name onto this form's. Row indices are stripped before the
 * lookup, so one `requests.email` entry answers `requests[0].email` and `requests[7].email` alike and
 * a form posting many rows to one endpoint needs no entry per row.
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
