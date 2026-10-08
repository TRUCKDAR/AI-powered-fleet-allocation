package escuelaing.edu.co.truckdar.fleet_allocation.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FreightLoadPublishedEvent {
    private Long loadId;
    private String generatorCompanyId;
    private String cargoDescription;
    private Double weightTons;
    private String requiredTruckType;
    private String originCity;
    private String destinationCity;
    private BigDecimal offeredBudget;
    private String eventType;
    private LocalDateTime timestamp;
}