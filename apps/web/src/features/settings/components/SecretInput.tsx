import { useId, useState, type InputHTMLAttributes, type Ref } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Input } from "../../../components/ui";

/**
 * A client secret's field: masked while typed, with Show beside it. Not a `Field`, whose wrapping label would name
 * the Show button too. Autofill is off: on a password field browsers offer a generated password, which would replace
 * the real secret, and password managers offer to keep the firm's secret in someone's own vault.
 */
export function SecretInput({
  label,
  error,
  hint,
  ...input
}: {
  label: string;
  error?: string;
  hint?: string;
} & Omit<InputHTMLAttributes<HTMLInputElement>, "type"> & { ref?: Ref<HTMLInputElement> }) {
  const [shown, setShown] = useState(false);
  const id = useId();
  const messageId = `${id}-message`;

  return (
    <div className="mb-4">
      <label htmlFor={id} className="mb-1.5 block type-micro-label font-mono text-u-text3">
        {label}
      </label>
      <div className="flex gap-1.5">
        <Input
          {...input}
          id={id}
          type={shown ? "text" : "password"}
          autoComplete="off"
          data-1p-ignore="true"
          data-lpignore="true"
          data-bwignore="true"
          spellCheck={false}
          aria-describedby={messageId}
          invalid={!!error}
          className="min-w-0 flex-1"
        />
        <button
          type="button"
          aria-pressed={shown}
          aria-label={shown ? "Hide the secret" : "Show the secret"}
          onClick={() => setShown((wasShown) => !wasShown)}
          className="flex flex-none cursor-pointer items-center gap-1.5 rounded-u-control border border-u-border-strong bg-u-surface px-2.5 text-note font-medium text-u-text2 transition hover:text-u-text"
        >
          <Icon d={shown ? ICONS.eyeOff : ICONS.eye} size={14} />
          {shown ? "Hide" : "Show"}
        </button>
      </div>
      <span id={messageId} aria-live="polite">
        {error ? (
          <span className="mt-1.5 block font-mono text-meta text-u-offlimits">{error}</span>
        ) : hint ? (
          <span className="mt-1.5 block font-mono text-meta text-u-text3">{hint}</span>
        ) : null}
      </span>
    </div>
  );
}
