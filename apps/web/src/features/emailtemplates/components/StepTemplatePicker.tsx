import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { Link } from "react-router-dom";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Avatar } from "../../../components/ui";
import { messageFor } from "../../../lib/errorCodes";
import type { SequenceStep } from "../../outreach/api/sequenceApi";
import * as emailTemplatesApi from "../api/emailTemplatesApi";
import type { EmailTemplate } from "../api/emailTemplatesApi";
import { stepRefusalOf } from "../lib/stepTemplates";

/**
 * From template on a sequence step (`Outreach.dc.html?page=sequence`): a search over the caller's templates and the
 * shared ones, picked to be copied into the step. The copy is the editor's to make, and to ask about first.
 */
export function StepTemplatePicker({
  step,
  isFirstStep,
  onPick,
  onClose,
}: {
  step: SequenceStep;
  isFirstStep: boolean;
  onPick: (template: EmailTemplate) => void;
  onClose: () => void;
}) {
  const [query, setQuery] = useState("");
  const templates = useQuery({
    queryKey: emailTemplatesApi.EMAIL_TEMPLATES_KEY,
    queryFn: ({ signal }) => emailTemplatesApi.listEmailTemplates(signal),
  });
  const needle = query.trim().toLowerCase();
  const shown = [...(templates.data?.templates ?? [])]
    .sort((a, b) => Number(b.mine) - Number(a.mine))
    .filter(
      (template) =>
        !needle ||
        [template.name, template.subject ?? "", template.plainBody, template.owner.fullName ?? ""]
          .join(" ")
          .toLowerCase()
          .includes(needle),
    );

  return (
    <div
      role="dialog"
      aria-label="From template"
      onClick={(event) => event.stopPropagation()}
      onKeyDown={(event) => event.key === "Escape" && onClose()}
      className="mb-2.5 rounded-u-card border border-u-border bg-u-raised p-2.5"
    >
      <div className="mb-1.5 flex flex-wrap items-center gap-2.5">
        <input
          autoFocus
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="Search templates"
          aria-label="Search templates"
          className="w-full rounded-u-control border border-u-border bg-u-surface px-2 py-1.5 text-note text-u-text outline-none focus:border-u-accent sm:w-56"
        />
        <span className="min-w-0 flex-1 text-meta text-u-text3">
          Sequence emails are plain text: bold, lists and links come in as plain words.
        </span>
        <button
          type="button"
          aria-label="Close"
          onClick={onClose}
          className="rounded-u-control p-1 text-u-text3 hover:bg-u-surface hover:text-u-text"
        >
          <Icon d={ICONS.close} size={13} />
        </button>
      </div>
      <div className="max-h-64 overflow-y-auto">
        {templates.isError ? (
          <p className="p-2 text-note text-u-text3">{messageFor(templates.error)}</p>
        ) : !templates.data ? (
          <p className="p-2 text-note text-u-text3" aria-busy="true">
            Reading your templates…
          </p>
        ) : shown.length === 0 ? (
          <p className="p-2 text-note text-u-text3">
            {needle ? (
              `No template matches “${query.trim()}”.`
            ) : (
              <>
                No templates yet.{" "}
                <Link to="/settings/email-templates" className="text-u-accent hover:underline">
                  Write one in Settings
                </Link>
                , or save a step as one.
              </>
            )}
          </p>
        ) : (
          <ul aria-label="Email templates">
            {shown.map((template) => {
              const refusal = stepRefusalOf(template, step);
              return (
                <li key={template.id}>
                  <button
                    type="button"
                    disabled={refusal !== null}
                    onClick={() => onPick(template)}
                    className="flex w-full items-center gap-2.5 rounded-u-control px-2 py-1.5 text-left enabled:hover:bg-u-surface disabled:cursor-not-allowed"
                  >
                    <span className="min-w-0 flex-1">
                      <span className="block truncate text-note font-medium text-u-text">{template.name}</span>
                      <span className={refusal ? "block text-meta text-u-offlimits" : "block truncate text-meta text-u-text3"}>
                        {refusal ??
                          `${template.mine ? "Yours" : `Shared by ${template.owner.fullName ?? "a colleague"}`} · ${template.plainBody.replace(/\s+/g, " ")}`}
                      </span>
                    </span>
                    {!template.mine && (
                      <span aria-hidden="true" className="flex-none">
                        <Avatar
                          id={template.owner.userId}
                          name={template.owner.fullName ?? "?"}
                          src={template.owner.avatarUrl}
                          size="sm"
                        />
                      </span>
                    )}
                  </button>
                </li>
              );
            })}
          </ul>
        )}
      </div>
      <p className="mt-1.5 border-t border-u-border pt-1.5 text-meta text-u-text3">
        {isFirstStep
          ? "The template's subject comes into the first email too. A copy: editing the template later changes no step."
          : "Follow-ups reply in the thread, so a template's subject is left out. A copy: editing the template later changes no step."}
      </p>
    </div>
  );
}
