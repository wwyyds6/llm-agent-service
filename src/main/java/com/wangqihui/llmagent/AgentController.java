package com.wangqihui.llmagent;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AgentController {

    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @GetMapping("/agent/chat")
    public String chat(@RequestParam String msg) {
        return agentService.chatWithTools(msg);
    }
}