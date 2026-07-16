const KEY = "lm_pending_invite";

/**
 * The invitation token, held across the detour an invitee has to take before it can be redeemed.
 *
 * <p>Accepting requires a signed-in, verified user, and an invitee usually has neither. So the link
 * from their inbox sends them through signup and out to their email again for the verification click —
 * and every one of those steps is a full page load that would drop the token if it lived in React
 * state. It has to survive a reload, and it must not survive the tab.
 *
 * <p>`sessionStorage`, therefore, and not `localStorage`: an invitation is not a thing to still be
 * holding a week later on a shared machine. It is cleared the moment it is redeemed or refused.
 *
 * <p>This is not a credential the way the access token is — the same token is already sitting in the
 * user's inbox, in a link they were sent. Persisting it briefly is no worse than the email itself.
 */
export interface PendingInvite {
  token: string;
  /** The address the invitation is addressed to. Signup pins its email field to this. */
  email: string;
}

export const pendingInvite = {
  remember(invite: PendingInvite): void {
    sessionStorage.setItem(KEY, JSON.stringify(invite));
  },

  peek(): PendingInvite | null {
    const raw = sessionStorage.getItem(KEY);
    if (!raw) {
      return null;
    }
    try {
      return JSON.parse(raw) as PendingInvite;
    } catch {
      // Someone hand-edited it, or an old shape is still in the tab. Neither is worth crashing over.
      sessionStorage.removeItem(KEY);
      return null;
    }
  },

  clear(): void {
    sessionStorage.removeItem(KEY);
  },
};
