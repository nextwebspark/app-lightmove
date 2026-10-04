# Use your own Microsoft app with Uncava

For IT admins who want Uncava to connect Outlook and Microsoft 365 mailboxes through an app registered in
**your own** Entra tenant, instead of Uncava's. Takes about ten minutes; you need the Application Administrator or
Global Administrator role.

1. Open the **Microsoft Entra admin center → Identity → Applications → App registrations → New registration**.
   - **Name:** `Uncava` (anything your users will recognise).
   - **Supported account types:** *Accounts in this organizational directory only (single tenant)*.
   - **Redirect URI:** platform **Web**, and paste the **Redirect URI** shown on Uncava's
     **Settings → Integrations → Microsoft 365** card once **Your own app** is chosen (it ends in
     `/api/v1/outreach/mailbox/callback`).
   - Select **Register**.
2. **API permissions → Add a permission → Microsoft Graph → Delegated permissions**, and add:
   `offline_access`, `User.Read`, `Mail.ReadWrite`, `Mail.Send`, `Calendars.ReadWrite`.
   Then select **Grant admin consent for <your organisation>**, so your users are not each asked.
3. **Certificates & secrets → Client secrets → New client secret.** Choose an expiry (24 months at most) and
   **write the date down**: when the secret expires, Uncava can no longer send from your users' mailboxes until you
   paste a new one. Copy the secret's **Value** now — it is shown once.
4. From **Overview**, copy the **Application (client) ID** and the **Directory (tenant) ID**.
5. In Uncava, **Settings → Integrations → Microsoft 365 → Your own app**, fill in **Client ID**, **Tenant ID**,
   **Client secret** and **Secret expires**, and save. The secret is stored encrypted and is never shown again.

Your consultants then connect their mailboxes from the Outreach page as usual. Mailboxes connected before the switch
need reconnecting once.

**Why these permissions:** `Mail.ReadWrite` lets Uncava write each email as a draft and send it, which is how
follow-ups thread; Uncava reads only who replied, never what they wrote. `Calendars.ReadWrite` lets it book calls and
show meetings with candidates. `offline_access` keeps the connection alive without signing in again.
