package escuelaing.edu.co.truckdar.fleet_allocation.dto.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvailableTruckDto {
    private String plate;
    private String truckType;
    private Double capacityTons;
    private String driverId;
    private String driverName;
    private Double currentLatitude;
    private Double currentLongitude;
    private Double rating;
    private Boolean isAvailable;
}