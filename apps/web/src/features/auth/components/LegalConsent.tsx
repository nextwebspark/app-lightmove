import { cn } from "../../../lib/cn";
import { PRIVACY_URL, TERMS_URL } from "../../../lib/links";

export function LegalConsent({ className }: { className?: string }) {
  return (
    <p className={cn("text-meta leading-relaxed text-u-text3", className)}>
      By continuing you agree to the{" "}
      <a href={TERMS_URL} target="_blank" rel="noopener noreferrer" className="text-u-accent hover:underline">
        Terms
        <span className="sr-only"> (opens in a new tab)</span>
      </a>{" "}
      and{" "}
      <a href={PRIVACY_URL} target="_blank" rel="noopener noreferrer" className="text-u-accent hover:underline">
        Privacy Policy
        <span className="sr-only"> (opens in a new tab)</span>
      </a>
      .
    </p>
  );
}
