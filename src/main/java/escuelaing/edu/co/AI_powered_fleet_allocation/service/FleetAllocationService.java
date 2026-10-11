package escuelaing.edu.co.truckdar.fleet_allocation.service;

import escuelaing.edu.co.truckdar.fleet_allocation.dto.client.AvailableTruckDto;
import escuelaing.edu.co.truckdar.fleet_allocation.dto.client.FreightLoadDto;
import escuelaing.edu.co.truckdar.fleet_allocation.dto.event.FleetAllocationCompletedEvent;
import escuelaing.edu.co.truckdar.fleet_allocation.dto.event.AgentScoreReadyEvent;
import escuelaing.edu.co.truckdar.fleet_allocation.dto.event.EventEnvelope;
import escuelaing.edu.co.truckdar.fleet_allocation.dto.request.ManualAllocationRequest;
import escuelaing.edu.co.truckdar.fleet_allocation.dto.response.AllocationCandidateResponse;
import escuelaing.edu.co.truckdar.fleet_allocation.dto.response.AllocationResultResponse;
import escuelaing.edu.co.truckdar.fleet_allocation.model.AllocationDecision;
import escuelaing.edu.co.truckdar.fleet_allocation.model.AllocationStatus;
import escuelaing.edu.co.truckdar.fleet_allocation.repository.AllocationDecisionRepository;
import escuelaing.edu.co.truckdar.fleet_allocation.service.algorithm.FleetScoringOptimizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FleetAllocationService implements IFleetAllocationService {

    private final AllocationDecisionRepository repository;
    private final FleetScoringOptimizer optimizer;
    private final RestClient restClient;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${truckdar.services.load-allocation-url:http://localhost:8083}")
    private String loadAllocationServiceUrl;

    @Value("${truckdar.services.fleets-drivers-url:http://localhost:8081}")
    private String fleetsDriversServiceUrl;

    @Value("${truckdar.kafka.topic.allocation-results:truckdar.events.fleet-allocation-results}")
    private String allocationTopic;

    @Override
    @Transactional
    public AllocationResultResponse processManualOptimization(ManualAllocationRequest request) {
        FreightLoadDto load = request.getLoad();
        List<AvailableTruckDto> candidates = request.getCandidateTrucks();

        List<AllocationCandidateResponse> evaluatedList = candidates.stream()
                .map(truck -> optimizer.evaluateTruck(truck, load))
                .sorted(Comparator.comparing(AllocationCandidateResponse::getScore).reversed())
                .collect(Collectors.toList());

        AllocationCandidateResponse bestCandidate = evaluatedList.stream()
                .filter(AllocationCandidateResponse::getEligible)
                .findFirst()
                .orElse(null);

        AllocationDecision decision = buildDecisionEntity(load.getId(), bestCandidate, evaluatedList.size());
        AllocationDecision saved = repository.save(decision);

        publishAllocationKafkaEvent(saved);

        return AllocationResultResponse.builder()
                .decisionId(saved.getId())
                .loadId(saved.getLoadId())
                .status(saved.getStatus())
                .selectedDriverId(saved.getSelectedDriverId())
                .selectedDriverName(saved.getSelectedDriverName())
                .selectedVehiclePlate(saved.getSelectedVehiclePlate())
                .matchingScore(saved.getMatchingScore())
                .deadheadDistanceKm(saved.getDeadheadDistanceKm())
                .reasonDetails(saved.getReasonDetails())
                .evaluatedCandidates(evaluatedList)
                .executedAt(saved.getExecutedAt())
                .build();
    }

    @Override
    @Transactional
    public AllocationResultResponse processAutomaticAllocationForLoad(Long loadId) {
        Optional<FreightLoadDto> load = fetchLoadFromService(loadId);
        if (load.isEmpty()) {
            return pendingRecommendation(loadId,
                    "La carga no está disponible; la recomendación queda pendiente sin datos sintéticos");
        }

        Optional<List<AvailableTruckDto>> availableTrucks = fetchAvailableTrucksFromService();
        if (availableTrucks.isEmpty()) {
            return pendingRecommendation(loadId,
                    "La flota no está disponible; la recomendación queda pendiente sin datos sintéticos");
        }

        ManualAllocationRequest request = ManualAllocationRequest.builder()
                .load(load.get())
                .candidateTrucks(availableTrucks.get())
                .build();

        AllocationResultResponse result = processManualOptimization(request);

        if (result.getStatus() == AllocationStatus.SUCCESSFUL) {
            confirmAssignmentInLoadService(loadId, result.getSelectedDriverId(), result.getSelectedDriverName(), result.getSelectedVehiclePlate());
        }

        return result;
    }

    @Override
    @Transactional
    public AllocationResultResponse processAutomaticReallocationForLoad(Long loadId, Set<String> excludedDriverIds,
                                                                        String trigger) {
        Set<String> excluded = excludedDriverIds == null ? Set.of() : excludedDriverIds;
        Optional<FreightLoadDto> load = fetchLoadFromService(loadId);
        if (load.isEmpty()) {
            return pendingRecommendation(loadId,
                    "La carga no está disponible; la reasignación queda pendiente sin datos sintéticos");
        }

        Optional<List<AvailableTruckDto>> availableTrucks = fetchAvailableTrucksFromService();
        if (availableTrucks.isEmpty()) {
            return pendingRecommendation(loadId,
                    "La flota no está disponible; la reasignación queda pendiente sin datos sintéticos");
        }

        List<AllocationCandidateResponse> evaluatedList = availableTrucks.get().stream()
                .filter(truck -> !excluded.contains(truck.getDriverId()))
                .map(truck -> optimizer.evaluateTruck(truck, load.get()))
                .sorted(Comparator.comparing(AllocationCandidateResponse::getScore).reversed())
                .collect(Collectors.toList());
        AllocationCandidateResponse nextCandidate = evaluatedList.stream()
                .filter(AllocationCandidateResponse::getEligible)
                .findFirst()
                .orElse(null);
        AllocationDecision saved = repository.save(buildDecisionEntity(loadId, nextCandidate, evaluatedList.size()));

        if (nextCandidate == null) {
            publishNoCandidatesEvent(saved, excluded, trigger);
            return AllocationResultResponse.builder()
                    .decisionId(saved.getId())
                    .loadId(loadId)
                    .status(AllocationStatus.NO_DRIVERS_AVAILABLE)
                    .reasonDetails("No hay candidatos elegibles después de excluir a los conductores " + excluded)
                    .evaluatedCandidates(evaluatedList)
                    .executedAt(saved.getExecutedAt())
                    .build();
        }

        publishScoreReadyEvent(saved, nextCandidate, trigger);
        return AllocationResultResponse.builder()
                .decisionId(saved.getId())
                .loadId(loadId)
                .status(saved.getStatus())
                .selectedDriverId(nextCandidate.getDriverId())
                .selectedDriverName(nextCandidate.getDriverName())
                .selectedVehiclePlate(nextCandidate.getVehiclePlate())
                .matchingScore(nextCandidate.getScore())
                .deadheadDistanceKm(nextCandidate.getDeadheadDistanceKm())
                .reasonDetails(nextCandidate.getReason())
                .evaluatedCandidates(evaluatedList)
                .executedAt(saved.getExecutedAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AllocationResultResponse> getDecisionsByLoad(Long loadId) {
        return repository.findByLoadIdOrderByExecutedAtDesc(loadId).stream()
                .map(d -> AllocationResultResponse.builder()
                        .decisionId(d.getId())
                        .loadId(d.getLoadId())
                        .status(d.getStatus())
                        .selectedDriverId(d.getSelectedDriverId())
                        .selectedDriverName(d.getSelectedDriverName())
                        .selectedVehiclePlate(d.getSelectedVehiclePlate())
                        .matchingScore(d.getMatchingScore())
                        .deadheadDistanceKm(d.getDeadheadDistanceKm())
                        .reasonDetails(d.getReasonDetails())
                        .executedAt(d.getExecutedAt())
                        .build())
                .collect(Collectors.toList());
    }

    private Optional<FreightLoadDto> fetchLoadFromService(Long loadId) {
        try {
            FreightLoadDto load = restClient.get()
                    .uri(loadAllocationServiceUrl + "/api/v1/loads/" + loadId)
                    .retrieve()
                    .body(FreightLoadDto.class);
            return Optional.ofNullable(load);
        } catch (Exception e) {
            log.error("No se pudo obtener carga desde {}; no se usarán datos sintéticos",
                    loadAllocationServiceUrl, e);
            return Optional.empty();
        }
    }

    private Optional<List<AvailableTruckDto>> fetchAvailableTrucksFromService() {
        try {
            List<AvailableTruckDto> trucks = restClient.get()
                    .uri(fleetsDriversServiceUrl + "/api/v1/vehicles/available")
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<AvailableTruckDto>>() {});
            return Optional.ofNullable(trucks);
        } catch (Exception e) {
            log.error("No se pudo obtener flota desde {}; no se usarán datos sintéticos",
                    fleetsDriversServiceUrl, e);
            return Optional.empty();
        }
    }

    private AllocationResultResponse pendingRecommendation(Long loadId, String reason) {
        return AllocationResultResponse.builder()
                .loadId(loadId)
                .status(AllocationStatus.RECOMMENDATION_PENDING)
                .reasonDetails(reason)
                .executedAt(LocalDateTime.now())
                .build();
    }

    private void confirmAssignmentInLoadService(Long loadId, String driverId, String driverName, String plate) {
        try {
            restClient.patch()
                    .uri(loadAllocationServiceUrl + "/api/v1/loads/" + loadId + "/assign")
                    .body(Map.of(
                            "driverId", driverId,
                            "driverName", driverName,
                            "vehiclePlate", plate
                    ))
                    .retrieve()
                    .toBodilessEntity();
            log.info("Asignación confirmada en Load-Allocation para carga #{}", loadId);
        } catch (Exception e) {
            log.warn("Fallo confirmando asignación en Load-Allocation: {}", e.getMessage());
        }
    }

    private AllocationDecision buildDecisionEntity(Long loadId, AllocationCandidateResponse bestCandidate, int candidatesCount) {
        if (bestCandidate != null) {
            return AllocationDecision.builder()
                    .loadId(loadId)
                    .selectedDriverId(bestCandidate.getDriverId())
                    .selectedDriverName(bestCandidate.getDriverName())
                    .selectedVehiclePlate(bestCandidate.getVehiclePlate())
                    .matchingScore(bestCandidate.getScore())
                    .deadheadDistanceKm(bestCandidate.getDeadheadDistanceKm())
                    .status(AllocationStatus.SUCCESSFUL)
                    .reasonDetails(bestCandidate.getReason())
                    .evaluatedCandidatesCount(candidatesCount)
                    .executedAt(LocalDateTime.now())
                    .build();
        } else {
            return AllocationDecision.builder()
                    .loadId(loadId)
                    .status(AllocationStatus.NO_DRIVERS_AVAILABLE)
                    .reasonDetails("Ningún vehículo cumplió los requisitos de capacidad, tipo o distancia máxima")
                    .evaluatedCandidatesCount(candidatesCount)
                    .executedAt(LocalDateTime.now())
                    .build();
        }
    }

    private void publishAllocationKafkaEvent(AllocationDecision d) {
        try {
            FleetAllocationCompletedEvent event = FleetAllocationCompletedEvent.builder()
                    .decisionId(d.getId())
                    .loadId(d.getLoadId())
                    .driverId(d.getSelectedDriverId())
                    .driverName(d.getSelectedDriverName())
                    .vehiclePlate(d.getSelectedVehiclePlate())
                    .matchingScore(d.getMatchingScore())
                    .deadheadDistanceKm(d.getDeadheadDistanceKm())
                    .status(d.getStatus().name())
                    .timestamp(LocalDateTime.now())
                    .build();

            kafkaTemplate.send(allocationTopic, d.getLoadId().toString(), event);
        } catch (Throwable t) {
            log.warn("No se pudo publicar evento de asignación en Kafka: {}", t.getMessage());
        }
    }

    private void publishScoreReadyEvent(AllocationDecision decision,
                                        AllocationCandidateResponse candidate,
                                        String trigger) {
            AgentScoreReadyEvent payload = AgentScoreReadyEvent.builder()
                    .loadId(decision.getLoadId())
                    .decisionId(decision.getId())
                    .driverId(candidate.getDriverId())
                    .driverName(candidate.getDriverName())
                    .vehiclePlate(candidate.getVehiclePlate())
                    .score(candidate.getScore())
                    .deadheadDistanceKm(candidate.getDeadheadDistanceKm())
                    .explanation(candidate.getReason())
                    .trigger(trigger)
                    .build();
            kafkaTemplate.send(allocationTopic, decision.getLoadId().toString(),
                    envelope("agent.score.ready", decision.getLoadId(), payload));
            log.info("Puntaje de reasignación listo para carga #{} y conductor {}",
                    decision.getLoadId(), candidate.getDriverId());
    }

    private void publishNoCandidatesEvent(AllocationDecision decision, Set<String> excludedDriverIds, String trigger) {
        Map<String, Object> payload = Map.of(
                "loadId", decision.getLoadId(),
                "decisionId", decision.getId(),
                "excludedDriverIds", List.copyOf(excludedDriverIds),
                "trigger", trigger == null ? "" : trigger
        );
        kafkaTemplate.send(allocationTopic, decision.getLoadId().toString(),
                envelope("agent.no.candidates", decision.getLoadId(), payload));
        log.info("Sin candidatos para la carga #{} tras excluir {}", decision.getLoadId(), excludedDriverIds);
    }

    private <T> EventEnvelope<T> envelope(String eventType, Long loadId, T payload) {
        return EventEnvelope.<T>builder()
                .eventId(UUID.randomUUID())
                .eventType(eventType)
                .occurredAt(Instant.now())
                .correlationId(UUID.nameUUIDFromBytes(
                        String.valueOf(loadId).getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .version(1)
                .producer("ai-powered-fleet-allocation")
                .payload(payload)
                .build();
    }
}