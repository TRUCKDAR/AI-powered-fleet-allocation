package escuelaing.edu.co.truckdar.fleet_allocation.service;

import escuelaing.edu.co.truckdar.fleet_allocation.dto.request.ManualAllocationRequest;
import escuelaing.edu.co.truckdar.fleet_allocation.dto.response.AllocationResultResponse;

import java.util.List;

public interface IFleetAllocationService {
    AllocationResultResponse processManualOptimization(ManualAllocationRequest request);
    AllocationResultResponse processAutomaticAllocationForLoad(Long loadId);
    List<AllocationResultResponse> getDecisionsByLoad(Long loadId);
}