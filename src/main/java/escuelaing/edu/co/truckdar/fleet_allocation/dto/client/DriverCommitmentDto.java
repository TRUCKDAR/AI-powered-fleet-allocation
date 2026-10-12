package escuelaing.edu.co.truckdar.fleet_allocation.dto.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Contrato de GET /api/v1/loads/driver-commitments en Load-Allocation: conductor con una carga aceptada,
 * en ruta o con una propuesta vigente.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DriverCommitmentDto {

    private String driverId;
    private Long loadId;
    private String loadStatus;
    private String assignmentStatus;

    /** Motivo legible para el candidato descartado. */
    public String busyReason() {
        String state = "AVAILABLE".equals(loadStatus)
                ? "propuesta " + assignmentStatus + " pendiente"
                : loadStatus;
        return "Conductor ocupado con la carga #" + loadId + " (" + state + ")";
    }
}
