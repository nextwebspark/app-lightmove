import { Icon, ICONS } from "../../../components/layout/Icon";

/**
 * The privacy footer every outreach email ends with (#924, `Outreach.dc.html`'s review footer), drawn as the executive
 * receives it. The links are drawn, never followed: each email gets a stop link of its own when it is sent.
 */
export function PrivacyFooterPreview({ statement }: { statement: string }) {
  return (
    <div
      aria-label="Privacy footer"
      className="mt-3.5 border-t border-u-border pt-2.5 text-meta text-u-text3"
    >
      {statement} <span className="text-u-accent underline">Our privacy notice</span> ·{" "}
      <span className="text-u-accent underline">Stop emails from us</span>
      <div className="mt-1 flex items-center gap-1.5 font-mono text-meta text-u-text3">
        <Icon d={ICONS.lock} size={11} />
        Added to every email. It can&apos;t be removed.
      </div>
    </div>
  );
}

/** The composer's note that the footer is added at send (`Outreach.dc.html`'s composer footer note). */
export function PrivacyFooterNote() {
  return (
    <div className="flex items-center gap-1.5 font-mono text-meta text-u-text3">
      <Icon d={ICONS.lock} size={11} />
      Your firm&apos;s privacy footer and a link to stop emails are added when it&apos;s sent.
    </div>
  );
}
