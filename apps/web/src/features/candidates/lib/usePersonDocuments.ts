import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useToast } from "../../../components/ui";
import { messageFor } from "../../../lib/errorCodes";
import { saveBlob } from "../../../lib/saveBlob";
import * as documentsApi from "../api/documentsApi";
import type { DocumentScope } from "../api/documentsApi";
import type { PersonDocument, PersonDocumentVersion, UpdatePersonDocumentPayload } from "../api/types";

/** A person's documents through one door, and every change to them; each change also adds a timeline line. */
export function usePersonDocuments(scope: DocumentScope, enabled = true) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const documents = useQuery({
    queryKey: documentsApi.DOCUMENTS_KEY(scope),
    queryFn: ({ signal }) => documentsApi.listDocuments(scope, signal),
    enabled,
  });

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: documentsApi.DOCUMENTS_KEY(scope) });
    void queryClient.invalidateQueries({ queryKey: documentsApi.DOCUMENT_TIMELINE_KEY(scope) });
  };

  const updating = useMutation({
    mutationFn: ({ documentId, patch }: { documentId: string; patch: UpdatePersonDocumentPayload }) =>
      documentsApi.updateDocument(scope, documentId, patch),
    onSuccess: refresh,
    onError: (error) => toast(messageFor(error)),
  });
  const removing = useMutation({
    mutationFn: (documentId: string) => documentsApi.removeDocument(scope, documentId),
    onSuccess: () => {
      toast("Document deleted — the timeline keeps a line, without its name");
      refresh();
    },
    onError: (error) => toast(messageFor(error)),
  });
  const removingVersion = useMutation({
    mutationFn: ({ documentId, versionId }: { documentId: string; versionId: string }) =>
      documentsApi.removeVersion(scope, documentId, versionId),
    onSuccess: refresh,
    onError: (error) => toast(messageFor(error)),
  });

  const download = async (document: PersonDocument, version: PersonDocumentVersion) => {
    try {
      saveBlob(await documentsApi.documentContent(scope, document.id, version.id, false), version.fileName);
    } catch (error) {
      toast(messageFor(error));
    }
  };

  return { documents, refresh, updating, removing, removingVersion, download };
}

export type PersonDocuments = ReturnType<typeof usePersonDocuments>;
