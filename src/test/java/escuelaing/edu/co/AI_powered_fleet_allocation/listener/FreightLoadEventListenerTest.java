package escuelaing.edu.co.AI_powered_fleet_allocation.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import escuelaing.edu.co.truckdar.fleet_allocation.dto.event.EventEnvelope;
import escuelaing.edu.co.truckdar.fleet_allocation.dto.event.FreightLoadEventPayload;
import escuelaing.edu.co.truckdar.fleet_allocation.listener.FreightLoadEventListener;
import escuelaing.edu.co.truckdar.fleet_allocation.service.IFleetAllocationService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.Mockito.verify;

class FreightLoadEventListenerTest {

    @Test
    void rejectedAssignmentTriggersReallocationAndExcludesDriver() throws Exception {
        IFleetAllocationService service = Mockito.mock(IFleetAllocationService.class);
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        FreightLoadEventListener listener = new FreightLoadEventListener(service, mapper);
        EventEnvelope<FreightLoadEventPayload> event = EventEnvelope.<FreightLoadEventPayload>builder()
                .eventId(UUID.randomUUID())
                .eventType("assignment.rejected")
                .occurredAt(Instant.parse("2026-10-10T23:00:00Z"))
                .correlationId(UUID.randomUUID())
                .version(1)
                .producer("load-allocation")
                .payload(FreightLoadEventPayload.builder()
                        .loadId(42L)
                        .assignedDriverId("driver-rejected")
                        .excludedDriverIds(List.of("driver-earlier", "driver-rejected"))
                        .build())
                .build();

        listener.handleLoadPublished(mapper.writeValueAsString(event));

        verify(service).processAutomaticReallocationForLoad(
                42L, Set.of("driver-earlier", "driver-rejected"), "assignment.rejected");
    }
}
