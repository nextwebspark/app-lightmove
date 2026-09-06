import { useNavigate } from "react-router-dom";
import { Icon, ICONS } from "../components/layout/Icon";
import { WorkspaceShell } from "../components/layout/WorkspaceLayout";
import { Button, EmptyState } from "../components/ui";

/**
 * Where an unknown URL and an unreadable project id both land, rendered in the shell rather than
 * bounced to My projects — the bounce left a typo, a stale bookmark, a deleted mandate and one the
 * caller is not seated on indistinguishable from one another. The URL stays as it was asked for, and
 * the copy names neither cause: from here the app cannot tell "gone" from "not yours".
 */
export function NotFoundPage({
  title = "We couldn't open that page",
  body = "The address may be mistyped, the link may be out of date, or it may point somewhere you don't have access to.",
}: {
  title?: string;
  body?: string;
}) {
  const navigate = useNavigate();

  return (
    <WorkspaceShell>
      <EmptyState icon={<Icon d={ICONS.search} size={24} />} title={title} body={body}>
        <Button variant="secondary" onClick={() => navigate("/")}>
          Go to My projects
        </Button>
      </EmptyState>
    </WorkspaceShell>
  );
}
