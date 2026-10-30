import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type { CandidateTag, PersonTags } from "../../api/types";
import { askOrThrow } from "../lib/askOrThrow";

const CATALOG_KEY = ["extension", "candidateTags"] as const;
const personTagsKey = (personId: string) => ["extension", "personTags", personId] as const;

/** The workspace's tag catalog, and a new tag added to it by typing its name. */
export function useTagCatalog() {
  const queryClient = useQueryClient();
  const catalog = useQuery<CandidateTag[]>({
    queryKey: CATALOG_KEY,
    queryFn: () => askOrThrow({ kind: "listCandidateTags" }),
    staleTime: 60_000,
  });
  const create = useMutation<CandidateTag, Error, string>({
    mutationFn: (label) => askOrThrow({ kind: "createCandidateTag", label }),
    onSuccess: (tag) => queryClient.setQueryData<CandidateTag[]>(CATALOG_KEY, (held) => [...(held ?? []), tag]),
  });
  return { tags: catalog.data ?? [], isLoading: catalog.isPending, create };
}

/** The tags a filed person holds — the person's, on every position — and adding or taking one off. */
export function usePersonTags(personId: string) {
  const queryClient = useQueryClient();
  const held = useQuery<PersonTags>({
    queryKey: personTagsKey(personId),
    queryFn: () => askOrThrow({ kind: "readPersonTags", personId }),
  });
  const toggle = useMutation<PersonTags, Error, { tagId: string; on: boolean }>({
    mutationFn: ({ tagId, on }) => askOrThrow({ kind: on ? "tagPerson" : "untagPerson", personId, tagId }),
    onSuccess: (tags) => queryClient.setQueryData(personTagsKey(personId), tags),
  });
  return { tagIds: held.data?.tagIds ?? [], isLoading: held.isPending, toggle };
}
