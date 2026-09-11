import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { PageHeader } from "../../../components/layout/PageHeader";
import { Button, useToast } from "../../../components/ui";
import { cn } from "../../../lib/cn";
import { messageFor } from "../../../lib/errorCodes";
import { SENIORITY_LABELS } from "../../../lib/seniority";
import { POSITION_TEMPLATES_KEY } from "../../position/api/positionApi";
import * as templateApi from "../api/templateAdminApi";
import type { TemplateOverview, TemplateScope } from "../api/types";
import { ImportTemplatesDialog } from "../components/ImportTemplatesDialog";
import { LibraryUpdatedPill, TemplateBadge } from "../components/TemplateBadge";
import { DISCIPLINE_LABELS, DISCIPLINES, SCOPE_COPY } from "../lib/labels";

const NOTES: Record<TemplateScope, string> = {
  library:
    "Changes here reach every workspace that hasn't customised the template. Workspaces with their own copy keep it and are told the library moved on. Mandates already drafted never change.",
  workspace:
    "Your firm starts from the LightMove library. Edit any template to make it your own — library updates stop reaching your copy until you reset it. Mandates already drafted never change.",
};

/** Settings → Templates or Settings → Template library: every template, grouped by discipline. */
export function TemplateListPage({ scope }: { scope: TemplateScope }) {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const toast = useToast();
  const [importing, setImporting] = useState(false);
  const { heading, path } = SCOPE_COPY[scope];

  const { data: templates, isPending, isError } = useQuery({
    queryKey: templateApi.TEMPLATE_ADMIN_KEY(scope),
    queryFn: ({ signal }) => templateApi.listTemplates(scope, signal),
  });

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: templateApi.TEMPLATE_ADMIN_KEY(scope) });
    void queryClient.invalidateQueries({ queryKey: POSITION_TEMPLATES_KEY });
  };

  const visibility = useMutation({
    mutationFn: (template: TemplateOverview) =>
      scope === "library"
        ? templateApi.setTemplateActive(template.code, !template.active)
        : templateApi.setTemplateHidden(template.code, template.origin !== "HIDDEN"),
    onSuccess: (_, template) => {
      refresh();
      toast(visibilityMessage(scope, template));
    },
    onError: (error) => toast(messageFor(error)),
  });

  const exporting = useMutation({
    mutationFn: () => templateApi.exportTemplates(scope),
    onError: (error) => toast(messageFor(error)),
  });

  const header = (
    <PageHeader
      title={heading}
      subtitle={templates ? countLine(scope, templates) : undefined}
      action={
        <>
          <Button variant="secondary" className="py-2" onClick={() => setImporting(true)}>
            Import
          </Button>
          <Button
            variant="secondary"
            className="py-2"
            loading={exporting.isPending}
            onClick={() => exporting.mutate()}
          >
            Export
          </Button>
          <Button className="py-2" onClick={() => navigate(`${path}/new`)}>
            <Icon d={ICONS.plus} size={15} />
            New template
          </Button>
        </>
      }
    />
  );

  // Before the empty state, never after it: a refused read rendered as "0 templates" states a fact
  // the caller was not allowed to learn.
  if (isError) {
    return (
      <>
        {header}
        <p role="alert" className="rounded-lg bg-red-dim px-3 py-2.5 font-mono text-xs text-red">
          The templates could not be loaded. Refresh to try again.
        </p>
      </>
    );
  }

  if (isPending) {
    return (
      <>
        {header}
        <p className="font-mono text-xs text-text3">Loading templates…</p>
      </>
    );
  }

  return (
    <>
      {header}

      <div className="mb-5 flex items-start gap-2.5 rounded-[10px] border border-line-soft bg-sky-dim px-3.5 py-3 font-mono text-xs leading-relaxed text-text2">
        <Icon d={ICONS.info} size={15} className="mt-px flex-none text-sky" />
        <span>{NOTES[scope]}</span>
      </div>

      {DISCIPLINES.map((discipline) => {
        const rows = templates.filter((template) => template.discipline === discipline);
        if (rows.length === 0) return null;
        return (
          <section key={discipline} aria-label={DISCIPLINE_LABELS[discipline]} className="mb-[18px]">
            <div className="mb-2 ms-0.5 font-mono text-[10px] font-semibold uppercase tracking-[0.14em] text-text3">
              {DISCIPLINE_LABELS[discipline]} · {rows.length}
            </div>
            <div className="rounded-[10px] border border-line-soft bg-panel2 px-5">
              {rows.map((template, index) => {
                const toggleLabel = toggleLabelOf(scope, template);
                return (
                  <div
                    key={template.code}
                    className={cn(
                      "flex items-center gap-3 py-3",
                      index > 0 && "border-t border-line-soft",
                      isDimmed(scope, template) && "opacity-55",
                    )}
                  >
                    <div className="min-w-0 flex-1">
                      <div className="flex flex-wrap items-center gap-2">
                        <span className="text-[13px] font-medium">{template.title}</span>
                        <span className="font-mono text-[11px] text-text3">
                          {SENIORITY_LABELS[template.seniority]}
                        </span>
                        <TemplateBadge scope={scope} template={template} />
                        {template.libraryChangedSinceCustomised && <LibraryUpdatedPill />}
                      </div>
                      {template.summary && (
                        <div className="mt-0.5 truncate font-mono text-[11.5px] text-text3">
                          {template.summary}
                        </div>
                      )}
                    </div>
                    {toggleLabel && (
                      <button
                        type="button"
                        aria-label={`${toggleLabel} ${template.title}`}
                        disabled={visibility.isPending}
                        onClick={() => visibility.mutate(template)}
                        className="flex-none rounded-md px-2 py-1 text-xs font-medium text-text3 transition hover:bg-panel hover:text-text disabled:opacity-50"
                      >
                        {toggleLabel}
                      </button>
                    )}
                    <Link
                      to={`${path}/${encodeURIComponent(template.code)}`}
                      aria-label={`Open ${template.title}`}
                      className="flex-none rounded-[7px] border border-line bg-panel px-3 py-1 text-[12.5px] font-medium text-text2 transition hover:border-text3 hover:text-text"
                    >
                      Open
                    </Link>
                  </div>
                );
              })}
            </div>
          </section>
        );
      })}

      <ImportTemplatesDialog
        open={importing}
        scope={scope}
        onClose={() => setImporting(false)}
        onImported={refresh}
      />
    </>
  );
}

function toggleLabelOf(scope: TemplateScope, template: TemplateOverview): string | null {
  if (template.fallback) return null;
  if (scope === "library") return template.active ? "Archive" : "Restore";
  if (template.origin === "LIBRARY") return "Hide";
  if (template.origin === "HIDDEN") return "Show";
  return null;
}

function isDimmed(scope: TemplateScope, template: TemplateOverview): boolean {
  return scope === "library" ? !template.active : template.origin === "HIDDEN";
}

function visibilityMessage(scope: TemplateScope, template: TemplateOverview): string {
  if (scope === "library") {
    return template.active ? "Archived — no workspace can pick it now" : "Restored to the library";
  }
  return template.origin === "HIDDEN" ? "Back in your firm's picker" : "Hidden from your firm's picker";
}

function countLine(scope: TemplateScope, templates: TemplateOverview[]): string {
  const total = `${templates.length} template${templates.length === 1 ? "" : "s"}`;
  if (scope === "library") {
    const archived = templates.filter((template) => !template.active).length;
    return `${total} · ${archived} archived · the library every workspace starts from`;
  }
  const count = (origin: TemplateOverview["origin"]) =>
    templates.filter((template) => template.origin === origin).length;
  return `${total} · ${count("CUSTOMISED")} customised · ${count("OWN")} your own · ${count("HIDDEN")} hidden`;
}
