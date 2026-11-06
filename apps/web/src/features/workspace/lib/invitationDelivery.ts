/** What to say when invitations were saved but their emails were refused by the mail provider. */
export function undeliveredInvitationsMessage(addresses: readonly string[]): string {
  const who = addresses.length === 1 ? addresses[0] : `${addresses.length} people`;
  return `The invitation to ${who} is saved, but the email didn't go out. Resend it from Team.`;
}
