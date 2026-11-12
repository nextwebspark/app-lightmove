package app.lightmove.api.account.dto;

import app.lightmove.api.account.constant.AccountProof;
import java.util.List;

/** What deleting the caller's account would do, and what it would ask of them first. */
public record AccountDeletionPreviewResponse(
        /** Every workspace they would leave. */
        List<AccountWorkspace> workspaces,
        /** Workspaces they are the only admin of: deletion is refused until each has another. */
        List<AccountWorkspace> soleAdminOf,
        /** Live positions they are the only lead of: deletion is refused until each has another. */
        List<HandoverPosition> soleLeadOf,
        AccountProof proof,
        /** A password account with two-factor on gives a code as well. */
        boolean codeRequired,
        /** For {@link AccountProof#RECENT_SIGN_IN}: whether the session was signed in recently enough already. */
        boolean signedInRecently,
        /** The providers the account signs in with, lower-cased registration ids, to offer "sign in again" with. */
        List<String> signInProviders
) {}
