import { useQuery } from "@tanstack/react-query";
import { useCallback, useEffect, useMemo, useState } from "react";
import { PageHeader } from "../../../components/layout/PageHeader";
import { Icon, ICONS } from "../../../components/layout/Icon";
import { Button, EmptyState } from "../../../components/ui";
import { ColumnPicker, hideableColumnsOf } from "../../../components/ui/ColumnPicker";
import { PaginationBar } from "../../../components/ui/PaginationBar";
import { useColumnVisibility } from "../../../lib/useColumnVisibility";
import { EMPTY_GRID_LAYOUT, layoutColumnsOf, useGridLayout } from "../../../lib/useGridLayout";
import { useGridPaging } from "../../../lib/useGridPaging";
import { useGridSort, WORKSPACE_SCOPE } from "../../../lib/useGridSort";
import { useAuth } from "../../auth/AuthProvider";
import * as projectsApi from "../../projects/api/projectsApi";
import { isActive } from "../../projects/lib/filtering";
import * as workspaceApi from "../api/workspaceApi";
import { InviteModal } from "../components/InviteModal";
import { MembersList } from "../components/MembersList";
import {
  MEMBER_COLUMN_VISIBILITY,
  MEMBER_SORT_FIELDS,
  memberColumns,
  type MemberSortField,
} from "../lib/memberColumns";

const MEMBER_LAYOUT_COLUMNS = layoutColumnsOf(memberColumns);
const HIDEABLE_MEMBER_COLUMNS = hideableColumnsOf(memberColumns);

const DEFAULT_MEMBER_SORT = { field: "name", direction: "asc" } as const;

/** The roster as colleagues see it: who's here, their role, and how many mandates they carry. */
export function TeamPage() {
  const { user } = useAuth();
  const isAdmin = user?.workspace?.roles.includes("ADMIN") ?? false;
  const [inviteOpen, setInviteOpen] = useState(false);
  const [sort, setSort] = useGridSort<MemberSortField>(
    "members",
    WORKSPACE_SCOPE,
    MEMBER_SORT_FIELDS,
    DEFAULT_MEMBER_SORT,
  );
  const [columnVisibility, setColumnVisibility] = useColumnVisibility(
    "members",
    WORKSPACE_SCOPE,
    MEMBER_COLUMN_VISIBILITY,
  );
  const [layout, setLayout] = useGridLayout("members", MEMBER_LAYOUT_COLUMNS);
  const paging = useGridPaging();

  const { data: members = [], isError } = useQuery({
    queryKey: workspaceApi.MEMBERS_KEY,
    queryFn: workspaceApi.members,
  });
  const { data: projects = [] } = useQuery({
    queryKey: projectsApi.PROJECTS_KEY,
    queryFn: projectsApi.projects,
  });

  // One pass over the mandates, because the column reads this per cell and its comparator twice per
  // comparison: walking every seat on each call would make a sort click quadratic in the roster.
  const activeCountByMember = useMemo(() => {
    const counts = new Map<string, number>();
    for (const project of projects) {
      if (!isActive(project)) continue;
      for (const seat of project.team) {
        counts.set(seat.memberId, (counts.get(seat.memberId) ?? 0) + 1);
      }
    }
    return counts;
  }, [projects]);
  const activeCount = useCallback(
    (memberId: string) => activeCountByMember.get(memberId) ?? 0,
    [activeCountByMember],
  );

  const { reset: resetPage, clampTo } = paging;
  useEffect(() => {
    resetPage();
  }, [resetPage, sort]);
  useEffect(() => {
    clampTo(members.length);
  }, [clampTo, members.length]);

  // A refused roster falls back to the [] default, and the header would then report "0 members" — a
  // count the caller was never allowed to read, stated as fact. Say what happened instead.
  if (isError) {
    return (
      <>
        <PageHeader title="Team" subtitle="roles apply per project" />
        <EmptyState
          icon={<Icon d={ICONS.lock} size={24} />}
          title="Couldn't load the roster"
          body="You may no longer have access to it, or the request failed. Reload the page, and ask an admin if it keeps happening."
        />
      </>
    );
  }

  return (
    <>
      <PageHeader
        title="Team"
        subtitle={`${members.length} ${members.length === 1 ? "member" : "members"} · roles apply per project`}
        action={
          isAdmin && (
            <Button
              variant="secondary"
              className="!px-3.5 !py-[7px] !text-[13px]"
              onClick={() => setInviteOpen(true)}
            >
              <Icon d={ICONS.plus} size={15} />
              Invite
            </Button>
          )
        }
      />

      <div className="mb-3.5 hidden justify-end md:flex">
        <ColumnPicker
          columns={HIDEABLE_MEMBER_COLUMNS}
          visibility={columnVisibility}
          defaults={MEMBER_COLUMN_VISIBILITY}
          onChange={setColumnVisibility}
          onResetLayout={() => setLayout(EMPTY_GRID_LAYOUT)}
        />
      </div>

      <div className="flex flex-col gap-3">
        <MembersList
          members={members}
          activeCount={activeCount}
          sort={sort}
          onSortChange={setSort}
          columnVisibility={columnVisibility}
          onColumnVisibilityChange={setColumnVisibility}
          layout={layout}
          onLayoutChange={setLayout}
          pagination={paging.pagination}
          onPaginationChange={paging.onPaginationChange}
        />
        <PaginationBar
          page={paging.page}
          size={paging.size}
          totalCount={members.length}
          onPage={paging.setPage}
          onSize={paging.setSize}
          autoHide
        />
      </div>

      {inviteOpen && <InviteModal open onClose={() => setInviteOpen(false)} />}
    </>
  );
}
