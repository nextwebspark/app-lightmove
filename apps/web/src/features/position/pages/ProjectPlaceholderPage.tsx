import { EmptyState } from "../../../components/ui";
import { Icon, ICONS } from "../../../components/layout/Icon";

/**
 * The not-yet-built tabs of the project shell (Strategy, Sourcing, Candidates, Outreach, Reports).
 * Deliberately just a placeholder — their tables don't exist yet, and we don't build ahead of the
 * mockups being taken on.
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
