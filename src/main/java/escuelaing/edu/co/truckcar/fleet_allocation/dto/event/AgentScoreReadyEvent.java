package escuelaing.edu.co.truckcar.fleet_allocation.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentScoreReadyEvent {
    private Long loadId;
    private Long decisionId;
    private String driverId;
    private String driverName;
    private String vehiclePlate;
    private Double score;
    private Double deadheadDistanceKm;
    private String explanation;
    private String trigger;
}
