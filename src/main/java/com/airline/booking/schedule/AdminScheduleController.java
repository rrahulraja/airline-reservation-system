package com.airline.booking.schedule;

import com.airline.booking.schedule.dto.CreateScheduleRequest;
import com.airline.booking.schedule.dto.ScheduleResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** ADMIN only; the restriction is applied by SecurityConfig on /api/admin/**. */
@RestController
@RequestMapping("/api/admin/schedules")
public class AdminScheduleController {

    private static final int MAX_PAGE_SIZE = 100;

    private final ScheduleService service;

    public AdminScheduleController(ScheduleService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ScheduleResponse create(@Valid @RequestBody CreateScheduleRequest request) {
        return service.create(request);
    }

    @GetMapping("/{id}")
    public ScheduleResponse get(@PathVariable long id) {
        return service.findById(id);
    }

    @GetMapping
    public Page<ScheduleResponse> list(@RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        // Capped: without this, ?size=1000000 is a trivially available way to exhaust
        // memory.
        return service.findPage(PageRequest.of(page, Math.min(size, MAX_PAGE_SIZE)));
    }
}
