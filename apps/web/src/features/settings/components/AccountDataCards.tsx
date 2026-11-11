import { useState } from "react";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button, useToast } from "../../../components/ui";
import { messageFor } from "../../../lib/errorCodes";
import * as authApi from "../../auth/api/authApi";
import { DeleteAccountModal } from "./DeleteAccountModal";

/**
 * Settings → Profile's foot (`Settings.dc.html`, `profile`): the account's own copy, and its deletion. What the person
 * added inside a workspace is that workspace's, and its admin exports it from Data & retention.
 */
export function AccountDataCards() {
  const toast = useToast();
  const [downloading, setDownloading] = useState(false);
  const [deleting, setDeleting] = useState(false);

  const handleDownload = async () => {
    setDownloading(true);
    try {
      await authApi.saveAccountData();
    } catch (error) {
      toast.error(messageFor(error));
    } finally {
      setDownloading(false);
    }
  };

  return (
    <>
      <section
        aria-label="Download my data"
        className="mt-4 rounded-u-card border border-u-border bg-u-raised p-5"
      >
        <div className="flex flex-col gap-3 md:flex-row md:items-center">
          <div className="min-w-0 flex-1">
            <h2 className="text-body font-semibold">Download my data</h2>
            <p className="mt-1 max-w-[520px] font-mono text-meta text-u-text3">
              Your account as a JSON file: your profile, the workspaces you belong to, sign-ins, API keys and
              connected AI apps. What you added inside a workspace belongs to that workspace; its admin can export it.
            </p>
          </div>
          <Button variant="secondary" loading={downloading} onClick={() => void handleDownload()}>
            <Icon d={ICONS.download} size={14} />
            Download
          </Button>
        </div>
      </section>

      <section
        aria-label="Delete my account"
        className="mt-4 rounded-u-card border border-u-offlimits bg-u-offlimits-tint p-5"
      >
        <div className="flex flex-col gap-3 md:flex-row md:items-center">
          <div className="flex-1">
            <h2 className="text-body font-semibold text-u-offlimits">Delete my account</h2>
            <p className="mt-1 font-mono text-meta text-u-text3">
              You leave every workspace and your sign-in is deleted. This cannot be undone.
            </p>
          </div>
          <Button
            variant="secondary"
            className="border-u-offlimits text-u-offlimits hover:border-u-offlimits hover:bg-u-offlimits hover:text-white"
            onClick={() => setDeleting(true)}
          >
            Delete…
          </Button>
        </div>
      </section>

      {deleting && <DeleteAccountModal onClose={() => setDeleting(false)} />}
    </>
  );
}
