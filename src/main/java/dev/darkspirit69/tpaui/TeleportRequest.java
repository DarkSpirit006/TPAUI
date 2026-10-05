package dev.darkspirit69.tpaui;

import java.util.UUID;

/** One pending standalone request, indexed from both players' request maps. */
final class TeleportRequest {
    final UUID requesterId;
    final UUID targetId;
    final String requesterName;
    final String targetName;
    final RequestMode mode;
    final long createdAtMillis;

    TeleportRequest(
            UUID requesterId,
            UUID targetId,
            String requesterName,
            String targetName,
            RequestMode mode,
            long createdAtMillis) {
        this.requesterId = requesterId;
        this.targetId = targetId;
        this.requesterName = requesterName;
        this.targetName = targetName;
        this.mode = mode;
        this.createdAtMillis = createdAtMillis;
    }
}
