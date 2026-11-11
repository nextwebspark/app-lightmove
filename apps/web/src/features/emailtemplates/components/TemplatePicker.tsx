import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { Link } from "react-router-dom";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Avatar, Button } from "../../../components/ui";
import { Popover } from "../../../components/ui/Popover";
import { cn } from "../../../lib/cn";
import { messageFor } from "../../../lib/errorCodes";
import type { RecipientTokens } from "../../outreach/api/sequenceApi";
import { BOOKING_LINK_PLACEHOLDER, renderParts } from "../../outreach/lib/sequenceTokens";
import { FilledVariable } from "../../outreach/components/FilledVariable";
import * as emailTemplatesApi from "../api/emailTemplatesApi";
import type { EmailTemplate } from "../api/emailTemplatesApi";

interface TemplateGroup {
  label: string;
  templates: EmailTemplate[];
}

/**
 * The composer's Templates (`Outreach.dc.html`): a search, Recently used, Mine and Shared, and the highlighted template
 * read with this executive's values, as the server will fill them. Applying one is the composer's to decide.
 */
export function TemplatePicker({
  tokens,
  disabled,
  canSaveCurrent,
  onApply,
  onSaveCurrent,
}: {
  tokens: RecipientTokens | null;
  disabled?: boolean;
  /** Whether the composer holds anything worth saving. */
  canSaveCurrent: boolean;
  onApply: (template: EmailTemplate) => void;
  onSaveCurrent: () => void;
}) {
  return (
    <Popover
      label="Templates"
      title="Templates"
      align="left"
      placement="auto"
      width={560}
      hasPopup="dialog"
      disabled={disabled}
      triggerClassName="inline-flex items-center gap-1.5 rounded-u-control px-2 py-1 text-note font-medium text-u-text2 hover:bg-u-raised hover:text-u-text disabled:opacity-50"
      trigger={() => (
        <>
          <Icon d={ICONS.fileText} size={13} />
          Templates
        </>
      )}
    >
      {(close) => (
        <TemplatePickerPanel
          tokens={tokens}
          canSaveCurrent={canSaveCurrent}
          onApply={(template) => {
            close();
            onApply(template);
          }}
          onSaveCurrent={() => {
            close();
            onSaveCurrent();
          }}
        />
      )}
    </Popover>
  );
}

function TemplatePickerPanel({
  tokens,
  canSaveCurrent,
  onApply,
  onSaveCurrent,
}: {
  tokens: RecipientTokens | null;
  canSaveCurrent: boolean;
  onApply: (template: EmailTemplate) => void;
  onSaveCurrent: () => void;
}) {
  const [query, setQuery] = useState("");
  const [highlightedId, setHighlightedId] = useState<string | null>(null);
  const templates = useQuery({
    queryKey: emailTemplatesApi.EMAIL_TEMPLATES_KEY,
    queryFn: ({ signal }) => emailTemplatesApi.listEmailTemplates(signal),
  });

  const groups = groupsOf(templates.data, query);
  const shown = groups.flatMap((group) => group.templates);
  const highlighted = shown.find((template) => template.id === highlightedId) ?? shown[0] ?? null;

  return (
    <div className="flex flex-col">
      <div className="border-b border-u-border p-2.5">
        <input
          autoFocus
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="Search templates"
          aria-label="Search templates"
          className="w-full rounded-u-control border border-u-border bg-u-surface px-2.5 py-1.5 text-note text-u-text outline-none focus:border-u-accent"
        />
      </div>
      {/* A fixed height: the popover sits above its button, so a preview that grew or shrank as the pointer moved would
          slide the list under it between press and release, and the click would land on nothing. */}
      <div className="flex h-72 min-h-0 flex-col md:flex-row">
        <div className="min-h-0 min-w-0 flex-1 overflow-y-auto p-1.5 md:w-56 md:flex-none md:border-e md:border-u-border">
          {templates.isError ? (
            <p className="p-2 text-note text-u-text3">{messageFor(templates.error)}</p>
          ) : !templates.data ? (
            <p className="p-2 text-note text-u-text3" aria-busy="true">
              Reading your templates…
            </p>
          ) : shown.length === 0 ? (
            <p className="p-2 text-note text-u-text3">
              {query.trim() ? `No template matches “${query.trim()}”.` : "No templates yet. Save this email as one."}
            </p>
          ) : (
            groups.map((group) => (
              <div key={group.label} role="group" aria-label={group.label} className="mb-1.5">
                <div className="px-2 pb-1 pt-1.5 type-micro-label font-mono text-u-text3">{group.label}</div>
                {group.templates.map((template) => (
                  <button
                    key={`${group.label}-${template.id}`}
                    type="button"
                    onMouseEnter={() => setHighlightedId(template.id)}
                    onFocus={() => setHighlightedId(template.id)}
                    onClick={() => onApply(template)}
                    className={cn(
                      "flex w-full items-center gap-2 rounded-u-control px-2 py-1.5 text-left text-note",
                      highlighted?.id === template.id ? "bg-u-raised text-u-text" : "text-u-text2",
                    )}
                  >
                    {!template.mine && (
                      <span aria-hidden="true" className="flex-none">
                        <Avatar
                          id={template.owner.userId}
                          name={template.owner.fullName ?? "?"}
                          src={template.owner.avatarUrl}
                          size="sm"
                          title={template.owner.fullName ?? undefined}
                        />
                      </span>
                    )}
                    <span className="min-w-0 flex-1 truncate">{template.name}</span>
                  </button>
                ))}
              </div>
            ))
          )}
        </div>
        <div className="hidden min-h-0 min-w-0 flex-1 overflow-y-auto p-3 md:block" aria-label="Preview">
          {highlighted ? (
            <TemplatePreview template={highlighted} tokens={tokens} />
          ) : (
            <p className="text-note text-u-text3">Pick a template to read it with this executive's details.</p>
          )}
        </div>
      </div>
      <div className="flex flex-wrap items-center gap-2 border-t border-u-border p-2.5">
        <Button variant="secondary" size="xs" disabled={!canSaveCurrent} onClick={onSaveCurrent}>
          Save as template
        </Button>
        <Link to="/settings/email-templates" className="ms-auto text-meta text-u-accent hover:underline">
          Manage templates
        </Link>
      </div>
    </div>
  );
}

function TemplatePreview({ template, tokens }: { template: EmailTemplate; tokens: RecipientTokens | null }) {
  const values = tokens ? { ...tokens, bookingLink: tokens.bookingLink ?? BOOKING_LINK_PLACEHOLDER } : null;
  return (
    <div className="flex flex-col gap-1.5">
      {template.subject && (
        <p className="text-note font-semibold text-u-text">
          {values ? <FilledText text={template.subject.replace(/\s+/g, " ")} values={values} /> : template.subject}
        </p>
      )}
      <p className="whitespace-pre-wrap text-note text-u-text2">
        {values ? <FilledText text={template.plainBody} values={values} /> : template.plainBody}
      </p>
      <p className="font-mono text-meta text-u-text3">
        {template.mine ? "Yours" : `${template.owner.fullName ?? "A colleague"}'s, shared`} · applied with its
        formatting; a reply keeps its own subject
      </p>
    </div>
  );
}

/** The text as the server would fill it for this executive, a variable with nothing on file shown where it falls. */
function FilledText({ text, values }: { text: string; values: RecipientTokens }) {
  return (
    <>
      {renderParts(text, values, null).map((part, index) => (
        <FilledVariable key={index} part={part} forWhom={values.firstName} />
      ))}
    </>
  );
}

/** Recently used first, then Mine and Shared, each narrowed by the search. */
function groupsOf(data: emailTemplatesApi.EmailTemplates | undefined, query: string): TemplateGroup[] {
  if (!data) return [];
  const needle = query.trim().toLowerCase();
  const matches = (template: EmailTemplate) =>
    !needle ||
    [template.name, template.subject ?? "", template.plainBody, template.owner.fullName ?? ""]
      .join(" ")
      .toLowerCase()
      .includes(needle);
  const byId = new Map(data.templates.map((template) => [template.id, template]));
  const recent = data.recentlyUsed.flatMap((id) => (byId.has(id) ? [byId.get(id)!] : [])).filter(matches);
  return [
    { label: "Recently used", templates: recent },
    { label: "Mine", templates: data.templates.filter((template) => template.mine).filter(matches) },
    { label: "Shared", templates: data.templates.filter((template) => !template.mine).filter(matches) },
  ].filter((group) => group.templates.length > 0);
}
