/**
 * Client-side email shape check for the plain-useState forms (the client modal and drawer) that don't
 * run through the auth module's zod schemas. The pattern mirrors the backend EmailAddressValidator's
 * SHAPE — a local part, an @, and a dotted domain — so the two agree on what "looks like an email" is;
 * the server still has the last word (deliverability, work-domain rules).
 */
const EMAIL_SHAPE = /^[^@\s]+@[^@\s.]+(\.[^@\s.]+)+$/;

export function isValidEmail(value: string): boolean {
  return EMAIL_SHAPE.test(value.trim());
}
