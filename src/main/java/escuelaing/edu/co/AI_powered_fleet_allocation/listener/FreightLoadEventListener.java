package escuelaing.edu.co.truckdar.fleet_allocation.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import escuelaing.edu.co.truckdar.fleet_allocation.dto.event.FreightLoadPublishedEvent;
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
            FreightLoadPublishedEvent event = objectMapper.readValue(message, FreightLoadPublishedEvent.class);
            if ("PUBLISHED".equalsIgnoreCase(event.getEventType())) {
                log.info("Evento Kafka de nueva carga recibido para matching automático: Carga #{}", event.getLoadId());
                allocationService.processAutomaticAllocationForLoad(event.getLoadId());
            }
        } catch (Exception e) {
            log.warn("No se pudo procesar evento de carga publicada: {}", e.getMessage());
        }
    }
}