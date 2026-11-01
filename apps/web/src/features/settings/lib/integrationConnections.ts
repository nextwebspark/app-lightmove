import type { IntegrationProvider } from "../api/types";
import { INTEGRATION_PROVIDERS } from "./integrationProviders";

/** "6 mailboxes connected through this app." — Zoom connects accounts, not mailboxes. */
export function connectionsLine(provider: IntegrationProvider, count: number): string {
  return `${accountsOf(provider, count)} connected through this app.`;
}

/** "6 mailboxes are connected through your app.", the return dialog's. */
export function connectionsThroughYourApp(provider: IntegrationProvider, count: number): string {
  return `${accountsOf(provider, count)} ${count === 1 ? "is" : "are"} connected through your app.`;
}

/** "6 mailboxes were asked to connect again", once a change of app has withdrawn them. */
export function reconnectsAsked(provider: IntegrationProvider, count: number): string {
  return `${accountsOf(provider, count)} ${count === 1 ? "was" : "were"} asked to connect again`;
}

function accountsOf(provider: IntegrationProvider, count: number): string {
  const { account, accounts } = INTEGRATION_PROVIDERS[provider];
  return `${count} ${count === 1 ? account : accounts}`;
}
