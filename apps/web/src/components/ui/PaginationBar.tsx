import { Icon, ICONS } from "../layout/Icon";

/**
 * The mockup's pagination row, wired for real: its own buttons were decorative because every row
 * fitted on one page of seeded data. The universe is 71,822 companies, so they have to work.
 *
 * <p>An undefined {@code totalCount} means "not known yet" and is not the same as zero: rendering
 * "0 results" against a page that is still loading states as fact that nothing matched, next to a
 * table that is showing a skeleton.
 */
export function PaginationBar({
  page,
  size,
  totalCount,
  onPage,
}: {
  page: number;
  size: number;
  totalCount: number | undefined;
  onPage: (page: number) => void;
}) {
  const known = totalCount ?? 0;
  const lastPage = Math.max(0, Math.ceil(known / size) - 1);

  const countLabel = () => {
    if (totalCount === undefined) return "";
    if (totalCount === 0) return "0 results";
    const first = page * size + 1;
    const last = Math.min((page + 1) * size, totalCount);
    return `${first} - ${last} of ${totalCount.toLocaleString()}`;
  };

  return (
    <div className="flex flex-none flex-wrap items-center gap-x-4 gap-y-2">
      <PageButton
        label="Previous page"
        path={ICONS.back}
        disabled={page === 0}
        onClick={() => onPage(page - 1)}
      />
      <span className="inline-flex items-center gap-2 rounded-[6px] border border-line-soft px-3 py-1.5 font-sans text-[13px] font-semibold text-text">
        {page + 1}
      </span>
      <PageButton
        label="Next page"
        path={ICONS.chevronRight}
        disabled={page >= lastPage}
        onClick={() => onPage(page + 1)}
      />
      <span className="font-sans text-[13px] text-text3">{countLabel()}</span>
    </div>
  );
}

function PageButton({
  label,
  path,
  disabled,
  onClick,
}: {
  label: string;
  path: string;
  disabled: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      aria-label={label}
      disabled={disabled}
      onClick={onClick}
      className="grid size-10 place-items-center rounded-[6px] border border-line-soft lg:size-8 text-text3 transition hover:border-line hover:text-text disabled:opacity-40 disabled:hover:border-line-soft disabled:hover:text-text3"
    >
      <Icon d={path} size={14} />
    </button>
  );
}
