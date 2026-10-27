import { PageHeader } from "../../../components/layout/PageHeader";
import { MyTasksCard } from "../components/MyTasksCard";

/** My tasks on a screen of its own: the phone's Tasks tab, where the card would sit under the positions list. */
export function MyTasksPage() {
  return (
    <>
      <PageHeader title="My tasks" subtitle="Across every position you work on" />
      <MyTasksCard page />
    </>
  );
}
