package io.github.vxmqmqtt.vxmq.routing.eval;

/**
 * Factory abstraction so correctness and benchmark tests can instantiate candidates lazily.
 */
interface RoutingRegistryCandidateFactory {

    /**
     * Returns the display name of the candidate strategy.
     */
    String name();

    /**
     * Creates a new isolated registry candidate instance for a test or benchmark run.
     */
    RoutingRegistryCandidate create() throws Exception;
}
