/**
 * The HTTP contract for authentication.
 *
 * <p>The validation messages are what the user reads under the field, so they read as instructions
 * ("Use at least 8 characters"), mirroring the frontend's Zod schema — the client validates for a fast
 * response, the server because the client can be bypassed with one curl command.
 */
package app.lightmove.api.core.security.dto;
