# Trust Uncava in your Google Workspace

Google Workspace can block third-party apps that ask for Gmail access. If your consultants see **"Access blocked"** or
**"This app is blocked"** when they connect Gmail, an administrator marks Uncava as trusted once.

1. Sign in to the **Google Admin console** as a super administrator.
2. Go to **Security → Access and data control → API controls → Manage Third-Party App Access**.
3. Select **Add app → OAuth App Name Or Client ID**, and search for `Uncava` (or for Uncava's client ID, which Uncava
   support can give you).
4. Select the app, choose who it applies to (your whole organisation, or the organisational units your consultants
   are in), and set access to **Trusted**.
5. Select **Finish**.

Consultants can now connect Gmail from the Outreach page. You can change or remove the app's access on the same page
at any time.

**What Uncava does with it:** sends the emails your consultants write, from their own Gmail; reads message headers
only to notice who replied, never what they wrote; books calls and shows meetings with candidates on their calendar.
