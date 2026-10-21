import { GoogleMark, MicrosoftMark } from "../../../components/ui/BrandMarks";

/** The mark beside a mail host's name on a connect button; a host without one goes by its name alone. */
export function MailboxProviderMark({ provider, size }: { provider: string; size?: number }) {
  if (provider === "google") return <GoogleMark size={size} />;
  if (provider === "microsoft") return <MicrosoftMark size={size} />;
  return null;
}
