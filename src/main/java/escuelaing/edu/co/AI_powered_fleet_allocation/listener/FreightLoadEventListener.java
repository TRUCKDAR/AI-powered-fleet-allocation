package escuelaing.edu.co.truckdar.fleet_allocation.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import escuelaing.edu.co.truckdar.fleet_allocation.dto.event.EventEnvelope;
import escuelaing.edu.co.truckdar.fleet_allocation.dto.event.FreightLoadEventPayload;
import escuelaing.edu.co.truckdar.fleet_allocation.service.IFleetAllocationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FreightLoadEventListener {

    private final IFleetAllocationService allocationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "${truckdar.kafka.topic.load-events:truckdar.events.load-allocation}", groupId = "fleet-allocation-group")
    public void handleLoadPublished(String message) {
        try {
            EventEnvelope<FreightLoadEventPayload> event = objectMapper.readValue(
                    message,
                    objectMapper.getTypeFactory().constructParametricType(
                            EventEnvelope.class,
                            FreightLoadEventPayload.class
                    )
            );
            if ("load.published".equals(event.getEventType()) && event.getPayload() != null) {
                log.info("Evento Kafka de nueva carga recibido para matching automático: Carga #{}",
                        event.getPayload().getLoadId());
                allocationService.processAutomaticAllocationForLoad(event.getPayload().getLoadId());
            } else if ("load.assigned".equals(event.getEventType())) {
                log.debug("Evento load.assigned ignorado para evitar reprocesar la carga #{}",
                        event.getPayload() != null ? event.getPayload().getLoadId() : null);
            } else if (("assignment.rejected".equals(event.getEventType())
                    || "assignment.timeout".equals(event.getEventType()))
                    && event.getPayload() != null
                    && event.getPayload().getLoadId() != null) {
                String excludedDriverId = event.getPayload().getAssignedDriverId();
                log.info("Reasignación solicitada para carga #{} por {}; se excluye conductor {}",
                        event.getPayload().getLoadId(), event.getEventType(), excludedDriverId);
                allocationService.processAutomaticReallocationForLoad(
                        event.getPayload().getLoadId(), excludedDriverId, event.getEventType());
            }
        } catch (Exception e) {
            log.error("No se pudo procesar evento de carga con sobre común", e);
            throw new IllegalStateException("Evento de carga inválido; se requiere reintento o DLQ", e);
        }
    }
}