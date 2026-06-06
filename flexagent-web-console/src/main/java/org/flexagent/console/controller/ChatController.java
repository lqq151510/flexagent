package org.flexagent.console.controller;

import org.flexagent.core.Agent;
import org.flexagent.core.model.Step;
import org.flexagent.core.model.StepType;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api")
public class ChatController {

    private final Agent agent;

    public ChatController(Agent agent) {
        this.agent = agent;
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<Step> chatStream(@RequestBody Map<String, String> payload) {
        String message = payload.getOrDefault("message", "");

        // 异步派发聊天任务
        new Thread(() -> {
            try {
                agent.chat(message);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

        // 持续从队列中 Poll 步骤，并转换为 Flux
        return Flux.<Step>create(sink -> {
            boolean done = false;
            while (!done && !sink.isCancelled()) {
                try {
                    Step step = agent.pollStep(30, TimeUnit.SECONDS);
                    if (step == null) continue;

                    sink.next(step);

                    if (step.type() == StepType.TEXT_RESPONSE || step.type() == StepType.ERROR) {
                        done = true;
                        sink.complete();
                    }
                } catch (Exception e) {
                    sink.error(e);
                    done = true;
                }
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
