package app.lightmove.api.project.dto;

import app.lightmove.api.project.constant.ClientRepStatus;

/** What the Clients table's contact avatars render — a name to initial and a status to tint by. */
public record RepAvatar(String fullName, ClientRepStatus status) {}
