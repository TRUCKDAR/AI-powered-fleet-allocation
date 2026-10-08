package escuelaing.edu.co.truckdar.fleet_allocation.dto.request;

import escuelaing.edu.co.truckdar.fleet_allocation.dto.client.AvailableTruckDto;
import escuelaing.edu.co.truckdar.fleet_allocation.dto.client.FreightLoadDto;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManualAllocationRequest {

    @NotNull(message = "Los datos del flete son obligatorios")
    private FreightLoadDto load;

    @NotNull(message = "La lista de vehículos candidatos es obligatoria")
    private List<AvailableTruckDto> candidateTrucks;
}