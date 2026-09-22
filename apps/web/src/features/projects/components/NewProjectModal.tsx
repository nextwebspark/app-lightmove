import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useMemo, useState } from "react";
import { Button, Field, FormError, Modal, Select, useToast } from "../../../components/ui";
import { codeOf } from "../../../lib/errorCodes";
import { fieldErrorsFrom } from "../../../lib/formErrors";
import * as clientsApi from "../../clients/api/clientsApi";
import type { Client } from "../../clients/api/types";
import { CompanyPicker } from "../../clients/components/CompanyPicker";
import { pickedCompanyName, type CompanyPick } from "../../clients/lib/companyPick";
import * as positionApi from "../../position/api/positionApi";
import { RoleTitleCombobox } from "../../position/components/RoleTitleCombobox";
import type { ProjectType } from "../api/types";
import * as projectsApi from "../api/projectsApi";
import { autoMappingTarget, timelineProblem } from "../lib/timeline";
import { ProjectTimelineFields } from "./ProjectTimelineFields";
import { ProjectTypeChooser } from "./ProjectTypeChooser";
import { todayIso } from "../../../lib/dates";

const NEW_CLIENT = "__new__";

/** Mirrors `@Size(max = 160)` on CreateProjectRequest.positionTitle, so the cap is met at the field. */
const MAX_POSITION_TITLE_LENGTH = 160;

/** The inputs a rejected create can be attributed to; the client select offers ids only. */
type ProjectField = "newClientName" | "positionTitle" | "mappingTargetDate" | "shortlistTargetDate";

/**
 * The New-project modal: client (pick or create inline), position (typed free, or picked from the
 * role-template library — the same combobox as the brief's step one), what the mandate is engaged to
 * deliver, and the dates it will be measured against. There is no lead to choose — whoever creates the
 * mandate is seated as its admin (and lead) by the server, and delegates from the project drawer
 * afterwards. A 409 on the inline client quietly resolves to the existing record — the user meant that
 * client.
 *
 * Opened from a client's drawer, the entrance has already decided the client: the field is shown
 * locked and `lockedClientId` — not state — is what gets submitted, so the mandate cannot land on a
 * different client than the drawer behind the modal. Picking or creating a client belongs to the
 * other entrance (Projects → New project), which passes no `lockedClientId`.
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
  /** Locks the client to this one — set when opening from that client's drawer ("New mandate"). */
  lockedClientId?: string;
}) {
  const queryClient = useQueryClient();
  const toast = useToast();

  // null until the user picks: seeding from `clients` at mount mirrors server state, and the list is
  // still empty on the render where Projects opens this modal before its clients query has settled.
  const [pickedClientId, setPickedClientId] = useState<string | null>(null);
  const [newClientPick, setNewClientPick] = useState<CompanyPick | null>(null);
  const [positionTitle, setPositionTitle] = useState("");
  const [projectType, setProjectType] = useState<ProjectType>("MAPPING");
  const [startDate, setStartDate] = useState(todayIso);
  const [mappingTargetDate, setMappingTargetDate] = useState("");
  const [shortlistTargetDate, setShortlistTargetDate] = useState("");
  // The derived mapping target belongs to the form until the user takes it: after their first edit it
  // stops following the shortlist date, or every correction would be undone by the next keystroke.
  const [mappingTargetEdited, setMappingTargetEdited] = useState(false);
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
  // Rebuilt only when the registry changes: this modal re-renders on every keystroke in the title.
  const existingClientNames = useMemo(
    () => new Set(clients.map((client) => client.name.toLowerCase())),
    [clients],
  );
  const clientId = lockedClientId || pickedClientId || clients[0]?.id || NEW_CLIENT;

  const creatingClient = clientId === NEW_CLIENT;

  const create = useMutation({
    mutationFn: async () => {
      let resolvedClientId = clientId;
      if (creatingClient) {
        const pick = newClientPick!;
        try {
          // The same request the registry's New-client modal posts, so a company picked here is
          // resolved against the universe rather than filed as a custom record that duplicates it.
          resolvedClientId = (
            await clientsApi.createClient(clientsApi.createClientPayloadFor(pick))
          ).id;
        } catch (clientError) {
          if (codeOf(clientError) !== "CLIENT_ALREADY_EXISTS") throw clientError;
          // The user meant that client. Re-fetch rather than trust the prop — a colleague may have
          // created it after this modal's list was cached. The name is the universe's on a DB pick,
          // which is the name the server refused as a duplicate.
          const fresh = await clientsApi.clients();
          const existing = fresh.find(
            (c) => c.name.toLowerCase() === pickedCompanyName(pick).toLowerCase(),
          );
          if (!existing) throw clientError;
          resolvedClientId = existing.id;
        }
      }
      return projectsApi.createProject({
        clientId: resolvedClientId,
        positionTitle: positionTitle.trim(),
        projectType,
        startDate: startDate || undefined,
        mappingTargetDate: mappingTargetDate || undefined,
        shortlistTargetDate:
          projectType === "EXECUTIVE_SEARCH" ? shortlistTargetDate || undefined : undefined,
      });
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: projectsApi.PROJECTS_KEY });
      void queryClient.invalidateQueries({ queryKey: clientsApi.CLIENTS_KEY });
      toast("Project created — you're its admin and lead");
      onClose();
    },
    onError: (mutationError) => {
      // Two requests can fail here — the inline client create and the project create — so both DTOs'
      // field names are mapped onto the field that carries them.
      const { fields, formMessage } = fieldErrorsFrom(mutationError, {
        customName: "newClientName",
        positionTitle: "positionTitle",
        mappingTargetDate: "mappingTargetDate",
        shortlistTargetDate: "shortlistTargetDate",
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

  const submit = () => {
    setError(null);
    setFieldErrors({});
    if (creatingClient && !newClientPick) {
      setFieldErrors({ newClientName: "Pick the client's company, or add it as a new one" });
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
    const problem = timelineProblem(projectType, startDate, mappingTargetDate, shortlistTargetDate);
    if (problem) {
      setFieldErrors({ [problem.field]: problem.message });
      return;
    }
    create.mutate();
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="New project"
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Cancel
          </Button>
          <Button loading={create.isPending} onClick={submit}>
            Create project
          </Button>
        </>
      }
    >
      <FormError message={error} />

      {/* The hint carries the name because a disabled <select> is skipped in a screen reader's forms
          mode — Field renders the hint inside the wrapping <label>, so it reaches the accessible name
          even when the control itself never gets focus. */}
      <Field
        label="Client"
        hint={locked ? `This mandate belongs to ${locked.name}.` : undefined}
      >
        {lockedClientId ? (
          // Disabled rather than replaced by plain text: the user still sees which client the mandate
          // is for, and the label keeps a control to name.
          <Select value={lockedClientId} disabled className="cursor-not-allowed opacity-60">
            <option value={lockedClientId}>{locked?.name ?? "Selected client"}</option>
          </Select>
        ) : (
          <Select value={clientId} onChange={(event) => setPickedClientId(event.target.value)}>
            {clients.map((client) => (
              <option key={client.id} value={client.id}>
                {client.name}
              </option>
            ))}
            <option value={NEW_CLIENT}>＋ New client…</option>
          </Select>
        )}
      </Field>

      {/* The registry's own company step, not a bare name box: a mandate opened this way resolves its
          client against the universe exactly as Clients → New client does. */}
      {creatingClient && (
        <CompanyPicker
          pick={newClientPick}
          onPick={(pick) => {
            setNewClientPick(pick);
            clearFieldError("newClientName");
          }}
          existingNames={existingClientNames}
          // A company already on the books is not a dead end here: the registry has it, so the select
          // is switched to that client. Announced, because the picker vanishing and a different
          // control coming back is otherwise an unexplained answer to clicking a badged row.
          onRejectExisting={(name) => {
            const existing = clients.find(
              (client) => client.name.toLowerCase() === name.toLowerCase(),
            );
            if (!existing) return;
            setPickedClientId(existing.id);
            setNewClientPick(null);
            clearFieldError("newClientName");
            toast(`${existing.name} is already a client — selected`);
          }}
          error={fieldErrors.newClientName}
          autoFocus
        />
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

      {/* Not wrapped in `Field`: its <label> would associate with the first button in the group and
          name that one option "Project type", which is the wrong name on the wrong control. The
          group carries its own aria-label instead. */}
      <div className="mb-4">
        <div className="mb-1.5 font-mono text-[10px] font-semibold uppercase tracking-[0.12em] text-text3">
          Project type
        </div>
        <ProjectTypeChooser
          value={projectType}
          onChange={(picked) => {
            setProjectType(picked);
            clearFieldError("mappingTargetDate");
            clearFieldError("shortlistTargetDate");
          }}
        />
      </div>

      <ProjectTimelineFields
        projectType={projectType}
        startDate={startDate}
        mappingTargetDate={mappingTargetDate}
        shortlistTargetDate={shortlistTargetDate}
        errors={fieldErrors}
        onStartDateChange={(value) => {
          setStartDate(value);
          if (!mappingTargetEdited && value && shortlistTargetDate) {
            setMappingTargetDate(autoMappingTarget(value, shortlistTargetDate));
          }
        }}
        onMappingTargetChange={(value) => {
          setMappingTargetDate(value);
          setMappingTargetEdited(true);
          clearFieldError("mappingTargetDate");
        }}
        onShortlistTargetChange={(value) => {
          setShortlistTargetDate(value);
          if (!mappingTargetEdited && value && startDate) {
            setMappingTargetDate(autoMappingTarget(startDate, value));
          }
          clearFieldError("shortlistTargetDate");
        }}
      />
    </Modal>
  );
}
