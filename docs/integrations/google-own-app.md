# Use your own Google app with Uncava

For Google Workspace admins who want Uncava to connect Gmail through an **Internal** app in your own Google Cloud
project. An Internal app needs no Google verification and is limited to your organisation's accounts.

1. In the **Google Cloud console**, create a project (for example `uncava-mail`) under your organisation.
2. **APIs & Services → Library:** enable the **Gmail API** and the **Google Calendar API**.
3. **Google Auth Platform → Branding / Audience:**
   - **User type: Internal.**
   - App name `Uncava`, a support email, and your domain under authorised domains.
4. **Data access → Add or remove scopes**, and add:
   - `openid`, `email`
   - `https://www.googleapis.com/auth/gmail.send`
   - `https://www.googleapis.com/auth/gmail.metadata`
   - `https://www.googleapis.com/auth/calendar.events`
   - `https://www.googleapis.com/auth/calendar.freebusy`
5. **Clients → Create client → Web application.** Under **Authorised redirect URIs**, paste the **Redirect URI**
   shown on Uncava's **Settings → Integrations → Google Workspace** card once **Your own app** is chosen (it ends in
   `/api/v1/outreach/mailbox/callback`). Create, then copy the **Client ID** and **Client secret**.
6. In Uncava, **Settings → Integrations → Google Workspace → Your own app**, fill in **Client ID** and
   **Client secret**, and save. The secret is stored
   encrypted and is never shown again.

Your consultants then connect Gmail from the Outreach page. Mailboxes connected before the switch need reconnecting
once.

**Why these scopes:** `gmail.send` sends the emails your consultants write; `gmail.metadata` reads message headers
only — who replied, never the message — so a sequence stops when someone answers. The calendar scopes book calls,
read free time and show meetings with candidates.
