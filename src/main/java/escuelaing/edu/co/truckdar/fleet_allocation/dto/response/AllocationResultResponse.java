package escuelaing.edu.co.truckdar.fleet_allocation.dto.response;

import escuelaing.edu.co.truckdar.fleet_allocation.model.AllocationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AllocationResultResponse {
    private Long decisionId;
    private Long loadId;
    private AllocationStatus status;
    private String selectedDriverId;
    private String selectedDriverName;
    private String selectedVehiclePlate;
    private Double matchingScore;
    private Double deadheadDistanceKm;
    private String reasonDetails;
    private List<AllocationCandidateResponse> evaluatedCandidates;
    private LocalDateTime executedAt;
}