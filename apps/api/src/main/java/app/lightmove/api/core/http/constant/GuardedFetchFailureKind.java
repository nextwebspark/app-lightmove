package app.lightmove.api.core.http.constant;

/** Why a guarded fetch got no answer to read. */
public enum GuardedFetchFailureKind {
    /** The host resolves to an address on this machine or a private network: never dialled. */
    PRIVATE_ADDRESS,
    /** The deadline for the whole exchange passed. */
    TIMEOUT,
    /** The body ran past the caller's cap, declared or streamed. */
    TOO_LARGE,
    /** Anything else between us and the host. */
    UNREACHABLE
}
