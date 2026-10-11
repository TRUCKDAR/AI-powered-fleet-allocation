package escuelaing.edu.co.truckdar.fleet_allocation.service.algorithm;

import org.springframework.stereotype.Component;

@Component
public class HaversineDistanceCalculator {

    private static final double EARTH_RADIUS_KM = 6371.0;

    public double calculateDistanceKm(Double lat1, Double lon1, Double lat2, Double lon2) {
        if (lat1 == null || lon1 == null || lat2 == null || lon2 == null) {
            return Double.MAX_VALUE;
        }

        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double originLatRad = Math.toRadians(lat1);
        double destLatRad = Math.toRadians(lat2);

        double a = Math.sin(dLat / 2.0) * Math.sin(dLat / 2.0)
                + Math.sin(dLon / 2.0) * Math.sin(dLon / 2.0)
                * Math.cos(originLatRad) * Math.cos(destLatRad);

        double c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));

        return Math.round((EARTH_RADIUS_KM * c) * 100.0) / 100.0;
    }
}