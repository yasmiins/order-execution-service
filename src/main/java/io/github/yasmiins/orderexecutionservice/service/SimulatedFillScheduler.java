package io.github.yasmiins.orderexecutionservice.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "simulator.enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(name = "simulator.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SimulatedFillScheduler {

    private final SimulatedFillEngine engine;

    public SimulatedFillScheduler(SimulatedFillEngine engine) {
        this.engine = engine;
    }

    @Scheduled(fixedDelayString = "${simulator.tick-ms:1000}")
    public void runTick() {
        engine.processOpenOrders();
    }
}
