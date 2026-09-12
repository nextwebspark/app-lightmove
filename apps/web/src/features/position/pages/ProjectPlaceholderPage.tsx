import { EmptyState } from "../../../components/ui";
import { Icon, ICONS } from "../../../components/layout/Icon";

/**
 * The not-yet-built tabs of the project shell (Candidates, Outreach, Reports). Deliberately just a
 * placeholder — we don't build ahead of the mockups being taken on. Reports is here because its
 * previous build was removed wholesale ahead of a new backend, not because it was never started.
 */
export function ProjectPlaceholderPage({ title, icon }: { title: string; icon: keyof typeof ICONS }) {
  return (
    <EmptyState
      icon={<Icon d={ICONS[icon]} size={22} />}
      title={title}
      body="This part of the mandate isn't built yet — it arrives in a later phase."
    />
  );
}
