package app.uncava.api.project.dto;

import app.uncava.api.project.constant.ClientRepStatus;

/** What the Clients table's contact avatars render — a name to initial and a status to tint by. */
public record RepAvatar(String fullName, ClientRepStatus status) {}
