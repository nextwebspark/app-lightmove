import { SUPPORT_EMAIL } from "../../../lib/links";

/** A verification email that could not be sent, with the one way on that does not depend on email working. */
export function SendFailedNotice() {
  return (
    <div role="alert" className="mb-4 rounded-lg bg-u-offlimits-tint px-3 py-2.5 font-mono text-[11.5px] text-u-offlimits">
      We couldn&rsquo;t send the email. Try again, or{" "}
      <a href={`mailto:${SUPPORT_EMAIL}`} className="underline">
        contact Uncava support
      </a>
      .
    </div>
  );
}
