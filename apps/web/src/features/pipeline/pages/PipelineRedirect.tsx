import { Navigate, useParams, useSearchParams } from "react-router-dom";
import { positionPipelinePath } from "../lib/pipelinePath";

/** The retired Pipeline page's address: links in old emails and bookmarks land on In universe's Pipeline view. */
export default function PipelineRedirect() {
  const { projectId } = useParams();
  const [searchParams] = useSearchParams();
  return <Navigate to={positionPipelinePath(projectId!, searchParams)} replace />;
}
