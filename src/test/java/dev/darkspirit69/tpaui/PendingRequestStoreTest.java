package dev.darkspirit69.tpaui;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PendingRequestStoreTest {
    private final PendingRequestStore store = new PendingRequestStore();
    private final UUID firstPlayer = UUID.randomUUID();
    private final UUID secondPlayer = UUID.randomUUID();
    private final UUID thirdPlayer = UUID.randomUUID();

    @Test
    void indexesAndRemovesRequestsFromBothPlayers() {
        TeleportRequest request = request(firstPlayer, secondPlayer, "Alex", 10L);

        assertTrue(store.add(request));
        assertTrue(store.isPending(request));
        assertEquals(1, store.outgoingCount(firstPlayer));
        assertSame(request, store.outgoingFor(firstPlayer).get(0));
        assertSame(request, store.incomingFor(secondPlayer).get(0));

        assertTrue(store.remove(request));
        assertFalse(store.isPending(request));
        assertEquals(0, store.outgoingCount(firstPlayer));
        assertTrue(store.incomingFor(secondPlayer).isEmpty());
    }

    @Test
    void doesNotReplaceAnExistingRequestForTheSameTarget() {
        TeleportRequest first = request(firstPlayer, secondPlayer, "Alex", 10L);
        TeleportRequest duplicate = request(firstPlayer, secondPlayer, "Alex", 20L);

        assertTrue(store.add(first));
        assertFalse(store.add(duplicate));
        assertTrue(store.isPending(first));
        assertFalse(store.isPending(duplicate));
        assertEquals(1, store.outgoingCount(firstPlayer));
    }

    @Test
    void findsTheNamedOrNewestIncomingRequest() {
        TeleportRequest older = request(firstPlayer, thirdPlayer, "Alex", 10L);
        TeleportRequest newer = request(secondPlayer, thirdPlayer, "Sam", 20L);
        store.add(older);
        store.add(newer);

        assertSame(newer, store.findIncoming(thirdPlayer, null));
        assertSame(older, store.findIncoming(thirdPlayer, "aLeX"));
        assertNull(store.findIncoming(thirdPlayer, "missing"));
    }

    @Test
    void clearRemovesEveryRequest() {
        store.add(request(firstPlayer, secondPlayer, "Alex", 10L));
        store.add(request(secondPlayer, thirdPlayer, "Sam", 20L));

        store.clear();

        assertTrue(store.outgoingFor(firstPlayer).isEmpty());
        assertTrue(store.incomingFor(secondPlayer).isEmpty());
        assertNull(store.findIncoming(thirdPlayer, null));
    }

    private TeleportRequest request(UUID requesterId, UUID targetId, String name, long createdAt) {
        return new TeleportRequest(requesterId, targetId, name, "Target", RequestMode.TPA, createdAt);
    }
}
