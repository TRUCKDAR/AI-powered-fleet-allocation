package escuelaing.edu.co.truckdar.fleet_allocation.dto.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FreightLoadDto {
    private Long id;
    private String cargoDescription;
    private Double weightTons;
    private String requiredTruckType;
    private String originCity;
    private Double originLatitude;
    private Double originLongitude;
    private String destinationCity;
    private BigDecimal offeredBudget;
    private String status;
}