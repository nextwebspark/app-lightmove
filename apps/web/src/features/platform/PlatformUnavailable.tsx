import { Link } from "react-router-dom";
import { Icon, ICONS } from "../../components/layout/Icon";
import { Button, EmptyState } from "../../components/ui";

/** Drawn in place of the console when its chunk will not load. It stands outside every shell, so it needs nothing the chunk carries. */
export function PlatformUnavailable() {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center">
      <EmptyState
        icon={<Icon d={ICONS.shield} size={24} />}
        title="Platform couldn't load"
        body="Reload the page to try again."
      >
        <div className="flex flex-wrap items-center justify-center gap-3">
          <Button onClick={() => window.location.reload()}>Reload</Button>
          <Link to="/" className="text-body font-medium text-u-accent hover:underline">
            Back to workspace
          </Link>
        </div>
      </EmptyState>
    </div>
  );
}
