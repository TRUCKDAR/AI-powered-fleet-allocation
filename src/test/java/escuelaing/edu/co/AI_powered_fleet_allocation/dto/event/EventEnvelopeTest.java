package escuelaing.edu.co.AI_powered_fleet_allocation.dto.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import escuelaing.edu.co.truckdar.fleet_allocation.dto.event.EventEnvelope;
import escuelaing.edu.co.truckdar.fleet_allocation.dto.event.FreightLoadEventPayload;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EventEnvelopeTest {

    @Test
    void deserializesLoadPublishedEnvelopeWithNestedPayload() throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        UUID eventId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        String json = """
                {
                  "eventId": "%s",
                  "eventType": "load.published",
                  "occurredAt": "2026-10-10T23:00:00Z",
                  "correlationId": "%s",
                  "version": 1,
                  "producer": "load-allocation",
                  "payload": {
                    "loadId": 42,
                    "weightTons": 30.0,
                    "requiredTruckType": "TRACTOMULA_3S3",
                    "loadStatus": "AVAILABLE"
                  }
                }
                """.formatted(eventId, correlationId);

        EventEnvelope<FreightLoadEventPayload> envelope = mapper.readValue(
                json,
                mapper.getTypeFactory().constructParametricType(
                        EventEnvelope.class,
                        FreightLoadEventPayload.class
                )
        );

        assertThat(envelope.getEventId()).isEqualTo(eventId);
        assertThat(envelope.getEventType()).isEqualTo("load.published");
        assertThat(envelope.getOccurredAt()).isEqualTo(Instant.parse("2026-10-10T23:00:00Z"));
        assertThat(envelope.getCorrelationId()).isEqualTo(correlationId);
        assertThat(envelope.getVersion()).isEqualTo(1);
        assertThat(envelope.getPayload().getLoadId()).isEqualTo(42L);
        assertThat(envelope.getPayload().getWeightTons()).isEqualTo(30.0);
    }
}
