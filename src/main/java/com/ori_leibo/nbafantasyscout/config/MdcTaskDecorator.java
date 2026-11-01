package com.ori_leibo.nbafantasyscout.config;

import java.util.Map;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

/**
 * MDC lives in a ThreadLocal, so a pool thread does not see the submitter's correlation id.
 * Copies it over for the task and restores the worker's own context afterwards.
 */
class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable task) {
        Map<String, String> submitter = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> worker = MDC.getCopyOfContextMap();
            set(submitter);
            try {
                task.run();
            } finally {
                set(worker);
            }
        };
    }

    private static void set(Map<String, String> context) {
        if (context == null) {
            MDC.clear();
        } else {
            MDC.setContextMap(context);
        }
    }
}
