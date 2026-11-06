import type { IntegrationProvider } from "../api/types";

/** What Settings → Integrations says about each provider, as `Settings.dc.html` words it. */
export interface IntegrationProviderCopy {
  title: string;
  short: string;
  covers: string;
  /** Where the admin registers their own app. */
  console: string;
  registerHint: string;
  idLabel: string;
  secretLabel: string;
  scopesNoun: string;
  /** The same, starting a sentence: "Scopes copied". */
  scopesLabel: string;
  onceNote: string;
  sharedSetup: string;
  sharedGuide: string;
  /** Where a refused secret was most likely deleted. */
  secretHome: string;
  account: string;
  accounts: string;
  /** Its calendars sync, so while calendar sync is on Recall its keys are handed to Recall.ai. */
  syncsCalendars: boolean;
}

export const INTEGRATION_PROVIDERS: Record<IntegrationProvider, IntegrationProviderCopy> = {
  GOOGLE: {
    title: "Google Workspace",
    short: "Google",
    covers: "Gmail and Google Calendar.",
    console: "the Google Cloud console",
    registerHint:
      "Google Auth Platform → Clients → Create client, type Web application. An Internal app needs no verification from Google.",
    idLabel: "Client ID",
    secretLabel: "Client secret",
    scopesNoun: "scopes",
    scopesLabel: "Scopes",
    onceNote: "Google shows the secret once, when the client is made. Copy it before you close that dialog.",
    sharedSetup:
      "Your Google Workspace admin marks Uncava as Trusted once (Admin console → Security → API controls), so nobody is stopped by Google's unverified-app screen.",
    sharedGuide: "How to trust Uncava",
    secretHome: "Google",
    account: "mailbox",
    accounts: "mailboxes",
    syncsCalendars: true,
  },
  MICROSOFT: {
    title: "Microsoft 365",
    short: "Microsoft",
    covers: "Outlook mail and calendar, Teams meetings.",
    console: "the Microsoft Entra admin center",
    registerHint:
      "App registrations → New registration, accounts in this organisational directory only. Then add a secret under Certificates & secrets.",
    idLabel: "Application (client) ID",
    secretLabel: "Client secret value",
    scopesNoun: "API permissions",
    scopesLabel: "API permissions",
    onceNote: "Paste the Value column, not the Secret ID. Entra shows the value only once, right after you add it.",
    sharedSetup: "Send this link to your IT department. Approving it once lets every colleague connect in one click.",
    sharedGuide: "What IT sees",
    secretHome: "Entra",
    account: "mailbox",
    accounts: "mailboxes",
    syncsCalendars: true,
  },
  ZOOM: {
    title: "Zoom",
    short: "Zoom",
    covers: "Zoom links in Book a call.",
    console: "the Zoom App Marketplace",
    registerHint:
      "Develop → Build App → General app, user-managed. The keys are under Basic Information → App Credentials.",
    idLabel: "Client ID",
    secretLabel: "Client secret",
    scopesNoun: "scopes",
    scopesLabel: "Scopes",
    onceNote: "Regenerating it in Zoom stops this one at once, so save the new one here straight after.",
    sharedSetup: "Each consultant connects their own Zoom account in one click.",
    sharedGuide: "Setup guide",
    secretHome: "Zoom",
    account: "Zoom account",
    accounts: "Zoom accounts",
    syncsCalendars: false,
  },
};
