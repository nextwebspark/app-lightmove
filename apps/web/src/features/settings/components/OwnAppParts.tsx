/** What the set-up form and the saved view of an own app say alike. */

/** "Microsoft refused these keys (AADSTS7000215). Nothing was saved." */
export function KeysRefusedAlert({
  short,
  providerError,
  consequence,
}: {
  short: string;
  providerError: string | null;
  consequence: string;
}) {
  return (
    <p role="alert" className="rounded-u-chip bg-u-offlimits-tint px-3.5 py-2.5 text-note text-u-text">
      <b className="font-semibold text-u-offlimits">
        {short} refused these keys{providerError ? ` (${providerError})` : ""}.
      </b>{" "}
      {consequence}
    </p>
  );
}

export function RecallKeysNote() {
  return (
    <p className="mt-3 font-mono text-meta text-u-text3">
      Calendar sync is on Recall, so Recall.ai holds a copy of these keys for each connected calendar. A secret saved
      here replaces every copy.
    </p>
  );
}

export function ScopeCodes({ scopes, label }: { scopes: readonly string[]; label: string }) {
  return (
    <ul aria-label={label} className="flex flex-wrap gap-1.5">
      {scopes.map((scope) => (
        <li key={scope}>
          <code className="inline-block rounded bg-u-surface px-1.5 py-0.5 font-mono text-meta text-u-text2 [overflow-wrap:anywhere]">
            {scope}
          </code>
        </li>
      ))}
    </ul>
  );
}
