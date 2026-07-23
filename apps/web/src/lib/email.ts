/**
 * Client-side email shape check, mirroring the backend EmailAddressValidator's shape so the two agree
 * on what "looks like an email" is; the server has the last word (deliverability, work-domain rules).
 */
const EMAIL_SHAPE = /^[^@\s]+@[^@\s.]+(\.[^@\s.]+)+$/;

export function isValidEmail(value: string): boolean {
  return EMAIL_SHAPE.test(value.trim());
}
