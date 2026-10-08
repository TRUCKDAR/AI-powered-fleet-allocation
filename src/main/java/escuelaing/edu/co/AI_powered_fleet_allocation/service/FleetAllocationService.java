package escuelaing.edu.co.truckdar.fleet_allocation.service;

import escuelaing.edu.co.truckdar.fleet_allocation.dto.client.AvailableTruckDto;
import escuelaing.edu.co.truckdar.fleet_allocation.dto.client.FreightLoadDto;
import escuelaing.edu.co.truckdar.fleet_allocation.dto.event.FleetAllocationCompletedEvent;
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
        FreightLoadDto load = fetchLoadFromService(loadId);
        List<AvailableTruckDto> availableTrucks = fetchAvailableTrucksFromService();

        ManualAllocationRequest request = ManualAllocationRequest.builder()
                .load(load)
                .candidateTrucks(availableTrucks)
                .build();

        AllocationResultResponse result = processManualOptimization(request);

        if (result.getStatus() == AllocationStatus.SUCCESSFUL) {
            confirmAssignmentInLoadService(loadId, result.getSelectedDriverId(), result.getSelectedDriverName(), result.getSelectedVehiclePlate());
        }

        return result;
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

    private FreightLoadDto fetchLoadFromService(Long loadId) {
        try {
            return restClient.get()
                    .uri(loadAllocationServiceUrl + "/api/v1/loads/" + loadId)
                    .retrieve()
                    .body(FreightLoadDto.class);
        } catch (Exception e) {
            log.warn("No se pudo obtener carga desde {}: usando fallback local", loadAllocationServiceUrl);
            return FreightLoadDto.builder()
                    .id(loadId)
                    .weightTons(30.0)
                    .requiredTruckType("TRACTOMULA_3S3")
                    .originLatitude(4.6097)
                    .originLongitude(-74.0817)
                    .build();
        }
    }

    private List<AvailableTruckDto> fetchAvailableTrucksFromService() {
        try {
            return restClient.get()
                    .uri(fleetsDriversServiceUrl + "/api/v1/vehicles/available")
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<AvailableTruckDto>>() {});
        } catch (Exception e) {
            log.warn("No se pudo obtener flota desde {}: simulando transportadores disponibles", fleetsDriversServiceUrl);
            return List.of(
                    AvailableTruckDto.builder()
                            .plate("WOB123")
                            .driverId("79483011")
                            .driverName("Carlos Mendoza")
                            .truckType("TRACTOMULA_3S3")
                            .capacityTons(35.0)
                            .currentLatitude(4.6500)
                            .currentLongitude(-74.1000)
                            .rating(4.9)
                            .isAvailable(true)
                            .build()
            );
        }
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
}