import { cn } from "../../../lib/cn";
import { PRIVACY_URL, TERMS_URL } from "../../../lib/links";

export function LegalConsent({ className }: { className?: string }) {
  return (
    <p className={cn("text-[11.5px] leading-relaxed text-u-text3", className)}>
      By continuing you agree to the{" "}
      <a href={TERMS_URL} target="_blank" rel="noopener noreferrer" className="text-u-accent hover:underline">
        Terms
      </a>{" "}
      and{" "}
      <a href={PRIVACY_URL} target="_blank" rel="noopener noreferrer" className="text-u-accent hover:underline">
        Privacy Policy
      </a>
      .
    </p>
  );
}
