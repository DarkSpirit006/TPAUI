package dev.darkspirit69.tpaui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Keeps each standalone request indexed from both its sender and recipient. */
final class PendingRequestStore {
    private final Map<UUID, Map<UUID, TeleportRequest>> outgoingByPlayer =
            new HashMap<UUID, Map<UUID, TeleportRequest>>();
    private final Map<UUID, Map<UUID, TeleportRequest>> incomingByPlayer =
            new HashMap<UUID, Map<UUID, TeleportRequest>>();

    boolean add(TeleportRequest request) {
        if (hasOutgoing(request.requesterId, request.targetId)) {
            return false;
        }

        mapFor(outgoingByPlayer, request.requesterId).put(request.targetId, request);
        mapFor(incomingByPlayer, request.targetId).put(request.requesterId, request);
        return true;
    }

    boolean hasOutgoing(UUID requesterId, UUID targetId) {
        Map<UUID, TeleportRequest> outgoing = outgoingByPlayer.get(requesterId);
        return outgoing != null && outgoing.containsKey(targetId);
    }

    int outgoingCount(UUID requesterId) {
        Map<UUID, TeleportRequest> outgoing = outgoingByPlayer.get(requesterId);
        return outgoing == null ? 0 : outgoing.size();
    }

    List<TeleportRequest> outgoingFor(UUID requesterId) {
        return copyValues(outgoingByPlayer.get(requesterId));
    }

    List<TeleportRequest> incomingFor(UUID targetId) {
        return copyValues(incomingByPlayer.get(targetId));
    }

    TeleportRequest findIncoming(UUID targetId, String requesterName) {
        Map<UUID, TeleportRequest> incoming = incomingByPlayer.get(targetId);
        if (incoming == null || incoming.isEmpty()) {
            return null;
        }

        TeleportRequest newest = null;
        for (TeleportRequest request : incoming.values()) {
            if (requesterName != null && request.requesterName.equalsIgnoreCase(requesterName)) {
                return request;
            }
            if (requesterName == null
                    && (newest == null || request.createdAtMillis > newest.createdAtMillis)) {
                newest = request;
            }
        }
        return requesterName == null ? newest : null;
    }

    boolean isPending(TeleportRequest request) {
        Map<UUID, TeleportRequest> outgoing = outgoingByPlayer.get(request.requesterId);
        Map<UUID, TeleportRequest> incoming = incomingByPlayer.get(request.targetId);
        return outgoing != null && outgoing.get(request.targetId) == request
                && incoming != null && incoming.get(request.requesterId) == request;
    }

    boolean remove(TeleportRequest request) {
        boolean removed = removeIfCurrent(outgoingByPlayer, request.requesterId, request.targetId, request);
        removed |= removeIfCurrent(incomingByPlayer, request.targetId, request.requesterId, request);
        return removed;
    }

    void clear() {
        outgoingByPlayer.clear();
        incomingByPlayer.clear();
    }

    private Map<UUID, TeleportRequest> mapFor(
            Map<UUID, Map<UUID, TeleportRequest>> requests,
            UUID playerId) {
        Map<UUID, TeleportRequest> byPlayer = requests.get(playerId);
        if (byPlayer == null) {
            byPlayer = new HashMap<UUID, TeleportRequest>();
            requests.put(playerId, byPlayer);
        }
        return byPlayer;
    }

    private List<TeleportRequest> copyValues(Map<UUID, TeleportRequest> requests) {
        return requests == null || requests.isEmpty()
                ? Collections.<TeleportRequest>emptyList()
                : new ArrayList<TeleportRequest>(requests.values());
    }

    private boolean removeIfCurrent(
            Map<UUID, Map<UUID, TeleportRequest>> requests,
            UUID playerId,
            UUID otherPlayerId,
            TeleportRequest request) {
        Map<UUID, TeleportRequest> byPlayer = requests.get(playerId);
        if (byPlayer == null || byPlayer.get(otherPlayerId) != request) {
            return false;
        }

        byPlayer.remove(otherPlayerId);
        if (byPlayer.isEmpty()) {
            requests.remove(playerId);
        }
        return true;
    }
}
