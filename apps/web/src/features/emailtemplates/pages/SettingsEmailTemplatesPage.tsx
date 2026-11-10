import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { PageHeader } from "../../../components/layout/PageHeader";
import { ActionMenu, MenuItem } from "../../../components/ui/ActionMenu";
import { ConfirmDialog } from "../../../components/ui/ConfirmDialog";
import { Avatar, Button, CardsSkeleton, EmptyState, SegmentedControl, useToast } from "../../../components/ui";
import { messageFor } from "../../../lib/errorCodes";
import { formatInstantDate } from "../../../lib/format";
import * as emailTemplatesApi from "../api/emailTemplatesApi";
import type { EmailTemplate, EmailTemplates } from "../api/emailTemplatesApi";
import { EmailTemplateEditor } from "../components/EmailTemplateEditor";

type Tab = "all" | "mine" | "shared";
type View = { kind: "list" } | { kind: "editor"; templateId: string | null };

/**
 * Settings → Email templates (`Settings.dc.html`, `email-templates`): the consultant's own templates and the ones
 * colleagues shared, a search, and the editor. Not Settings → Templates, which is the position brief library.
 */
export function SettingsEmailTemplatesPage() {
  const [view, setView] = useState<View>({ kind: "list" });
  const [deleting, setDeleting] = useState<EmailTemplate | null>(null);
  const queryClient = useQueryClient();
  const toast = useToast();
  const templates = useQuery({
    queryKey: emailTemplatesApi.EMAIL_TEMPLATES_KEY,
    queryFn: ({ signal }) => emailTemplatesApi.listEmailTemplates(signal),
  });
  const refresh = () => queryClient.invalidateQueries({ queryKey: emailTemplatesApi.EMAIL_TEMPLATES_KEY });

  const duplicate = useMutation({
    mutationFn: (template: EmailTemplate) => emailTemplatesApi.duplicateEmailTemplate(template.id),
    onSuccess: () => {
      void refresh();
      setView({ kind: "list" });
      toast("Duplicated as a personal template");
    },
    onError: (error) => toast.error(messageFor(error)),
  });
  const remove = useMutation({
    mutationFn: (template: EmailTemplate) => emailTemplatesApi.deleteEmailTemplate(template.id, template.version),
    onSuccess: (_, template) => {
      void refresh();
      setDeleting(null);
      setView({ kind: "list" });
      toast(`${template.name} deleted`);
    },
    onError: (error) => {
      setDeleting(null);
      toast.error(messageFor(error));
    },
  });

  const data = templates.data;
  const editing = view.kind === "editor" && view.templateId ? (data?.templates.find((t) => t.id === view.templateId) ?? null) : null;
  const ownCount = data?.templates.filter((template) => template.mine).length ?? 0;
  const atLimit = data ? ownCount >= data.limit : false;

  return (
    <>
      {view.kind === "editor" && (
        <button
          type="button"
          onClick={() => setView({ kind: "list" })}
          className="mb-2 text-meta font-medium text-u-text3 hover:text-u-text"
        >
          ← Email templates
        </button>
      )}
      <PageHeader
        title={view.kind === "editor" ? (editing?.name ?? "New template") : "Email templates"}
        subtitle={
          view.kind === "editor"
            ? "Variables fill in for each executive when you write the email, as in a sequence. Apply it from Templates in any email or reply."
            : "Emails and replies you send again and again. Apply one from Templates in the composer, or copy one into a sequence step."
        }
        action={
          view.kind === "list" && data && data.templates.length > 0 ? (
            <Button
              size="sm"
              className="whitespace-nowrap"
              disabled={atLimit}
              onClick={() => setView({ kind: "editor", templateId: null })}
            >
              New template
            </Button>
          ) : undefined
        }
      />

      {templates.isError ? (
        <p className="text-note text-u-text3">{messageFor(templates.error)}</p>
      ) : !data ? (
        <CardsSkeleton />
      ) : view.kind === "editor" ? (
        <EmailTemplateEditor
          key={view.templateId ?? "new"}
          template={editing}
          bookingLinkOffered={data.bookingLinkOffered}
          onDone={() => setView({ kind: "list" })}
          onDelete={setDeleting}
          onDuplicate={(template) => duplicate.mutate(template)}
        />
      ) : (
        <TemplateList
          data={data}
          ownCount={ownCount}
          atLimit={atLimit}
          onNew={() => setView({ kind: "editor", templateId: null })}
          onEdit={(template) => setView({ kind: "editor", templateId: template.id })}
          onDuplicate={(template) => duplicate.mutate(template)}
          onDelete={setDeleting}
        />
      )}

      <ConfirmDialog
        open={deleting !== null}
        title={deleting ? `Delete ${deleting.name}?` : "Delete template?"}
        confirmLabel="Delete template"
        pending={remove.isPending}
        onConfirm={() => deleting && remove.mutate(deleting)}
        onClose={() => setDeleting(null)}
      >
        {deleting?.shared
          ? `It is shared with the workspace: everyone loses it, not only you.${
              deleting.mine ? "" : ` It is ${deleting.owner.fullName ?? "a colleague"}'s.`
            } Emails already sent from it are kept.`
          : "It can't be brought back. Emails already sent from it are kept."}
      </ConfirmDialog>
    </>
  );
}

function TemplateList({
  data,
  ownCount,
  atLimit,
  onNew,
  onEdit,
  onDuplicate,
  onDelete,
}: {
  data: EmailTemplates;
  ownCount: number;
  atLimit: boolean;
  onNew: () => void;
  onEdit: (template: EmailTemplate) => void;
  onDuplicate: (template: EmailTemplate) => void;
  onDelete: (template: EmailTemplate) => void;
}) {
  const [tab, setTab] = useState<Tab>("all");
  const [query, setQuery] = useState("");
  const toast = useToast();

  if (data.templates.length === 0) {
    return (
      <EmptyState
        icon={<Icon d={ICONS.fileText} size={22} />}
        title="No email templates yet"
        body="Save the emails and replies you send again and again. Variables such as {{firstName}} fill in for each executive, as in a sequence, and a template shared with the workspace works for whoever sends it."
      >
        <Button onClick={onNew}>New template</Button>
      </EmptyState>
    );
  }

  const inTab = (template: EmailTemplate, which: Tab) =>
    which === "all" || (which === "mine" ? template.mine : template.shared && !template.mine);
  const needle = query.trim().toLowerCase();
  const rows = data.templates
    .filter((template) => inTab(template, tab))
    .filter(
      (template) =>
        !needle ||
        [template.name, template.subject ?? "", template.plainBody, template.owner.fullName ?? ""]
          .join(" ")
          .toLowerCase()
          .includes(needle),
    );
  const countOf = (which: Tab) => data.templates.filter((template) => inTab(template, which)).length;

  return (
    <>
      <div className="mb-3 flex flex-wrap items-center gap-2.5">
        <SegmentedControl
          label="Which templates"
          value={tab}
          onChange={setTab}
          options={[
            { value: "all", label: `All ${countOf("all")}` },
            { value: "mine", label: `Mine ${countOf("mine")}` },
            { value: "shared", label: `Shared ${countOf("shared")}` },
          ]}
        />
        <input
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder="Search templates"
          aria-label="Search templates"
          className="min-w-0 flex-[1_1_200px] rounded-u-control border border-u-border bg-u-surface px-2.5 py-1.5 text-body text-u-text outline-none focus:border-u-accent"
        />
        <span className="font-mono text-meta text-u-text3">
          {ownCount} of your {data.limit}
        </span>
      </div>

      <ul aria-label="Email templates" className="rounded-u-card border border-u-border bg-u-raised">
        {rows.map((template) => (
          <li
            key={template.id}
            className="flex flex-wrap items-center gap-3 border-b border-u-border px-3.5 py-3 last:border-b-0 md:flex-nowrap"
          >
            <button
              type="button"
              onClick={() =>
                template.canEdit
                  ? onEdit(template)
                  : toast(`${template.owner.fullName ?? "A colleague"}'s template: duplicate it to change a copy of your own`)
              }
              className="min-w-0 flex-[1_1_240px] text-left"
            >
              <span className="flex min-w-0 items-center gap-2">
                <span className="truncate text-body font-medium text-u-text">{template.name}</span>
                {template.shared && (
                  <span className="flex-none rounded bg-u-accent-tint px-1.5 py-0.5 font-mono text-eyebrow uppercase text-u-accent">
                    Shared
                  </span>
                )}
              </span>
              <span className="mt-0.5 block truncate text-note text-u-text3">
                {(template.subject ? `${template.subject} — ` : "") + template.plainBody.replace(/\s+/g, " ")}
              </span>
            </button>
            <span className="flex min-w-0 flex-[0_0_170px] items-center gap-2">
              <Avatar id={template.owner.userId} name={template.owner.fullName ?? "?"} src={template.owner.avatarUrl} size="sm" />
              <span className="truncate text-note text-u-text2">{template.mine ? "You" : template.owner.fullName}</span>
            </span>
            <span className="flex-[0_0_100px] font-mono text-meta text-u-text3">
              {formatInstantDate(template.updatedAt)}
            </span>
            <ActionMenu label={`Actions for ${template.name}`} width={220}>
              {(close) => (
                <>
                  {template.canEdit && (
                    <MenuItem
                      icon={ICONS.pencil}
                      onSelect={() => {
                        close();
                        onEdit(template);
                      }}
                    >
                      Edit
                    </MenuItem>
                  )}
                  <MenuItem
                    icon={ICONS.copy}
                    disabled={atLimit}
                    note={
                      atLimit
                        ? `You keep ${data.limit}, the most there can be.`
                        : template.canEdit
                          ? undefined
                          : `${template.owner.fullName ?? "A colleague"}'s. Duplicate it to make a copy of your own.`
                    }
                    onSelect={() => {
                      close();
                      onDuplicate(template);
                    }}
                  >
                    Duplicate
                  </MenuItem>
                  {template.canEdit && (
                    <MenuItem
                      icon={ICONS.trash}
                      tone="danger"
                      onSelect={() => {
                        close();
                        onDelete(template);
                      }}
                    >
                      Delete
                    </MenuItem>
                  )}
                </>
              )}
            </ActionMenu>
          </li>
        ))}
        {rows.length === 0 && (
          <li className="px-3.5 py-5 text-note text-u-text3">
            {needle ? `No template matches “${query.trim()}”.` : "None here yet."}
          </li>
        )}
      </ul>
      {atLimit && (
        <p className="mt-3 font-mono text-meta text-u-text3">
          You keep {data.limit} templates, the most there can be. Delete one to make another.
        </p>
      )}
    </>
  );
}
