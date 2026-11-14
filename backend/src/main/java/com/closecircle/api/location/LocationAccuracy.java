package com.closecircle.api.location;

/** Assesses reported uncertainty in metres; it does not determine location visibility. */
public final class LocationAccuracy {
    public enum Status { ACCEPTABLE, POOR, UNKNOWN }

    private final double poorAccuracyThresholdM;

    public LocationAccuracy(double poorAccuracyThresholdM) {
        if (!Double.isFinite(poorAccuracyThresholdM) || poorAccuracyThresholdM <= 0) {
            throw new IllegalArgumentException("Accuracy threshold must be finite and positive");
        }
        this.poorAccuracyThresholdM = poorAccuracyThresholdM;
    }

    public Status assess(Double accuracyM) {
        if (accuracyM == null || !Double.isFinite(accuracyM) || accuracyM < 0) {
            return Status.UNKNOWN;
        }
        return accuracyM > poorAccuracyThresholdM ? Status.POOR : Status.ACCEPTABLE;
    }
}
