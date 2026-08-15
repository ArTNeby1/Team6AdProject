package com.loomytrip.backend.event;

import com.loomytrip.backend.service.PlanningService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class InitialImportListener {

    private final PlanningService planningService;

    public InitialImportListener(PlanningService planningService) {
        this.planningService = planningService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void process(InitialImportRequestedEvent event) {
        planningService.processInitialImport(event.sessionId());
    }
}
