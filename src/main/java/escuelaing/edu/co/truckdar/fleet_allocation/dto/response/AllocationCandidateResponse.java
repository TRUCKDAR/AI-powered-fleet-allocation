package escuelaing.edu.co.truckdar.fleet_allocation.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AllocationCandidateResponse {
    private String vehiclePlate;
    private String driverId;
    private String driverName;
    private String truckType;
    private Double deadheadDistanceKm;
    private Double capacityUtilizationPercent;
    private Double score;
    private Boolean eligible;
    private String reason;
}