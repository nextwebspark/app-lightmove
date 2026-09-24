import type { WorkspaceUser } from "../../api/types";
import { InitialsAvatar } from "../components/PopupChrome";
import { ProjectSelect } from "../components/ProjectSelect";
import { SectionLabel } from "../components/SectionLabel";
import { ToggleRow } from "../components/ToggleRow";
import { useCaptureSettings } from "../hooks/useCaptureSettings";
import type { ProjectSelection } from "../hooks/useProjectSelection";
import { Icon } from "../components/Icon";
import { ICONS } from "../lib/icons";

interface CaptureSettingsScreenProps {
  user: WorkspaceUser | null;
  projects: ProjectSelection;
  onBack: () => void;
  onSignOut: () => void;
}

/**
 * How the popup behaves, and who it is paired as.
 *
 * Nothing here is a fact about a mandate, so nothing here reaches the workspace — the whole screen
 * writes to extension storage. Signing out ends the extension's own session and leaves the browser's
 * alone, which is the point of pairing being a separate family.
 */
export function CaptureSettingsScreen({ user, projects, onBack, onSignOut }: CaptureSettingsScreenProps) {
  const { settings, update } = useCaptureSettings();

  return (
    <div className="flex flex-1 flex-col overflow-hidden">
      <header className="flex items-center gap-[9px] border-b border-u-border px-3.5 py-[11px]">
        <button
          type="button"
          onClick={onBack}
          aria-label="Back to capture"
          className="grid h-[26px] w-[26px] place-items-center rounded-[7px] border border-u-border-strong text-u-text2 hover:text-u-text"
        >
          <Icon d={ICONS.chevronLeft} />
        </button>
        <span className="flex-1 font-mono text-[12.5px] font-semibold tracking-[0.02em]">Settings</span>
      </header>

      <div className="flex-1 overflow-y-auto p-3.5">
        <SectionLabel className="mb-2">Default destination</SectionLabel>
        <ProjectSelect
          projects={projects.projects}
          selectedProjectId={settings.defaultProjectId}
          onSelect={(projectId) => update({ defaultProjectId: projectId || null })}
          isLoading={projects.isLoading}
          unsetLabel="Ask every time — offer the last one used"
        />

        <SectionLabel className="mb-2 mt-[18px]">Behaviour</SectionLabel>
        <div className="divide-y divide-u-border rounded-lg border border-u-border-strong">
          <ToggleRow
            label="Auto-detect page type"
            hint="Opens the Person or Company tab to match the page"
            isOn={settings.isPageTypeDetected}
            onToggle={(isOn) => update({ isPageTypeDetected: isOn })}
          />
          <ToggleRow
            label="Close after save"
            hint="Closes the popup once a capture lands"
            isOn={settings.closesAfterSave}
            onToggle={(isOn) => update({ closesAfterSave: isOn })}
          />
        </div>

        <SectionLabel className="mb-2 mt-[18px]">Session</SectionLabel>
        <div className="flex items-center gap-2.5 rounded-lg border border-u-border-strong bg-u-raised px-2.5 py-2">
          {user && <InitialsAvatar name={user.fullName} />}
          <span className="flex-1 overflow-hidden">
            <span className="block truncate text-[12.5px] font-medium text-u-text">{user?.fullName ?? "Not paired"}</span>
            <span className="block truncate text-[10.5px] text-u-text3">{user?.email ?? ""}</span>
          </span>
          <button
            type="button"
            onClick={onSignOut}
            className="rounded-lg border border-u-border-strong px-2.5 py-1.5 text-[11.5px] font-semibold text-u-text2 hover:border-u-offlimits hover:text-u-offlimits"
          >
            Sign out
          </button>
        </div>
        <p className="mt-2 text-[10.5px] leading-[1.5] text-u-text3">
          Signing out here ends only the extension's session. Your browser stays signed in, and the
          session appears in Settings → Active sessions until you end it from either side.
        </p>
      </div>
    </div>
  );
}
