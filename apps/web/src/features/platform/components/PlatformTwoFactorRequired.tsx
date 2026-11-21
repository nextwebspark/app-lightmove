import { Link } from "react-router-dom";
import { buttonClassName, EmptyState } from "../../../components/ui";
import { Icon, ICONS } from "../../../components/layout/Icon";

/** What a platform screen draws for a super admin with no factor, where the deployment requires one. */
export function PlatformTwoFactorRequired() {
  return (
    <EmptyState
      icon={<Icon d={ICONS.lock} size={24} />}
      title="Set up two-factor to use the platform console"
      body="The console changes what every workspace runs on, so it asks for a second step. Turn on two-factor authentication for your account, then come back."
    >
      <Link to="/settings/security" className={buttonClassName("primary")}>
        Go to Security
      </Link>
    </EmptyState>
  );
}
