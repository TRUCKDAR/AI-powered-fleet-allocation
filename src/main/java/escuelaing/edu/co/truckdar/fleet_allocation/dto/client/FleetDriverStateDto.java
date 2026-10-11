package escuelaing.edu.co.truckdar.fleet_allocation.dto.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Contrato de GET /api/v1/fleets-drivers/active en fleets-drivers (DriverVehicleStateResponse).
 * Solo se mapean los campos que usa el motor de scoring.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FleetDriverStateDto {

    private String driverId;
    private String driverName;
    private Double rating;
    private String vehiclePlate;
    private String vehicleType;
    private Double netCargoWeightTons;
    private boolean restRequiredAlert;
    private Double currentLatitude;
    private Double currentLongitude;
    private String status;

    public boolean isAvailableForAllocation() {
        return "ACTIVO".equalsIgnoreCase(status) && !restRequiredAlert;
    }

    public AvailableTruckDto toAvailableTruck() {
        return AvailableTruckDto.builder()
                .driverId(driverId)
                .driverName(driverName)
                .rating(rating)
                .plate(vehiclePlate)
                .truckType(vehicleType)
                .capacityTons(netCargoWeightTons)
                .currentLatitude(currentLatitude)
                .currentLongitude(currentLongitude)
                .isAvailable(isAvailableForAllocation())
                .build();
    }
}
