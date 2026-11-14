import { useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { Button, FormError, Modal } from "../../../components/ui";
import { formatDate } from "../../../lib/datetime";
import { messageFor } from "../../../lib/errorCodes";
import { TERMS_URL } from "../../../lib/links";
import { useAuth } from "../AuthProvider";
import { LegalConsent, TERMS_REQUIRED_MESSAGE } from "./LegalConsent";

/**
 * Pages a signed-in person may open that are not the app: someone else's booking or privacy page, the public guide,
 * and the popups' landing pages. The sheet waits for the next screen of the app itself.
 */
const OUTSIDE_THE_APP = ["/book/", "/privacy/", "/docs/", "/auth/callback", "/outreach/mailbox/callback"];

/**
 * Login.dc.html `screen=updated-terms` (#928): once the terms or privacy policy change, every signed-in screen is
 * covered until the person accepts. No close; signing out is the only other way out. The server holds the gate too:
 * the tick names the version shown, and a version changed meanwhile is refused.
 */
export function TermsUpdateSheet() {
  const { user } = useAuth();
  const { pathname } = useLocation();

  if (!user?.termsUpdateRequired || OUTSIDE_THE_APP.some((prefix) => pathname.startsWith(prefix))) {
    return null;
  }
  return <TermsUpdateDialog key={user.termsVersion} version={user.termsVersion} />;
}

function TermsUpdateDialog({ version }: { version: string }) {
  const { acceptTerms, signOut } = useAuth();
  const navigate = useNavigate();
  const [ticked, setTicked] = useState(false);
  const [tickError, setTickError] = useState<string | undefined>();
  const [formError, setFormError] = useState<string | null>(null);
  const [accepting, setAccepting] = useState(false);

  const changedOn = /^\d{4}-\d{2}-\d{2}$/.test(version) ? formatDate(version) : null;

  const handleTick = (checked: boolean) => {
    setTicked(checked);
    setTickError(undefined);
  };

  const handleAccept = async () => {
    if (!ticked) {
      setTickError(TERMS_REQUIRED_MESSAGE);
      return;
    }
    setFormError(null);
    setAccepting(true);
    try {
      await acceptTerms(version);
    } catch (error) {
      setFormError(messageFor(error));
    } finally {
      setAccepting(false);
    }
  };

  const handleSignOut = async () => {
    await signOut();
    navigate("/login", { replace: true });
  };

  return (
    <Modal
      open
      onClose={() => {}}
      dismissible={false}
      title="We've updated our terms"
      footer={
        <div className="flex w-full flex-col gap-3">
          <Button type="button" loading={accepting} onClick={handleAccept} className="w-full">
            Accept and continue
          </Button>
          <button
            type="button"
            onClick={handleSignOut}
            className="text-center text-note font-medium text-u-text3 hover:text-u-text2"
          >
            Sign out
          </button>
        </div>
      }
    >
      <FormError message={formError} />
      <p className="text-body text-u-text2">
        Our Terms and Privacy Policy changed{changedOn ? ` on ${changedOn}` : ""}. Read what changed, then accept
        them to keep using Uncava.
      </p>
      <a
        href={TERMS_URL}
        target="_blank"
        rel="noopener noreferrer"
        className="my-4 inline-flex text-note font-medium text-u-accent hover:underline"
      >
        What changed
        <span className="sr-only"> (opens in a new tab)</span>
      </a>
      <LegalConsent checked={ticked} onChange={handleTick} error={tickError} className="mb-0" />
    </Modal>
  );
}
