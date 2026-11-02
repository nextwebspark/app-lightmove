package app.lightmove.api.account.dto;

import java.util.UUID;

/** A workspace the person is in, by name: the people deleting an account are its members, so the name is theirs to read. */
public record AccountWorkspace(UUID id, String name) {}
