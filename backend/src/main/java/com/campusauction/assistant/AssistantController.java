package com.campusauction.assistant;

import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

    private final AssistantService assistantService;

    public AssistantController(AssistantService assistantService) {
        this.assistantService = assistantService;
    }

    @PostMapping("/chat")
    public AssistantService.AssistantResponse chat(@RequestBody AssistantRequest request) {
        return assistantService.processQuery(
                request.message(),
                request.statedBudget(),
                request.contextAuctionId()
        );
    }

    public record AssistantRequest(
            String message,
            BigDecimal statedBudget,
            Long contextAuctionId
    ) {}
}
