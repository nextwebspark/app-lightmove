import { Link } from "react-router-dom";
import { Select } from "../../../components/ui";
import type { SignatureChoice } from "../lib/useSignatureChoice";
import { SignaturePreviewFrame } from "./SignaturePreviewFrame";

/**
 * The composer's Include signature (`Outreach.dc.html`, `signature`): ticked for the sender's default, a picker where
 * they keep more than one, and a way to Settings where they keep none. Nothing is drawn until their signatures load.
 */
export function SignatureChooser({ choice, disabled = false }: { choice: SignatureChoice; disabled?: boolean }) {
  if (!choice.isLoaded) return null;
  if (choice.signatures.length === 0) {
    return (
      <span className="text-note text-u-text3">
        No signature yet ·{" "}
        <Link to="/settings/signature" className="text-u-accent hover:underline">
          Add one in Settings
        </Link>
      </span>
    );
  }
  return (
    <span className="inline-flex flex-wrap items-center gap-2">
      <label className="inline-flex cursor-pointer items-center gap-1.5 text-note text-u-text2">
        <input
          type="checkbox"
          checked={choice.included}
          disabled={disabled}
          onChange={(event) => choice.setIncluded(event.target.checked)}
          className="accent-u-accent"
        />
        Include signature
      </label>
      {choice.included && choice.signatures.length > 1 && (
        <Select
          aria-label="Signature"
          density="compact"
          value={choice.chosen?.id}
          disabled={disabled}
          onChange={(event) => choice.choose(event.target.value)}
          className="w-auto"
        >
          {choice.signatures.map((signature) => (
            <option key={signature.id} value={signature.id}>
              {signature.isDefault ? `${signature.name} (default)` : signature.name}
            </option>
          ))}
        </Select>
      )}
    </span>
  );
}

/** The chosen signature drawn greyed under the words, as the email will carry it. */
export function SignatureUnderEmail({ choice }: { choice: SignatureChoice }) {
  if (!choice.included || !choice.chosen) return null;
  return (
    <div className="mt-1.5 border-l-2 border-u-border pl-3 opacity-60">
      <SignaturePreviewFrame title="Signature" html={choice.chosen.html} className="border-none" />
    </div>
  );
}
