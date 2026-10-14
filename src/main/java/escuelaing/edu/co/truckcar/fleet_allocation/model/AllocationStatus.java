package escuelaing.edu.co.truckcar.fleet_allocation.model;

public enum AllocationStatus {
    SUCCESSFUL,
    NO_DRIVERS_AVAILABLE,
    REJECTED,
    DEGRADED_USING_CACHE,
    RECOMMENDATION_PENDING,
    /**
     * Solo en respuestas: Load-Allocation rechazó la asignación (409). La decisión se persiste como REJECTED
     * porque la columna allocation_decisions.status tiene un CHECK con los valores anteriores.
     */
    ASSIGNMENT_CONFLICT,
    /** Solo en respuestas: Load-Allocation no respondió; la decisión se persiste como RECOMMENDATION_PENDING. */
    ASSIGNMENT_FAILED
}
