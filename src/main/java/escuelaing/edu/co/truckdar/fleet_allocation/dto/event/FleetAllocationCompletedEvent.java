package escuelaing.edu.co.truckdar.fleet_allocation.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FleetAllocationCompletedEvent {
    private Long decisionId;
    private Long loadId;
    private String driverId;
    private String driverName;
    private String vehiclePlate;
    private Double matchingScore;
    private Double deadheadDistanceKm;
    private String status;
    private LocalDateTime timestamp;
}