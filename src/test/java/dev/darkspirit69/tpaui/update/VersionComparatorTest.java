package dev.darkspirit69.tpaui.update;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionComparatorTest {
    private final VersionComparator comparator = new VersionComparator();

    @Test
    void comparesEachNumericPart() {
        assertTrue(comparator.isNewer("1.1.0", "1.0.9"));
        assertTrue(comparator.isNewer("2.0.0", "1.99.99"));
        assertTrue(comparator.isNewer("1.0.10", "1.0.9"));
        assertFalse(comparator.isNewer("1.0.9", "1.0.10"));
    }

    @Test
    void acceptsOptionalVersionPrefixAndOuterWhitespace() {
        assertTrue(comparator.isNewer(" v1.2.0 ", "1.1.9"));
        assertEquals("1.2.3", comparator.normalize("V1.2.3"));
    }

    @Test
    void rejectsInvalidOrIncompleteVersions() {
        assertNull(comparator.normalize(null));
        assertNull(comparator.normalize("1.2"));
        assertNull(comparator.normalize("1.2.3-rc1"));
        assertFalse(comparator.isNewer("latest", "1.0.0"));
        assertFalse(comparator.isNewer("1.0.0", "1.0.0"));
    }
}
