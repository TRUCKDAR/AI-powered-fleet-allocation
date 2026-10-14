package escuelaing.edu.co.truckcar.fleet_allocation.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import escuelaing.edu.co.truckcar.fleet_allocation.dto.event.EventEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DriverAvailabilityEventListener {

    private final ObjectMapper objectMapper;

    @KafkaListener(
            topics = "${truckcar.kafka.topic.vehicle-status:truckcar.events.vehicle-status}",
            groupId = "fleet-allocation-group"
    )
    public void handleDriverAvailability(String message) {
        try {
            EventEnvelope<?> event = objectMapper.readValue(message, EventEnvelope.class);
            if (!"driver.availability.changed".equals(event.getEventType())
                    || event.getPayload() == null) {
                log.debug("Evento de disponibilidad ignorado: type='{}'", event.getEventType());
                return;
            }
            log.info("Disponibilidad de conductor/vehículo actualizada para futuras asignaciones: eventId='{}', correlationId='{}'",
                    event.getEventId(), event.getCorrelationId());
        } catch (Exception e) {
            log.error("No se pudo procesar evento de disponibilidad bajo sobre común", e);
            throw new IllegalStateException("Evento de disponibilidad inválido; se requiere retry o DLQ", e);
        }
    }
}
