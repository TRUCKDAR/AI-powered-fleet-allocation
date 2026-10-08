package escuelaing.edu.co.truckdar.fleet_allocation.controller;

import escuelaing.edu.co.truckdar.fleet_allocation.dto.request.ManualAllocationRequest;
import escuelaing.edu.co.truckdar.fleet_allocation.dto.response.AllocationResultResponse;
import escuelaing.edu.co.truckdar.fleet_allocation.service.IFleetAllocationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/fleet-allocation")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class FleetAllocationController {

    private final IFleetAllocationService service;

    @PostMapping("/optimize")
    public ResponseEntity<AllocationResultResponse> optimize(@Valid @RequestBody ManualAllocationRequest request) {
        return ResponseEntity.ok(service.processManualOptimization(request));
    }

    @PostMapping("/match/{loadId}")
    public ResponseEntity<AllocationResultResponse> matchLoadById(@PathVariable Long loadId) {
        return ResponseEntity.ok(service.processAutomaticAllocationForLoad(loadId));
    }

    @GetMapping("/history/{loadId}")
    public ResponseEntity<List<AllocationResultResponse>> getHistoryByLoad(@PathVariable Long loadId) {
        return ResponseEntity.ok(service.getDecisionsByLoad(loadId));
    }
}