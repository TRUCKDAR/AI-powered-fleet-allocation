package escuelaing.edu.co.truckdar.fleet_allocation.service.algorithm;

import escuelaing.edu.co.truckdar.fleet_allocation.dto.client.AvailableTruckDto;
import escuelaing.edu.co.truckdar.fleet_allocation.dto.client.FreightLoadDto;
import escuelaing.edu.co.truckdar.fleet_allocation.dto.response.AllocationCandidateResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FleetScoringOptimizer {

    private final HaversineDistanceCalculator distanceCalculator;

    private static final double WEIGHT_PROXIMITY = 0.50;
    private static final double WEIGHT_CAPACITY = 0.30;
    private static final double WEIGHT_RATING = 0.20;
    private static final double MAX_PROXIMITY_RADIUS_KM = 300.0;

    public AllocationCandidateResponse evaluateTruck(AvailableTruckDto truck, FreightLoadDto load) {
        if (truck.getCapacityTons() == null || truck.getCapacityTons() < load.getWeightTons()) {
            return AllocationCandidateResponse.builder()
                    .vehiclePlate(truck.getPlate())
                    .driverId(truck.getDriverId())
                    .driverName(truck.getDriverName())
                    .truckType(truck.getTruckType())
                    .eligible(false)
                    .score(0.0)
                    .reason("Capacidad insuficiente (" + (truck.getCapacityTons() != null ? truck.getCapacityTons() : 0.0) + " Tn < " + load.getWeightTons() + " Tn requeridas)")
                    .build();
        }

        if (load.getRequiredTruckType() != null && !load.getRequiredTruckType().equalsIgnoreCase(truck.getTruckType())) {
            return AllocationCandidateResponse.builder()
                    .vehiclePlate(truck.getPlate())
                    .driverId(truck.getDriverId())
                    .driverName(truck.getDriverName())
                    .truckType(truck.getTruckType())
                    .eligible(false)
                    .score(0.0)
                    .reason("Tipo de vehículo no coincide (" + truck.getTruckType() + " != " + load.getRequiredTruckType() + ")")
                    .build();
        }

        double distanceKm = distanceCalculator.calculateDistanceKm(
                truck.getCurrentLatitude(), truck.getCurrentLongitude(),
                load.getOriginLatitude(), load.getOriginLongitude()
        );

        double proximityScore = Math.max(0.0, 1.0 - (distanceKm / MAX_PROXIMITY_RADIUS_KM));
        double capacityEfficiency = load.getWeightTons() / truck.getCapacityTons();
        double ratingFactor = (truck.getRating() != null ? truck.getRating() : 4.0) / 5.0;

        double finalScore = (proximityScore * WEIGHT_PROXIMITY)
                + (capacityEfficiency * WEIGHT_CAPACITY)
                + (ratingFactor * WEIGHT_RATING);

        double roundedScore = Math.round(finalScore * 1000.0) / 10.0;
        double roundedUtilization = Math.round(capacityEfficiency * 1000.0) / 10.0;

        return AllocationCandidateResponse.builder()
                .vehiclePlate(truck.getPlate())
                .driverId(truck.getDriverId())
                .driverName(truck.getDriverName())
                .truckType(truck.getTruckType())
                .deadheadDistanceKm(distanceKm)
                .capacityUtilizationPercent(roundedUtilization)
                .score(roundedScore)
                .eligible(true)
                .reason("Candidato idóneo evaluado por modelo heurístico multicriterio")
                .build();
    }
}