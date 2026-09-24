import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useMemo, useState, type ReactNode } from "react";
import {
  Button,
  ChoiceCardGroup,
  DateInput,
  Field,
  FormError,
  Input,
  Modal,
  Notice,
  Select,
  useToast,
  type ChoiceCardOption,
} from "../../../components/ui";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { cn } from "../../../lib/cn";
import { codeOf } from "../../../lib/errorCodes";
import { fieldErrorsFrom } from "../../../lib/formErrors";
import { formatDate } from "../../../lib/format";
import * as clientsApi from "../../clients/api/clientsApi";
import type { Client } from "../../clients/api/types";
import * as positionApi from "../../position/api/positionApi";
import { RoleTitleCombobox } from "../../position/components/RoleTitleCombobox";
import * as projectsApi from "../api/projectsApi";
import type { ProjectType } from "../api/types";
import { autoMappingTarget, daysBetween, MAPPING_SHARE, todayIso } from "../lib/timeline";

const NEW_CLIENT = "__new__";

/** Mirrors `@Size(max = 160)` on CreateProjectRequest.positionTitle, so the cap is met at the field. */
const MAX_POSITION_TITLE_LENGTH = 160;

/** Mirrors `@Size(max = 160)` on CreateClientRequest.customName. */
const MAX_BUSINESS_UNIT_NAME_LENGTH = 160;

/** The inputs a rejected create can be attributed to; the business unit select offers ids only. */
type ProjectField = "newClientName" | "positionTitle" | "deliveryDate" | "mappingTargetDate";

const PROJECT_TYPE_OPTIONS: readonly ChoiceCardOption<ProjectType>[] = [
  {
    value: "MAPPING",
    title: "Mapping",
    body: "Deliver a mapped executive universe",
    icon: <Icon d={ICONS.globe} size={18} />,
  },
  {
    value: "SEARCH",
    title: "Search",
    body: "Full search through to placement",
    icon: <Icon d={ICONS.briefcase} size={18} />,
  },
];

const TIMELINE_HEADING =
  "mb-4 mt-5 border-t border-line-soft pt-4 font-mono text-[10px] font-semibold uppercase tracking-[0.14em] text-text3";

/**
 * The New-position modal (`claude-design/Workspace.dc.html`): business unit (pick, or name a new one),
 * position (typed free, or picked from the role-template library — the same combobox as the brief's
 * step one), project type, and the timeline. There is no lead to choose — whoever creates the mandate
 * is seated as its lead by the server. A 409 on the inline business unit quietly resolves to the
 * existing record — the user meant that one.
 *
 * Opened from a business unit's drawer, the entrance has already decided the unit: the field is shown
 * locked and `lockedClientId` — not state — is what gets submitted, so the mandate cannot land on a
 * different unit than the drawer behind the modal.
 *
 * A search's mapping target is previewed here at {@link MAPPING_SHARE} of the window and sent only when
 * the user moved it; the server fills the same default otherwise, so the rule has one home.
 */
export function NewProjectModal({
  open,
  onClose,
  clients,
  lockedClientId,
}: {
  open: boolean;
  onClose: () => void;
  clients: Client[];
  /** Locks the business unit to this one — set when opening from that unit's drawer. */
  lockedClientId?: string;
}) {
  const queryClient = useQueryClient();
  const toast = useToast();

  // null until the user picks: seeding from `clients` at mount mirrors server state, and the list is
  // still empty on the render where Projects opens this modal before its clients query has settled.
  const [pickedClientId, setPickedClientId] = useState<string | null>(null);
  const [newClientName, setNewClientName] = useState("");
  const [positionTitle, setPositionTitle] = useState("");
  const [projectType, setProjectType] = useState<ProjectType>("MAPPING");
  const [startDate, setStartDate] = useState(todayIso);
  const [deliveryDate, setDeliveryDate] = useState("");
  const [mappingTargetOverride, setMappingTargetOverride] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Partial<Record<ProjectField, string>>>({});

  // The picker's options, sharing the Position page's cache. A failed read leaves the field a plain
  // typeable input — the same degradation as there.
  const { data: templates = [] } = useQuery({
    queryKey: positionApi.POSITION_TEMPLATES_KEY,
    queryFn: ({ signal }) => positionApi.listTemplates(signal),
    staleTime: 5 * 60 * 1000,
  });

  // Derived from the prop on every render, never seeded into state: a mount-time seed goes stale the
  // moment this modal is rendered always-mounted with `open` toggled, and would then aim the mandate
  // at whichever client's drawer was opened before. One `locked` value decides both what is rendered
  // and what is submitted — two tests of the same prop are how the shown client and the sent one drift
  // apart, which is the bug this lock exists to close.
  const locked = lockedClientId ? clients.find((client) => client.id === lockedClientId) : undefined;
  // Rebuilt only when the registry changes: this modal re-renders on every keystroke.
  const clientsByName = useMemo(
    () => new Map(clients.map((client) => [client.name.toLowerCase(), client])),
    [clients],
  );
  const clientId = lockedClientId || pickedClientId || clients[0]?.id || NEW_CLIENT;
  const creatingClient = clientId === NEW_CLIENT;

  const isMapping = projectType === "MAPPING";
  const windowDays = startDate && deliveryDate ? daysBetween(startDate, deliveryDate) : null;
  const dateOrderError =
    windowDays !== null && windowDays <= 0
      ? `${isMapping ? "Map" : "Shortlist"} delivery date must be after the project start date.`
      : undefined;
  const mappingTarget = mappingTargetOverride || autoMappingTarget(startDate, deliveryDate);

  const create = useMutation({
    mutationFn: async () => {
      let resolvedClientId = clientId;
      if (creatingClient) {
        const name = newClientName.trim();
        const known = clientsByName.get(name.toLowerCase());
        if (known) {
          resolvedClientId = known.id;
        } else {
          try {
            resolvedClientId = (await clientsApi.createClient({ customName: name })).id;
          } catch (clientError) {
            if (codeOf(clientError) !== "CLIENT_ALREADY_EXISTS") throw clientError;
            // The user meant that unit. Re-fetch rather than trust the prop — a colleague may have
            // created it after this modal's list was cached.
            const fresh = await clientsApi.clients();
            const existing = fresh.find((c) => c.name.toLowerCase() === name.toLowerCase());
            if (!existing) throw clientError;
            resolvedClientId = existing.id;
          }
        }
      }
      return projectsApi.createProject({
        clientId: resolvedClientId,
        positionTitle: positionTitle.trim(),
        projectType,
        startDate: startDate || undefined,
        deliveryDate: deliveryDate || undefined,
        mappingTargetDate: !isMapping && mappingTargetOverride ? mappingTargetOverride : undefined,
      });
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: projectsApi.PROJECTS_KEY });
      void queryClient.invalidateQueries({ queryKey: clientsApi.CLIENTS_KEY });
      toast("Position created — you're its admin and lead");
      onClose();
    },
    onError: (mutationError) => {
      // Two requests can fail here — the inline business unit create and the position create — so both
      // DTOs' field names are mapped onto the field that carries them.
      const { fields, formMessage } = fieldErrorsFrom(mutationError, {
        customName: "newClientName",
        positionTitle: "positionTitle",
        deliveryDate: "deliveryDate",
        mappingTargetDate: "mappingTargetDate",
      });
      setFieldErrors(fields);
      setError(formMessage);
    },
  });

  // Cleared as the field is edited, not only on the next submit: react-hook-form's reValidateMode
  // does this for every other form wearing `invalid=`, and without it a corrected value keeps the red
  // border and a message that no longer describes it.
  const clearFieldError = (field: ProjectField) =>
    setFieldErrors((current) => (current[field] ? { ...current, [field]: undefined } : current));

  const handlePositionTitleChange = (title: string) => {
    setPositionTitle(title);
    clearFieldError("positionTitle");
  };

  // A moved mapping target is a statement about the old window, so any change to the window drops it.
  const handleProjectTypeChange = (type: ProjectType) => {
    setProjectType(type);
    setMappingTargetOverride("");
    clearFieldError("mappingTargetDate");
  };
  const handleStartDateChange = (isoDate: string) => {
    setStartDate(isoDate);
    setMappingTargetOverride("");
    clearFieldError("deliveryDate");
  };
  const handleDeliveryDateChange = (isoDate: string) => {
    setDeliveryDate(isoDate);
    setMappingTargetOverride("");
    clearFieldError("deliveryDate");
  };
  const handleMappingTargetChange = (isoDate: string) => {
    setMappingTargetOverride(isoDate);
    clearFieldError("mappingTargetDate");
  };

  const submit = () => {
    setError(null);
    setFieldErrors({});
    const name = newClientName.trim();
    if (creatingClient && !name) {
      setFieldErrors({ newClientName: "Enter the business unit name" });
      return;
    }
    if (creatingClient && name.length > MAX_BUSINESS_UNIT_NAME_LENGTH) {
      setFieldErrors({
        newClientName: `That name is too long — keep it to ${MAX_BUSINESS_UNIT_NAME_LENGTH} characters or fewer`,
      });
      return;
    }
    if (!positionTitle.trim()) {
      setFieldErrors({ positionTitle: "Enter the position title" });
      return;
    }
    if (positionTitle.trim().length > MAX_POSITION_TITLE_LENGTH) {
      setFieldErrors({
        positionTitle: `That title is too long — keep it to ${MAX_POSITION_TITLE_LENGTH} characters or fewer`,
      });
      return;
    }
    if (dateOrderError) return;
    create.mutate();
  };

  const showSummary = windowDays !== null && windowDays > 0;
  const mappingTargetDays = mappingTarget ? daysBetween(startDate, mappingTarget) : null;

  return (
    // The dialog keeps its own scroll: the timeline makes this form taller than a laptop screen, and the
    // template list opens over the type cards below the Position field, well inside the scroll area.
    <Modal open={open} onClose={onClose} title="New position">
      <FormError message={error} />

      {/* The hint carries the name because a disabled <select> is skipped in a screen reader's forms
          mode — Field renders the hint inside the wrapping <label>, so it reaches the accessible name
          even when the control itself never gets focus. */}
      <Field
        label="Business unit"
        hint={locked ? `This position belongs to ${locked.name}.` : undefined}
      >
        {lockedClientId ? (
          // Disabled rather than replaced by plain text: the user still sees which unit the position
          // is for, and the label keeps a control to name.
          <Select value={lockedClientId} disabled className="cursor-not-allowed opacity-60">
            <option value={lockedClientId}>{locked?.name ?? "Selected business unit"}</option>
          </Select>
        ) : (
          <Select value={clientId} onChange={(event) => setPickedClientId(event.target.value)}>
            {clients.map((client) => (
              <option key={client.id} value={client.id}>
                {client.name}
              </option>
            ))}
            <option value={NEW_CLIENT}>＋ New business unit…</option>
          </Select>
        )}
      </Field>

      {creatingClient && (
        <Field label="New business unit name" error={fieldErrors.newClientName}>
          <Input
            value={newClientName}
            onChange={(event) => {
              setNewClientName(event.target.value);
              clearFieldError("newClientName");
            }}
            placeholder="e.g. Data & Analytics"
            invalid={!!fieldErrors.newClientName}
            autoFocus
          />
        </Field>
      )}

      {/* Picking a template only fills the title: creation seeds the brief from the title on the
          server, through the same keyword match a typed one gets, so no id travels with the form. */}
      <Field label="Position" error={fieldErrors.positionTitle}>
        <RoleTitleCombobox
          value={positionTitle}
          templates={templates}
          busy={false}
          invalid={!!fieldErrors.positionTitle}
          onChange={handlePositionTitleChange}
          onPick={(template) => handlePositionTitleChange(template.title)}
        />
      </Field>

      <div className="mb-4">
        <span className="mb-1.5 block font-mono text-[10px] font-semibold uppercase tracking-[0.12em] text-text3">
          Project type
        </span>
        <ChoiceCardGroup
          label="Project type"
          options={PROJECT_TYPE_OPTIONS}
          value={projectType}
          onChange={handleProjectTypeChange}
          className="sm:grid-cols-2"
        />
      </div>

      <div className={TIMELINE_HEADING}>Timeline</div>

      <Field
        label="Project start date"
        hint="Defaults to today. Edit if the position starts later or was kicked off earlier."
      >
        <DateInput value={startDate} onChange={handleStartDateChange} />
      </Field>

      <Field
        label={isMapping ? "Map delivery date" : "Shortlist delivery date"}
        hint={
          isMapping
            ? "When does the business unit expect the completed universe map?"
            : "When does the business unit expect the shortlist?"
        }
        error={dateOrderError ?? fieldErrors.deliveryDate}
      >
        <DateInput value={deliveryDate} min={startDate} onChange={handleDeliveryDateChange} />
      </Field>

      {showSummary && isMapping && (
        <Notice title="Target">
          <div className="text-[13.5px] font-semibold text-text">
            Map delivery: {formatDate(deliveryDate)} · Starting {formatDate(startDate)}
          </div>
          <div className="mt-0.5 font-mono text-meta text-text2">{windowDays} days from start</div>
        </Notice>
      )}

      {showSummary && !isMapping && mappingTarget && (
        <Notice title="Auto-calculated milestones">
          <MilestoneRow
            label="Mapping target"
            dotClassName="bg-sky"
            note={`~${Math.round(MAPPING_SHARE * 100)}% of window · ${mappingTargetDays} days from start${
              mappingTargetOverride ? " · edited" : ""
            }`}
            error={fieldErrors.mappingTargetDate}
          >
            <DateInput
              value={mappingTarget}
              min={startDate}
              onChange={handleMappingTargetChange}
              ariaLabel="Mapping target date"
              className="w-auto gap-1.5 border-0 bg-transparent p-0 font-sans text-[12.5px] font-semibold"
            />
          </MilestoneRow>
          <MilestoneRow label="Shortlist target" dotClassName="bg-amber" note="Matches shortlist delivery date">
            <span className="flex items-center gap-1.5 text-[12.5px] font-semibold text-text">
              {formatDate(deliveryDate)}
              <Icon d={ICONS.lock} size={12} className="text-text3" />
            </span>
          </MilestoneRow>
          <div className="mt-2 border-t border-line-soft pt-2 text-meta text-text3">
            Auto-calculated from the start and shortlist dates.
          </div>
        </Notice>
      )}

      <div className="mt-5 flex justify-end gap-2">
        <Button variant="secondary" onClick={onClose}>
          Cancel
        </Button>
        <Button loading={create.isPending} onClick={submit}>
          Create position
        </Button>
      </div>
    </Modal>
  );
}

function MilestoneRow({
  label,
  dotClassName,
  note,
  error,
  children,
}: {
  label: string;
  dotClassName: string;
  note: string;
  error?: string;
  children: ReactNode;
}) {
  return (
    <div className="flex items-start gap-2 border-t border-line-soft py-2">
      <span aria-hidden="true" className={cn("mt-[5px] size-1.5 flex-none rounded-full", dotClassName)} />
      <div className="min-w-0 flex-1">
        <div className="flex items-center justify-between gap-2">
          <span className="font-mono text-[10px] font-semibold uppercase tracking-[0.06em] text-text3">{label}</span>
          {children}
        </div>
        <div className="mt-0.5 text-meta text-text3">{note}</div>
        {error && <div className="mt-0.5 font-mono text-meta text-red">{error}</div>}
      </div>
    </div>
  );
}
