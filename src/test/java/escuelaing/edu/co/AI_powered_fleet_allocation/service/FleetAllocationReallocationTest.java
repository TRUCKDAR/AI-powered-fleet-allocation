package escuelaing.edu.co.AI_powered_fleet_allocation.service;

import escuelaing.edu.co.truckdar.fleet_allocation.dto.client.AvailableTruckDto;
import escuelaing.edu.co.truckdar.fleet_allocation.dto.client.FreightLoadDto;
import escuelaing.edu.co.truckdar.fleet_allocation.dto.event.EventEnvelope;
import escuelaing.edu.co.truckdar.fleet_allocation.dto.response.AllocationCandidateResponse;
import escuelaing.edu.co.truckdar.fleet_allocation.dto.response.AllocationResultResponse;
import escuelaing.edu.co.truckdar.fleet_allocation.model.AllocationDecision;
import escuelaing.edu.co.truckdar.fleet_allocation.model.AllocationStatus;
import escuelaing.edu.co.truckdar.fleet_allocation.repository.AllocationDecisionRepository;
import escuelaing.edu.co.truckdar.fleet_allocation.service.FleetAllocationService;
import escuelaing.edu.co.truckdar.fleet_allocation.service.algorithm.FleetScoringOptimizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FleetAllocationReallocationTest {

    private AllocationDecisionRepository repository;
    private FleetScoringOptimizer optimizer;
    private RestClient restClient;
    private KafkaTemplate<String, Object> kafkaTemplate;
    private FleetAllocationService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        repository = mock(AllocationDecisionRepository.class);
        optimizer = mock(FleetScoringOptimizer.class);
        restClient = mock(RestClient.class, RETURNS_DEEP_STUBS);
        kafkaTemplate = mock(KafkaTemplate.class);
        service = new FleetAllocationService(repository, optimizer, restClient, kafkaTemplate);

        when(restClient.get().uri(anyString()).retrieve().body(FreightLoadDto.class))
                .thenReturn(FreightLoadDto.builder().id(42L).weightTons(20.0).build());
        when(restClient.get().uri(anyString()).retrieve().body(any(ParameterizedTypeReference.class)))
                .thenReturn(List.of(truck("driver-1"), truck("driver-2"), truck("driver-3")));
        when(optimizer.evaluateTruck(any(AvailableTruckDto.class), any(FreightLoadDto.class)))
                .thenAnswer(invocation -> {
                    AvailableTruckDto truck = invocation.getArgument(0);
                    return AllocationCandidateResponse.builder()
                            .driverId(truck.getDriverId())
                            .vehiclePlate(truck.getPlate())
                            .score("driver-1".equals(truck.getDriverId()) ? 99.0 : 50.0)
                            .eligible(true)
                            .build();
                });
        when(repository.save(any(AllocationDecision.class))).thenAnswer(invocation -> {
            AllocationDecision decision = invocation.getArgument(0);
            decision.setId(1L);
            return decision;
        });
    }

    @Test
    void excludesEveryDiscardedDriverAndPublishesScoreReady() {
        AllocationResultResponse result = service.processAutomaticReallocationForLoad(
                42L, Set.of("driver-1", "driver-2"), "assignment.rejected");

        assertThat(result.getSelectedDriverId()).isEqualTo("driver-3");
        assertThat(publishedEnvelope().getEventType()).isEqualTo("agent.score.ready");
    }

    @Test
    void publishesNoCandidatesWhenAllDriversWereDiscarded() {
        AllocationResultResponse result = service.processAutomaticReallocationForLoad(
                42L, Set.of("driver-1", "driver-2", "driver-3"), "assignment.timeout");

        assertThat(result.getStatus()).isEqualTo(AllocationStatus.NO_DRIVERS_AVAILABLE);
        assertThat(publishedEnvelope().getEventType()).isEqualTo("agent.no.candidates");
    }

    private EventEnvelope<?> publishedEnvelope() {
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(Mockito.<String>any(), eq("42"), captor.capture());
        return (EventEnvelope<?>) captor.getValue();
    }

    private AvailableTruckDto truck(String driverId) {
        return AvailableTruckDto.builder().driverId(driverId).plate("PL-" + driverId).build();
    }
}
