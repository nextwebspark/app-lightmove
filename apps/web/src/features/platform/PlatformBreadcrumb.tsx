import { Link } from "react-router-dom";
import { CRUMB_HERE, CRUMB_SEPARATOR } from "../../components/layout/Topbar";
import { AppIcon } from "../../components/ui";

/** The platform console's header: the product's own mark where every other screen has the workspace's menu. */
export function PlatformBreadcrumb({
  section,
  page = null,
}: {
  section: { label: string; to: string } | null;
  /** The page open inside the section, such as one prompt. */
  page?: string | null;
}) {
  return (
    <div className="flex min-w-0 items-center gap-2.5">
      <AppIcon className="h-6" />
      <span className="whitespace-nowrap text-sm font-semibold text-u-text">Uncava platform</span>
      <span
        title="Only Uncava staff reach this area"
        className="flex-none whitespace-nowrap rounded-full bg-u-accent-tint px-2 py-0.5 type-tag text-u-accent"
      >
        Staff only
      </span>
      {section && (
        <>
          {CRUMB_SEPARATOR}
          {page ? (
            <>
              <Link to={section.to} className="whitespace-nowrap text-sm font-medium text-u-text2 hover:text-u-text">
                {section.label}
              </Link>
              {CRUMB_SEPARATOR}
              <span className={CRUMB_HERE}>{page}</span>
            </>
          ) : (
            <span className={CRUMB_HERE}>{section.label}</span>
          )}
        </>
      )}
    </div>
  );
}
