package dev.darkspirit69.tpaui.update;

import java.math.BigInteger;

/** Compares three-part numeric release versions such as {@code 1.2.3}. */
public final class VersionComparator {
    private static final String VERSION_PATTERN = "[0-9]+\\.[0-9]+\\.[0-9]+";

    /**
     * Returns whether {@code candidate} is a newer release than {@code current}.
     * A leading {@code v} is accepted; pre-release labels are deliberately ignored.
     */
    public boolean isNewer(String candidate, String current) {
        String normalizedCandidate = normalize(candidate);
        String normalizedCurrent = normalize(current);
        if (normalizedCandidate == null || normalizedCurrent == null) {
            return false;
        }

        String[] candidateParts = normalizedCandidate.split("\\.");
        String[] currentParts = normalizedCurrent.split("\\.");
        for (int index = 0; index < candidateParts.length; index++) {
            BigInteger candidatePart = new BigInteger(candidateParts[index]);
            BigInteger currentPart = new BigInteger(currentParts[index]);
            int comparison = candidatePart.compareTo(currentPart);
            if (comparison != 0) {
                return comparison > 0;
            }
        }
        return false;
    }

    /** Returns a normalized version, or {@code null} when the value is not x.y.z. */
    public String normalize(String version) {
        if (version == null) {
            return null;
        }

        String normalized = version.trim();
        if (normalized.startsWith("v") || normalized.startsWith("V")) {
            normalized = normalized.substring(1);
        }
        return normalized.matches(VERSION_PATTERN) ? normalized : null;
    }
}
