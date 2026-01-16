package hu.blackbelt.judo.tatami.test.profiler;

/**
 * Represents a CPU hotspot identified from a profile.
 *
 * @param method     The fully qualified method name (leaf of the stack)
 * @param samples    The number of samples where this method was on top of the stack
 * @param percentage The percentage of total samples
 */
public record Hotspot(String method, long samples, double percentage) {

    /**
     * Returns a formatted string representation suitable for display.
     */
    public String toDisplayString() {
        return String.format("%5.1f%% (%d samples) %s", percentage, samples, method);
    }
}
