package com.aiplatform.service;

import com.aiplatform.dto.AiRequest;
import com.aiplatform.dto.RoutingContext;
import com.aiplatform.dto.RoutingDecision;
import com.aiplatform.dto.RoutingResult;
import com.aiplatform.model.ModelCapability;
import com.aiplatform.model.User;
import com.aiplatform.repository.UserRepository;
import com.aiplatform.strategy.RoutingStrategy;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ProviderRouter {
    private static final Logger logger = LoggerFactory.getLogger(ProviderRouter.class);
    private final ModelRegistry modelRegistry;
    private final UserRepository userRepository;
    private final List<RoutingStrategy> strategies;
    private final AiMetricsService metricsService;
    private final ObservationRegistry observationRegistry;

    public ProviderRouter(ModelRegistry modelRegistry, 
                          UserRepository userRepository, 
                          List<RoutingStrategy> strategies,
                          AiMetricsService metricsService,
                          ObservationRegistry observationRegistry) {
        this.modelRegistry = modelRegistry;
        this.userRepository = userRepository;
        this.metricsService = metricsService;
        this.observationRegistry = observationRegistry;
        this.strategies = strategies.stream()
                .sorted(java.util.Comparator.comparingInt(RoutingStrategy::getOrder))
                .toList();
        
        logger.info("🚦 Operational Routing Engine initialized with {} strategies", strategies.size());
    }

    public RoutingResult route(AiRequest request) {
        return Observation.createNotStarted("ai.routing.route", observationRegistry)
                .observe(() -> {
            long startTime = System.currentTimeMillis();
            User user = userRepository.findById(request.getUserId()).orElse(null);
            String currentModelId = request.getModelId();
            
            if (currentModelId == null || currentModelId.isEmpty()) {
                currentModelId = modelRegistry.getDefaultModel().getModelId();
            }

            RoutingContext context = new RoutingContext(request, user, currentModelId);
            List<String> trace = new ArrayList<>();
            trace.add("Initial selection: " + currentModelId);

            for (RoutingStrategy strategy : strategies) {
                ModelCapability capability = modelRegistry.getCapability(currentModelId)
                        .orElse(modelRegistry.getDefaultModel());
                
                RoutingDecision decision = strategy.apply(context, capability);
                
                if (decision.traceMessage() != null) {
                    String oldModelId = currentModelId;
                    currentModelId = decision.modelId();
                    
                    trace.add(String.format("[%s]: %s", strategy.getClass().getSimpleName(), decision.traceMessage()));
                    
                    // Record operational metrics
                    metricsService.recordRoutingDecision(strategy.getClass().getSimpleName(), currentModelId);
                    if (!oldModelId.equals(currentModelId)) {
                        metricsService.recordDowngrade(oldModelId, currentModelId, strategy.getClass().getSimpleName());
                    }
                }
            }
            
            long latency = System.currentTimeMillis() - startTime;
            RoutingResult result = new RoutingResult(currentModelId, trace, latency, new HashMap<>());
            
            logTrace(result);
            return result;
        });
    }

    private void logTrace(RoutingResult result) {
        StringBuilder sb = new StringBuilder("\n--- 🧩 AI ROUTING TRACE [")
                .append(result.routingLatencyMs()).append("ms] ---\n");
        for (int i = 0; i < result.trace().size(); i++) {
            sb.append(String.format("  %d. %s\n", i + 1, result.trace().get(i)));
        }
        sb.append("--- FINAL DECISION: ").append(result.selectedModelId()).append(" ---");
        logger.info(sb.toString());
    }
}
