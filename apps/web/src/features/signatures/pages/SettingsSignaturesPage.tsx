import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { PageHeader } from "../../../components/layout/PageHeader";
import { Button, CardsSkeleton, EmptyState, useToast } from "../../../components/ui";
import { messageFor } from "../../../lib/errorCodes";
import { useAuth } from "../../auth/AuthProvider";
import * as signaturesApi from "../api/signaturesApi";
import type { EmailSignature, EmailSignatures, SignatureLayout } from "../api/signaturesApi";
import { LayoutSketch } from "../components/LayoutSketch";
import { SignatureEditor } from "../components/SignatureEditor";
import { SignaturePreviewFrame } from "../components/SignaturePreviewFrame";
import { draftOf, duplicateOf, LAYOUT_DETAILS, newDraft, type SignatureDraft } from "../lib/signatureDraft";

type View =
  | { kind: "list" }
  | { kind: "gallery" }
  | { kind: "editor"; signatureId: string | null; draft: SignatureDraft };

/**
 * Settings → Email signature (`Settings.dc.html`, `signatures`): the consultant's own signatures, the default first,
 * a layout gallery for a new one and the editor beside the server's preview. Uncava adds the chosen one to the emails
 * it sends for them; no mailbox adds one to email sent through an app.
 */
export function SettingsSignaturesPage() {
  const [view, setView] = useState<View>({ kind: "list" });
  const signatures = useQuery({
    queryKey: signaturesApi.SIGNATURES_KEY,
    queryFn: ({ signal }) => signaturesApi.listSignatures(signal),
  });

  const title =
    view.kind === "gallery" ? "New signature" : view.kind === "editor" ? (view.signatureId ? "Edit signature" : "New signature") : "Email signature";
  const subtitle =
    view.kind === "gallery"
      ? "Pick a layout. Uncava fills it in from your profile and your workspace, and you can change anything after."
      : view.kind === "editor"
        ? "A field you leave empty follows your profile, so a change there reaches every signature."
        : "What closes every email you send from Uncava";
  const data = signatures.data;
  const atLimit = data ? data.signatures.length >= data.limit : false;

  return (
    <>
      {view.kind !== "list" && (
        <button
          type="button"
          onClick={() => setView({ kind: "list" })}
          className="mb-2 text-meta font-medium text-u-text3 hover:text-u-text"
        >
          ← Email signature
        </button>
      )}
      <PageHeader
        title={title}
        subtitle={subtitle}
        action={
          view.kind === "list" && data && data.signatures.length > 0 ? (
            <Button size="sm" disabled={atLimit} onClick={() => setView({ kind: "gallery" })}>
              New signature
            </Button>
          ) : undefined
        }
      />

      {signatures.isError ? (
        <p className="text-note text-u-text3">{messageFor(signatures.error)}</p>
      ) : !data ? (
        <CardsSkeleton />
      ) : view.kind === "gallery" ? (
        <LayoutGallery
          ownName={data.sources.name}
          onPick={(layout) =>
            setView({
              kind: "editor",
              signatureId: null,
              draft: newDraft(layout, data.signatures.length === 0, data.sources.name),
            })
          }
        />
      ) : view.kind === "editor" ? (
        <SignatureEditor
          initial={view.draft}
          signatureId={view.signatureId}
          sources={data.sources}
          onDone={() => setView({ kind: "list" })}
        />
      ) : (
        <SignatureList
          data={data}
          atLimit={atLimit}
          onNew={() => setView({ kind: "gallery" })}
          onEdit={(signature) => setView({ kind: "editor", signatureId: signature.id, draft: draftOf(signature) })}
        />
      )}
    </>
  );
}

function SignatureList({
  data,
  atLimit,
  onNew,
  onEdit,
}: {
  data: EmailSignatures;
  atLimit: boolean;
  onNew: () => void;
  onEdit: (signature: EmailSignature) => void;
}) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const refresh = () => queryClient.invalidateQueries({ queryKey: signaturesApi.SIGNATURES_KEY });

  const makeDefault = useMutation({
    mutationFn: (id: string) => signaturesApi.makeDefaultSignature(id),
    onSuccess: () => {
      void refresh();
      toast("Default signature changed");
    },
    onError: (error) => toast.error(messageFor(error)),
  });
  const duplicate = useMutation({
    mutationFn: (signature: EmailSignature) => signaturesApi.createSignature(duplicateOf(signature)),
    onSuccess: () => {
      void refresh();
      toast("Signature duplicated");
    },
    onError: (error) => toast.error(messageFor(error)),
  });
  const remove = useMutation({
    mutationFn: (signature: EmailSignature) => signaturesApi.deleteSignature(signature.id),
    onSuccess: (_, signature) => {
      void refresh();
      toast(`${signature.name} deleted`);
    },
    onError: (error) => toast.error(messageFor(error)),
  });

  if (data.signatures.length === 0) {
    return (
      <EmptyState
        icon={<Icon d={ICONS.pencil} size={22} />}
        title="No signature yet"
        body="Your emails go without one. Pick a layout and Uncava fills it in from your profile."
      >
        <Button onClick={onNew}>New signature</Button>
      </EmptyState>
    );
  }

  const ordered = [...data.signatures].sort((a, b) => Number(b.isDefault) - Number(a.isDefault));
  return (
    <>
      <p className="mb-4 rounded-u-card border border-u-border bg-u-surface px-4 py-3 font-mono text-note text-u-text2">
        Uncava adds your signature to the emails it sends for you. Your Gmail or Outlook signature isn't added —
        mailboxes don't add one to email sent through an app.
      </p>
      <ul className="grid gap-3">
        {ordered.map((signature) => (
          <li
            key={signature.id}
            className="flex flex-wrap items-start gap-4 rounded-u-card border border-u-border bg-u-raised p-4"
          >
            <SignaturePreviewFrame
              title={`${signature.name} preview`}
              html={signature.html}
              className="min-w-0 flex-[1_1_300px]"
            />
            <div className="flex flex-[0_1_220px] flex-col gap-1.5">
              <div className="flex flex-wrap items-center gap-2">
                <span className="text-body font-semibold">{signature.name}</span>
                {signature.isDefault && (
                  <span className="rounded-full bg-u-accent-tint px-2 py-0.5 font-mono text-meta text-u-accent">
                    Default
                  </span>
                )}
              </div>
              <span className="font-mono text-meta text-u-text3">{LAYOUT_DETAILS[signature.layout].label}</span>
              <div className="mt-2 flex flex-wrap gap-1.5">
                <Button variant="secondary" size="xs" onClick={() => onEdit(signature)}>
                  Edit
                </Button>
                {!signature.isDefault && (
                  <Button variant="secondary" size="xs" onClick={() => makeDefault.mutate(signature.id)}>
                    Make default
                  </Button>
                )}
                <Button variant="secondary" size="xs" disabled={atLimit} onClick={() => duplicate.mutate(signature)}>
                  Duplicate
                </Button>
                <Button
                  variant="danger"
                  size="xs"
                  aria-label={`Delete ${signature.name}`}
                  onClick={() => remove.mutate(signature)}
                >
                  Delete
                </Button>
              </div>
            </div>
          </li>
        ))}
      </ul>
      {atLimit && (
        <p className="mt-3 font-mono text-meta text-u-text3">
          You keep {data.limit} signatures, the most there can be. Delete one to make another.
        </p>
      )}
    </>
  );
}

function LayoutGallery({ ownName, onPick }: { ownName: string; onPick: (layout: SignatureLayout) => void }) {
  const { user } = useAuth();
  return (
    <ul aria-label="Layouts" className="grid grid-cols-1 gap-3 md:grid-cols-2">
      {signaturesApi.SIGNATURE_LAYOUTS.map((layout) => (
        <li key={layout}>
          <button
            type="button"
            onClick={() => onPick(layout)}
            className="flex w-full flex-col gap-2.5 rounded-u-card border border-u-border bg-u-raised p-3 text-left hover:border-u-accent"
          >
            <LayoutSketch layout={layout} name={ownName} title={user?.title ?? null} />
            <span>
              <span className="block text-body font-semibold text-u-text">{LAYOUT_DETAILS[layout].label}</span>
              <span className="mt-0.5 block font-mono text-meta text-u-text3">{LAYOUT_DETAILS[layout].hint}</span>
            </span>
          </button>
        </li>
      ))}
    </ul>
  );
}
