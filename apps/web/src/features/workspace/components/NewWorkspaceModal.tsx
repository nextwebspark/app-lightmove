import { useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Modal } from "../../../components/ui";
import { useAuth } from "../../auth/AuthProvider";
import type { CreateWorkspaceRequest, User } from "../../auth/api/types";
import * as workspaceApi from "../api/workspaceApi";
import { InviteTeamForm } from "./InviteTeamForm";
import { OrganisationForm } from "./OrganisationForm";

/**
 * A further workspace, founded from inside the app — the wizard's two last steps in a dialog. The
 * organisation stage creates it and <b>switches the session into it before the invite stage</b>,
 * because invitations address the workspace the session is in: sent from the old one they would land
 * on the wrong roster. Mounted only while open.
 */
export function NewWorkspaceModal({ onClose }: { onClose: () => void }) {
  const { switchWorkspace, reload } = useAuth();
  const navigate = useNavigate();
  const [stage, setStage] = useState<"organisation" | "invite">("organisation");
  // Once created, Continue only retries the switch: creating again would found a second workspace.
  const created = useRef<User | null>(null);

  const createOnce = async (payload: CreateWorkspaceRequest) =>
    (created.current ??= await workspaceApi.createWorkspace(payload));

  const finish = () => {
    onClose();
    navigate("/", { replace: true });
  };

  return (
    <Modal open onClose={stage === "organisation" ? onClose : finish} title="New workspace">
      {stage === "organisation" ? (
        <OrganisationForm
          editing={null}
          subtitle="A separate workspace with its own positions and team — you'll be its admin"
          submit={createOnce}
          onDone={async (answer) => {
            if (!answer.workspace) {
              throw new Error("The server created no workspace");
            }
            try {
              await switchWorkspace(answer.workspace.id);
            } catch (error) {
              await reload();
              throw error;
            }
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
