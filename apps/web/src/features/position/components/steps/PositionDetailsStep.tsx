import { useState } from "react";
import { Input, Select, TextArea } from "../../../../components/ui";
import type { PositionDetails, PositionDocument } from "../../api/types";
import { EMPLOYMENT_TYPE_LABELS, SENIORITY_LABELS } from "../../lib/labels";
import { PositionDocumentDropzone } from "../PositionDocumentDropzone";
import { AddRowButton, CheckedInput, RemoveRowButton, StepField, SubCard } from "../fields";

/** Step one: what the role is. */
export function PositionDetailsStep({
  details,
  document,
  uploading,
  onDownload,
  onChange,
  onAttachDocument,
  onRemoveDocument,
}: {
  details: PositionDetails;
  document: PositionDocument | null;
  uploading: boolean;
  onDownload: () => void;
  onChange: (patch: Partial<PositionDetails>) => void;
  onAttachDocument: (file: File) => void;
  onRemoveDocument: () => void;
}) {
  const [draft, setDraft] = useState("");

  const addResponsibility = () => {
    const text = draft.trim();
    if (!text) return;
    onChange({ responsibilities: [...details.responsibilities, text] });
    setDraft("");
  };

  return (
    <div className="flex flex-col gap-5">
      <PositionDocumentDropzone
        document={document}
        uploading={uploading}
        onDownload={onDownload}
        onAttach={onAttachDocument}
        onRemove={onRemoveDocument}
      />

      <div className="grid grid-cols-1 gap-4 md:grid-cols-2 md:gap-x-[18px]">
        <StepField label="Role title">
          <CheckedInput
            value={details.roleTitle}
            placeholder="e.g. Chief Financial Officer"
            onChange={(event) => onChange({ roleTitle: event.target.value })}
          />
        </StepField>
        <StepField label="Department">
          <CheckedInput
            value={details.department ?? ""}
            placeholder="e.g. Group Finance"
            onChange={(event) => onChange({ department: event.target.value || null })}
          />
        </StepField>
      </div>

      <div className="grid grid-cols-1 gap-4 md:grid-cols-3 md:gap-x-[18px]">
        <StepField label="Location">
          <Input
            value={details.location ?? ""}
            placeholder="e.g. Abu Dhabi, UAE"
            onChange={(event) => onChange({ location: event.target.value || null })}
          />
        </StepField>
        <StepField label="Employment type">
          <Select
            value={details.employmentType ?? ""}
            onChange={(event) =>
              onChange({
                employmentType: (event.target.value || null) as PositionDetails["employmentType"],
              })
            }
          >
            <option value="">Not set</option>
            {Object.entries(EMPLOYMENT_TYPE_LABELS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </Select>
        </StepField>
        <StepField label="Seniority">
          <Select
            value={details.seniority ?? ""}
            onChange={(event) =>
              onChange({ seniority: (event.target.value || null) as PositionDetails["seniority"] })
            }
          >
            <option value="">Not set</option>
            {Object.entries(SENIORITY_LABELS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </Select>
        </StepField>
      </div>

      <div>
        <span className="mb-1.5 block text-xs font-semibold uppercase tracking-[0.02em] text-text2">
          Key responsibilities
        </span>
        <SubCard>
          {details.responsibilities.length > 0 && (
            <div className="flex flex-wrap gap-2">
              {details.responsibilities.map((responsibility, index) => (
                <span
                  key={`${responsibility}-${index}`}
                  className="inline-flex items-center gap-[7px] rounded border border-line bg-panel py-1.5 pe-2 ps-2.5 text-xs font-semibold text-text2"
                >
                  {responsibility}
                  <RemoveRowButton
                    label={`Remove ${responsibility}`}
                    onClick={() =>
                      onChange({
                        responsibilities: details.responsibilities.filter((_, i) => i !== index),
                      })
                    }
                  />
                </span>
              ))}
            </div>
          )}
          <div className="mt-3.5 flex gap-2">
            <Input
              value={draft}
              aria-label="Add a responsibility"
              placeholder="Add a responsibility…"
              onChange={(event) => setDraft(event.target.value)}
              onKeyDown={(event) => {
                if (event.key !== "Enter") return;
                event.preventDefault();
                addResponsibility();
              }}
              className="flex-1 bg-panel"
            />
            <AddRowButton onClick={addResponsibility} className="flex-none">
              + Add custom
            </AddRowButton>
          </div>
        </SubCard>
      </div>

      <StepField label="Ideal profile — drafted from the brief">
        <TextArea
          value={details.narrative ?? ""}
          rows={5}
          onChange={(event) => onChange({ narrative: event.target.value || null })}
          className="min-h-[104px] border-line-soft px-4 py-3.5 font-sans text-[13.5px] leading-[1.65] text-text2"
        />
      </StepField>
    </div>
  );
}
