package com.smartseason.attendance.clockin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The rules clock-ins are judged by. The defaults are the strict ones; a farm that cannot yet draw its
 * fences can relax {@code requireGeofence} so an outside position is flagged for a supervisor instead of refused.
 */
@Configuration
public class ClockInConfig {

    @Bean
    ClockInPolicy clockInPolicy(
            @Value("${smartseason.clockin.min-biometric-score:0.80}") double minBiometricScore,
            @Value("${smartseason.clockin.max-accuracy-m:100.0}") double maxAccuracyM,
            @Value("${smartseason.clockin.geofence-tolerance-m:50.0}") double geofenceToleranceM,
            @Value("${smartseason.clockin.reject-mocked-location:true}") boolean rejectMockedLocation,
            @Value("${smartseason.clockin.require-geofence:true}") boolean requireGeofence) {
        return new ClockInPolicy(minBiometricScore, maxAccuracyM, geofenceToleranceM, rejectMockedLocation, requireGeofence);
    }

    @Bean
    GeofenceEvaluator geofenceEvaluator(ClockInPolicy policy) {
        return new GeofenceEvaluator(policy);
    }
}
