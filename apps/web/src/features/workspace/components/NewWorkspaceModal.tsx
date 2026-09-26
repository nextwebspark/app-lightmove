import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { Modal } from "../../../components/ui";
import { useAuth } from "../../auth/AuthProvider";
import * as workspaceApi from "../api/workspaceApi";
import { InviteTeamForm } from "./InviteTeamForm";
import { OrganisationForm } from "./OrganisationForm";

/**
 * A further workspace, founded from inside the app — the wizard's two last steps in a dialog. The
 * organisation stage creates it and <b>switches the session into it before the invite stage</b>,
 * because invitations address the workspace the session is in: sent from the old one they would land
 * on the wrong roster. Finishing, or skipping, opens the new workspace.
 */
export function NewWorkspaceModal({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { switchWorkspace } = useAuth();
  const navigate = useNavigate();
  const [stage, setStage] = useState<"organisation" | "invite">("organisation");

  const close = () => {
    onClose();
    setStage("organisation");
  };

  const finish = () => {
    close();
    navigate("/", { replace: true });
  };

  return (
    <Modal open={open} onClose={stage === "organisation" ? close : finish} title="New workspace">
      {stage === "organisation" ? (
        <OrganisationForm
          editing={null}
          subtitle="A separate workspace with its own positions and team — you'll be its admin"
          submit={workspaceApi.createWorkspace}
          onDone={async (created) => {
            if (!created.workspace) {
              throw new Error("The server created no workspace");
            }
            await switchWorkspace(created.workspace.id);
            setStage("invite");
          }}
        />
      ) : (
        <InviteTeamForm
          subtitle="Optional — invite people later from Members"
          submit={workspaceApi.invite}
          onDone={finish}
          onSkip={finish}
        />
      )}
    </Modal>
  );
}
