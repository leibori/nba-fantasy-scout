package com.ori_leibo.nbafantasyscout.api;

import com.ori_leibo.nbafantasyscout.api.dto.WeekScheduleResponse;
import com.ori_leibo.nbafantasyscout.recommend.WeekSchedule;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/schedule")
public class ScheduleController {

    private final WeekSchedule schedule;

    public ScheduleController(WeekSchedule schedule) {
        this.schedule = schedule;
    }

    @Operation(summary = "The fantasy week a pickup can still help in (Monday to Sunday, holding tomorrow): games per "
            + "team and how many from tomorrow on, most first")
    @GetMapping("/week")
    public WeekScheduleResponse week() {
        return schedule.thisWeek().map(WeekScheduleResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "The schedule can't be read right now"));
    }
}
