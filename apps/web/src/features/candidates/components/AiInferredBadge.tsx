import { Icon, ICONS } from "../../../components/layout/Icon";

/**
 * Marks a value the AI proposed that nobody has made their own yet — a background field in
 * `Candidate.aiInferredFields`, or an assessment score or section in the scorecard's AI draft. The same sparkle,
 * size and ink the brief's {@link ProvenanceMarker} wears, so AI-filled reads alike on every screen.
 */
export function AiInferredBadge({
  label = "AI-suggested",
  title = "AI-suggested — saving the Background section confirms it",
}: {
  label?: string;
  title?: string;
}) {
  return (
    <span role="img" aria-label={label} title={title} className="grid size-4 flex-none place-items-center text-u-inferred">
      <Icon d={ICONS.sparkle} size={13} />
    </span>
  );
}
