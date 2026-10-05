import { useState } from "react";
import { Button, ChoiceCardGroup, Field, FormError, Input, Modal, Select } from "../../../components/ui";
import { cn } from "../../../lib/cn";
import { messageFor } from "../../../lib/errorCodes";
import type { ApiKeyKind, ApiKeyScope, CreateApiKeyRequest } from "../api/types";
import { API_KEY_SCOPES, DEFAULT_SCOPES, EXPIRY_CHOICES, expiryDateAfter } from "../lib/apiKeys";

const KIND_OPTIONS = [
  { value: "PERSONAL", title: "Personal", body: "Yours. Reads only the positions you can open, and stops if you leave." },
  { value: "SERVICE", title: "Workspace", body: "The workspace's. Reads every position and outlives whoever made it." },
] as const;

/** Name, kind (an admin's choice alone), scopes and expiry. The kind is never offered to anyone else. */
export function CreateApiKeyModal({
  canMakeWorkspaceKeys,
  workspaceName,
  creating,
  error,
  onCreate,
  onClose,
}: {
  canMakeWorkspaceKeys: boolean;
  workspaceName: string;
  creating: boolean;
  error: unknown;
  onCreate: (request: CreateApiKeyRequest) => void;
  onClose: () => void;
}) {
  const [name, setName] = useState("");
  const [kind, setKind] = useState<ApiKeyKind>("PERSONAL");
  const [scopes, setScopes] = useState<ApiKeyScope[]>([...DEFAULT_SCOPES]);
  const [expiresInDays, setExpiresInDays] = useState(90);
  const blocked = !name.trim() || scopes.length === 0;

  const handleToggleScope = (scope: ApiKeyScope) =>
    setScopes((current) => (current.includes(scope) ? current.filter((held) => held !== scope) : [...current, scope]));

  const handleSubmit = () => {
    if (blocked) return;
    onCreate({
      name: name.trim(),
      kind: canMakeWorkspaceKeys ? kind : "PERSONAL",
      scopes: API_KEY_SCOPES.map((choice) => choice.scope).filter((scope) => scopes.includes(scope)),
      expiresInDays,
    });
  };

  return (
    <Modal
      open
      onClose={onClose}
      title="Create API key"
      className="md:w-[520px]"
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button onClick={handleSubmit} disabled={blocked} loading={creating}>
            Create key
          </Button>
        </>
      }
    >
      <form
        onSubmit={(event) => {
          event.preventDefault();
          handleSubmit();
        }}
      >
        <Field label="Name" hint="What will use it, so whoever revokes it later knows what stops.">
          <Input
            value={name}
            onChange={(event) => setName(event.target.value)}
            placeholder="e.g. Power BI dashboard"
            maxLength={80}
            autoFocus
          />
        </Field>

        {canMakeWorkspaceKeys && (
          <div className="mb-4">
            <span className="type-micro-label mb-1.5 block font-mono text-u-text3">Kind</span>
            <ChoiceCardGroup label="Kind of key" options={KIND_OPTIONS} value={kind} onChange={setKind} />
          </div>
        )}

        <div className="mb-4">
          <span className="type-micro-label mb-1.5 block font-mono text-u-text3">What it can read</span>
          <div className="divide-y divide-u-border rounded-lg border border-u-border bg-u-raised">
            {API_KEY_SCOPES.map(({ scope, note, personalData }) => {
              const held = scopes.includes(scope);
              return (
                <button
                  key={scope}
                  type="button"
                  role="checkbox"
                  aria-checked={held}
                  onClick={() => handleToggleScope(scope)}
                  className="flex w-full items-start gap-2.5 px-3 py-2.5 text-start hover:bg-u-surface"
                >
                  <span
                    className={cn(
                      "mt-px grid size-4 flex-none place-items-center rounded-[4px] border text-white",
                      held ? "border-u-accent-solid bg-u-accent-solid" : "border-u-border-strong bg-u-surface",
                    )}
                  >
                    {held && (
                      <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="3" aria-hidden="true">
                        <path d="M20 6 9 17l-5-5" />
                      </svg>
                    )}
                  </span>
                  <span className="min-w-0 flex-1">
                    <span className="flex flex-wrap items-center gap-1.5">
                      <code className="font-mono text-[12px] font-medium text-u-text">{scope}</code>
                      {personalData && (
                        <span className="rounded-full bg-u-offlimits-tint px-[7px] py-px font-mono text-[9px] font-semibold uppercase tracking-[0.06em] text-u-offlimits">
                          Personal data
                        </span>
                      )}
                    </span>
                    <span className="mt-0.5 block font-mono text-[11px]/[1.5] text-u-text3">{note}</span>
                  </span>
                </button>
              );
            })}
          </div>
        </div>

        <Field
          label="Expires"
          hint={`Stops working on ${expiryDateAfter(expiresInDays)}. Create a new key before then to keep a tool connected.`}
        >
          <Select value={expiresInDays} onChange={(event) => setExpiresInDays(Number(event.target.value))}>
            {EXPIRY_CHOICES.map((choice) => (
              <option key={choice.days} value={choice.days}>
                {choice.label}
              </option>
            ))}
          </Select>
        </Field>

        <p className="rounded-lg border border-u-border bg-u-raised px-3 py-2.5 font-mono text-[11.5px]/[1.55] text-u-text3">
          {canMakeWorkspaceKeys && kind === "SERVICE"
            ? `Read-only. This key reads every position in ${workspaceName}, including the ones you are not on.`
            : "Read-only. This key never reaches more than you can open in Uncava today; lose access to a position and so does the key."}
        </p>
        {error != null && (
          <div className="mt-3">
            <FormError message={messageFor(error)} />
          </div>
        )}
        <button type="submit" hidden />
      </form>
    </Modal>
  );
}
