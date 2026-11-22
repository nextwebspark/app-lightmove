import { useState } from "react";
import { Link } from "react-router-dom";
import { Button, useToast } from "../../../components/ui";
import { messageFor } from "../../../lib/errorCodes";
import { useAuth } from "../../auth/AuthProvider";
import * as startPageApi from "../api/startPageApi";

/** On Home when the saved start page is no longer the member's to open, so Home opened in its place. */
export function StartPageNotice() {
  const { user, reload } = useAuth();
  const toast = useToast();
  const [saving, setSaving] = useState(false);
  if (!user?.startPage || user.startPage.available) return null;

  const handleOpenOnHome = async () => {
    setSaving(true);
    try {
      await startPageApi.saveStartPage({ kind: "HOME" });
      await reload();
    } catch (error) {
      toast.error(messageFor(error));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div role="status" className="mb-4 flex flex-wrap items-center gap-3 rounded-u-chip border border-u-border bg-u-raised px-3.5 py-2.5">
      <p className="min-w-[220px] flex-1 text-note text-u-text2">
        Your start page is no longer available to you, so Uncava opened Home.
      </p>
      <div className="flex gap-2">
        <Link
          to="/settings/profile#start-page"
          className="flex items-center rounded-u-control border border-u-border-strong px-3 py-1.5 text-note font-medium text-u-text2 hover:text-u-text"
        >
          Choose another
        </Link>
        <Button variant="ghost" size="sm" loading={saving} onClick={() => void handleOpenOnHome()}>
          Open on Home
        </Button>
      </div>
    </div>
  );
}
