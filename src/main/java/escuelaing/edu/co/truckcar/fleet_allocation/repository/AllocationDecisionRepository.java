package escuelaing.edu.co.truckcar.fleet_allocation.repository;

import escuelaing.edu.co.truckcar.fleet_allocation.model.AllocationDecision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AllocationDecisionRepository extends JpaRepository<AllocationDecision, Long> {
    List<AllocationDecision> findByLoadIdOrderByExecutedAtDesc(Long loadId);
    List<AllocationDecision> findBySelectedDriverIdOrderByExecutedAtDesc(String driverId);
}