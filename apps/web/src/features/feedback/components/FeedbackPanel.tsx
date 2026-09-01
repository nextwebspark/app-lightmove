import { zodResolver } from "@hookform/resolvers/zod";
import { useEffect, useMemo, useRef, useState, type RefObject } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button, Field, FormError, Input, Select, TextArea } from "../../../components/ui";
import { Drawer, DrawerCloseButton } from "../../../components/ui/Drawer";
import { ApiRequestError } from "../../../lib/apiClient";
import { cn } from "../../../lib/cn";
import { useAuth } from "../../auth/AuthProvider";
import { submitFeedback } from "../api/feedbackApi";
import type { FeedbackContext, FeedbackKind, FeedbackResponse } from "../api/types";

/** Mirrors the server's rules so a tester is answered instantly rather than after a round-trip. */
const reportSchema = z.object({
  kind: z.enum(["BUG", "FEATURE_REQUEST"]),
  severity: z.enum(["LOW", "MEDIUM", "HIGH", "CRITICAL"]),
  title: z
    .string()
    .trim()
    .min(4, "Give it a short summary")
    .max(140, "Keep the summary under 140 characters"),
  message: z.string().trim().min(10, "Tell us what happened").max(5000, "That is too long to file"),
  stepsToReproduce: z.string().trim().max(5000, "That is too long to file").optional(),
  // Optional, and only read at all when nobody is signed in. Empty is allowed on purpose: a tester
  // who would rather not leave an address should still be able to file.
  reporterEmail: z
    .union([z.literal(""), z.string().trim().email("That doesn't look like a valid email")])
    .optional(),
});

type ReportValues = z.infer<typeof reportSchema>;

/** The screenshot counts towards the server's ceiling of four, so three is what is left. */
const MAX_UPLOADS = 3;

const SEVERITIES: { value: ReportValues["severity"]; label: string }[] = [
  { value: "LOW", label: "Low — cosmetic or minor" },
  { value: "MEDIUM", label: "Medium — annoying, has a workaround" },
  { value: "HIGH", label: "High — blocks a task" },
  { value: "CRITICAL", label: "Critical — nothing works" },
];

/**
 * The report form: what happened, how bad, and what the screen looked like.
 *
 * <p>The screenshot arrives already taken — {@code FeedbackProvider} captures before this renders, so
 * the image is of the page the tester was on rather than of this panel sitting over it. All they do
 * here is decide whether to send it.
 */
export function FeedbackPanel({
  screenshot,
  context,
  onClose,
}: {
  screenshot: Blob | null;
  context: FeedbackContext;
  onClose: () => void;
}) {
  const { user } = useAuth();
  const signedIn = Boolean(user);

  const [includeScreenshot, setIncludeScreenshot] = useState(true);
  const [uploads, setUploads] = useState<File[]>([]);
  const [formError, setFormError] = useState<string | null>(null);
  const [filed, setFiled] = useState<FeedbackResponse | null>(null);
  const uploadInput = useRef<HTMLInputElement>(null);

  const {
    register,
    handleSubmit,
    watch,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<ReportValues>({
    resolver: zodResolver(reportSchema),
    defaultValues: {
      kind: "BUG",
      severity: "MEDIUM",
      title: "",
      message: "",
      stepsToReproduce: "",
      reporterEmail: "",
    },
  });

  const kind = watch("kind");

  const onSubmit = async (values: ReportValues) => {
    setFormError(null);
    try {
      const response = await submitFeedback(
        {
          kind: values.kind,
          severity: values.severity,
          title: values.title,
          message: values.message,
          stepsToReproduce: values.stepsToReproduce?.trim() || null,
          reporterEmail: signedIn ? null : values.reporterEmail?.trim() || null,
          context,
        },
        includeScreenshot ? screenshot : null,
        uploads,
      );
      setFiled(response);
    } catch (error) {
      setFormError(
        error instanceof ApiRequestError
          ? error.problem.detail
          : "Could not reach LightMove. Check your connection and try again.",
      );
    }
  };

  const handleFilesChosen = (chosen: FileList | null) => {
    if (!chosen) return;
    setUploads((current) => [...current, ...Array.from(chosen)].slice(0, MAX_UPLOADS));
    // Cleared so choosing the same file twice in a row still fires a change event — otherwise
    // removing an image and re-adding it silently does nothing.
    if (uploadInput.current) uploadInput.current.value = "";
  };

  return (
    <Drawer open onClose={onClose} label="Report a bug or request a feature">
      <div className="relative flex-none border-b border-line px-5 py-4">
        <div className="text-base font-semibold">
          {filed ? "Thanks — that's filed" : "Report a bug or request a feature"}
        </div>
        {!filed && (
          <p className="mt-1 pe-8 text-[12.5px] text-text3">
            Your browser, screen size and the page you're on are attached automatically.
          </p>
        )}
        <DrawerCloseButton onClose={onClose} />
      </div>

      {filed ? (
        <FiledConfirmation response={filed} onClose={onClose} />
      ) : (
        <form onSubmit={handleSubmit(onSubmit)} className="flex min-h-0 flex-1 flex-col">
          <div className="min-h-0 flex-1 overflow-y-auto px-5 py-4">
            {/* Not an <input>: the segmented control writes the field through setValue, so there is
                no hidden control to keep in step with it. */}
            <KindToggle value={kind} onChange={(next) => setValue("kind", next)} />

            <Field label="Summary" error={errors.title?.message}>
              <Input
                {...register("title")}
                invalid={Boolean(errors.title)}
                autoFocus
                placeholder={
                  kind === "BUG" ? "Saving the brief loses step 3" : "Let me export the shortlist"
                }
              />
            </Field>

            <Field
              label={kind === "BUG" ? "What happened" : "What would you like"}
              error={errors.message?.message}
            >
              <TextArea
                {...register("message")}
                invalid={Boolean(errors.message)}
                rows={5}
                placeholder={
                  kind === "BUG"
                    ? "What you expected, and what happened instead."
                    : "What you're trying to do, and why the app makes it hard."
                }
              />
            </Field>

            {kind === "BUG" && (
              <Field label="Steps to reproduce" hint="Optional, and the most useful thing you can add.">
                <TextArea
                  {...register("stepsToReproduce")}
                  rows={4}
                  placeholder={"1. Open a mandate\n2. Go to Position\n3. …"}
                />
              </Field>
            )}

            <Field label={kind === "BUG" ? "How bad is it" : "How much do you want it"}>
              <Select {...register("severity")}>
                {SEVERITIES.map((severity) => (
                  <option key={severity.value} value={severity.value}>
                    {severity.label}
                  </option>
                ))}
              </Select>
            </Field>

            {!signedIn && (
              <Field
                label="Your email"
                error={errors.reporterEmail?.message}
                hint="Optional — only so we can come back to you about this."
              >
                <Input
                  {...register("reporterEmail")}
                  invalid={Boolean(errors.reporterEmail)}
                  type="email"
                  placeholder="you@company.com"
                />
              </Field>
            )}

            <ScreenshotPreview
              screenshot={screenshot}
              included={includeScreenshot}
              onToggle={() => setIncludeScreenshot((on) => !on)}
            />

            <UploadList
              uploads={uploads}
              inputRef={uploadInput}
              onChoose={handleFilesChosen}
              onRemove={(index) => setUploads((current) => current.filter((_, i) => i !== index))}
            />

            <FormError message={formError} />
          </div>

          <div className="flex flex-none items-center justify-end gap-2 border-t border-line px-5 py-3.5">
            <Button type="button" variant="secondary" onClick={onClose} disabled={isSubmitting}>
              Cancel
            </Button>
            <Button type="submit" loading={isSubmitting}>
              Send report
            </Button>
          </div>
        </form>
      )}
    </Drawer>
  );
}

function KindToggle({ value, onChange }: { value: FeedbackKind; onChange: (next: FeedbackKind) => void }) {
  const options: { value: FeedbackKind; label: string; icon: string }[] = [
    { value: "BUG", label: "Something's broken", icon: ICONS.warning },
    { value: "FEATURE_REQUEST", label: "I'd like something", icon: ICONS.star },
  ];

  return (
    <div className="mb-4 grid grid-cols-2 gap-2">
      {options.map((option) => (
        <button
          key={option.value}
          type="button"
          onClick={() => onChange(option.value)}
          aria-pressed={value === option.value}
          className={cn(
            "flex items-center justify-center gap-2 rounded-lg border px-3 py-2.5 text-[12.5px] transition",
            value === option.value
              ? "border-amber-btn bg-amber-dim font-semibold text-text"
              : "border-line bg-panel2 text-text2 hover:text-text",
          )}
        >
          <Icon d={option.icon} size={14} className="flex-none" />
          {option.label}
        </button>
      ))}
    </div>
  );
}

function ScreenshotPreview({
  screenshot,
  included,
  onToggle,
}: {
  screenshot: Blob | null;
  included: boolean;
  onToggle: () => void;
}) {
  const preview = useMemo(
    () => (screenshot ? URL.createObjectURL(screenshot) : null),
    [screenshot],
  );

  // Revoked when the panel closes: an object URL holds the blob alive for as long as the document
  // does, and a tester filing twenty reports would otherwise be carrying twenty screenshots around.
  useEffect(() => () => { if (preview) URL.revokeObjectURL(preview); }, [preview]);

  if (!screenshot || !preview) {
    return (
      <p className="mb-4 rounded-lg border border-line bg-panel2 px-3 py-2.5 font-mono text-[11px] text-text3">
        The screen could not be captured on this page. Attach an image below instead.
      </p>
    );
  }

  return (
    <div className="mb-4">
      <span className="mb-1.5 block font-mono text-[10px] font-semibold uppercase tracking-[0.12em] text-text3">
        Screenshot
      </span>
      <div className="overflow-hidden rounded-lg border border-line">
        <img
          src={preview}
          alt="The screen as it was when you opened this form"
          className={cn("block max-h-44 w-full object-cover object-top transition", !included && "opacity-30")}
        />
        <label className="flex cursor-pointer items-center gap-2 border-t border-line bg-panel2 px-3 py-2 text-[12.5px] text-text2">
          <input type="checkbox" checked={included} onChange={onToggle} className="accent-amber-btn" />
          Attach this screenshot
        </label>
      </div>
    </div>
  );
}

function UploadList({
  uploads,
  inputRef,
  onChoose,
  onRemove,
}: {
  uploads: File[];
  inputRef: RefObject<HTMLInputElement | null>;
  onChoose: (files: FileList | null) => void;
  onRemove: (index: number) => void;
}) {
  return (
    <div className="mb-4">
      <span className="mb-1.5 block font-mono text-[10px] font-semibold uppercase tracking-[0.12em] text-text3">
        Your own images
      </span>

      {uploads.map((file, index) => (
        <div
          key={`${file.name}-${index}`}
          className="mb-1.5 flex items-center gap-2 rounded-lg border border-line bg-panel2 px-3 py-2"
        >
          <Icon d={ICONS.file} size={14} className="flex-none text-text3" />
          <span className="min-w-0 flex-1 truncate font-mono text-[11.5px] text-text2">{file.name}</span>
          <button
            type="button"
            onClick={() => onRemove(index)}
            aria-label={`Remove ${file.name}`}
            className="flex-none rounded-md p-1 text-text3 transition hover:text-red"
          >
            <Icon d={ICONS.close} size={13} />
          </button>
        </div>
      ))}

      {uploads.length < MAX_UPLOADS && (
        <button
          type="button"
          onClick={() => inputRef.current?.click()}
          className="flex w-full items-center justify-center gap-2 rounded-lg border border-dashed border-line px-3 py-2.5 text-[12.5px] text-text3 transition hover:border-text3 hover:text-text2"
        >
          <Icon d={ICONS.uploadCloud} size={14} />
          Add an image
        </button>
      )}

      <input
        ref={inputRef}
        type="file"
        accept="image/png,image/jpeg,image/webp,image/gif"
        multiple
        hidden
        onChange={(event) => onChoose(event.target.files)}
      />
    </div>
  );
}

/**
 * What happened to the report. `published: false` is not a failure — it is a deployment with no
 * issue-tracker credential, where the report was received and logged. Saying so beats offering a link
 * that goes nowhere.
 */
function FiledConfirmation({ response, onClose }: { response: FeedbackResponse; onClose: () => void }) {
  return (
    <div className="flex min-h-0 flex-1 flex-col justify-between px-5 py-5">
      <div>
        <div className="mb-3 flex size-9 items-center justify-center rounded-full bg-green-dim text-green">
          <Icon d={ICONS.check} size={18} />
        </div>

        {response.published && response.issueUrl ? (
          <>
            <p className="text-[13.5px] text-text2">
              Filed as issue <span className="font-mono font-semibold text-text">#{response.issueNumber}</span>,
              with everything you attached.
            </p>
            <a
              href={response.issueUrl}
              target="_blank"
              rel="noreferrer"
              className="mt-3 inline-flex items-center gap-1.5 font-mono text-[12px] text-sky hover:underline"
            >
              Open it on GitHub
              <Icon d={ICONS.arrowRight} size={13} />
            </a>
          </>
        ) : (
          <p className="text-[13.5px] text-text2">
            Your report was received. This deployment has no issue tracker wired up, so it has been
            recorded in the server log rather than filed.
          </p>
        )}
      </div>

      <Button type="button" variant="secondary" onClick={onClose} className="mt-6 self-end">
        Close
      </Button>
    </div>
  );
}
