# Use your own Zoom app with Uncava

For Zoom account admins who want Book a call's Zoom links made through an app in your own Zoom account. The app stays
unpublished: only users in your account can install it, and Zoom does not review it.

1. Sign in to the **Zoom App Marketplace → Develop → Build App → General App**.
2. Choose **User-managed**.
3. **Basic information → OAuth information:**
   - **Redirect URL for OAuth:** paste the **Redirect URI** shown on Uncava's **Settings → Integrations → Zoom** card
     once **Your own app** is chosen (it ends in `/api/v1/outreach/zoom/callback`).
   - Add the same URL to the **OAuth allow list**.
4. **Scopes → Add scopes:** `meeting:write:meeting`, `meeting:update:meeting`, `meeting:delete:meeting`,
   `user:read:user`, `user:read:token`.
5. Copy the **Client ID** and **Client secret** from **App credentials** (Production).
6. In Uncava, **Settings → Integrations → Zoom → Your own app**, fill in **Client ID** and **Client secret**, and
   save.

Each consultant then selects **Connect Zoom** on the Outreach page or in a candidate's Meetings section. Book a call
offers a Zoom link once they have.

**Why these scopes:** the meeting scopes create the Zoom meeting a booked call carries and delete it again if the
invite cannot be sent. `user:read:user` tells Uncava which Zoom account was connected. `user:read:token` is asked now
for the AI note taker, so nobody has to reconnect Zoom when it arrives.
