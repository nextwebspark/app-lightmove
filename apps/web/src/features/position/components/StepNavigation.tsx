import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button } from "../../../components/ui";
import { POSITION_STEPS, stepIndexOf, type StepKey } from "../lib/steps";

/** Back / Next along the bottom of the form, with Publish standing in for Next on the last step. */
export function StepNavigation({
  currentStep,
  onSelectStep,
  onPublish,
  publishing,
  published,
}: {
  currentStep: StepKey;
  onSelectStep: (key: StepKey) => void;
  onPublish: () => void;
  publishing: boolean;
  published: boolean;
}) {
  const index = stepIndexOf(currentStep);
  const previous = POSITION_STEPS[index - 1];
  const next = POSITION_STEPS[index + 1];

  return (
    <div className="mt-[26px] flex items-center gap-2.5 border-t border-line-soft pt-[18px]">
      {previous && (
        <Button variant="secondary" onClick={() => onSelectStep(previous.key)}>
          Back
        </Button>
      )}
      <span className="flex-1" />
      {next ? (
        <Button onClick={() => onSelectStep(next.key)}>
          Next: {next.name}
          <Icon d={ICONS.arrowRight} size={15} />
        </Button>
      ) : (
        <Button onClick={onPublish} loading={publishing}>
          {published ? "Published" : "Publish position profile"}
        </Button>
      )}
    </div>
  );
}
