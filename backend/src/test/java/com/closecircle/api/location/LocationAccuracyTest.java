package com.closecircle.api.location;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class LocationAccuracyTest {
    @Test
    void largerUncertaintyMeansPoorerAccuracyAndBoundaryIsInclusive() {
        LocationAccuracy policy = new LocationAccuracy(100);

        assertEquals(LocationAccuracy.Status.ACCEPTABLE, policy.assess(0.0));
        assertEquals(LocationAccuracy.Status.ACCEPTABLE, policy.assess(12.5));
        assertEquals(LocationAccuracy.Status.ACCEPTABLE, policy.assess(100.0));
        assertEquals(LocationAccuracy.Status.POOR, policy.assess(Math.nextUp(100.0)));
        assertEquals(LocationAccuracy.Status.POOR, policy.assess(Double.MAX_VALUE));
    }

    @Test
    void unknownOrInvalidMeasurementsAreNeverReportedAsAcceptable() {
        LocationAccuracy policy = new LocationAccuracy(100);

        assertEquals(LocationAccuracy.Status.UNKNOWN, policy.assess(null));
        for (double invalid : new double[] {-1, -Double.MIN_VALUE, Double.NaN,
                Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertEquals(LocationAccuracy.Status.UNKNOWN, policy.assess(invalid));
        }
    }

    @Test
    void thresholdCanBeChangedWithoutChangingTheMeasurement() {
        assertEquals(LocationAccuracy.Status.POOR, new LocationAccuracy(25).assess(50.0));
        assertEquals(LocationAccuracy.Status.ACCEPTABLE, new LocationAccuracy(75).assess(50.0));
    }

    @Test
    void rejectsThresholdsThatCannotRepresentAPositiveFiniteDistance() {
        for (double invalid : new double[] {0, -0.0, -1, Double.NaN,
                Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> new LocationAccuracy(invalid));
        }
    }
}
