package com.company.messenger.domain.schedule;
import com.company.messenger.global.auth.AuthenticatedUser;
import com.company.messenger.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ScheduleController {
    private final ScheduleService service;
    @GetMapping("/schedule-rooms") public ApiResponse<List<String>> rooms() { return ApiResponse.ok(ScheduleService.ROOMS); }
    @GetMapping("/channels/{channel}/schedules") public ApiResponse<List<ScheduleService.Schedule>> list(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long channel) {return ApiResponse.ok(service.list(user.userId(),channel));}
    @PostMapping("/channels/{channel}/schedules") public ApiResponse<ScheduleService.Schedule> create(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long channel,@Valid @RequestBody ScheduleService.Input input) {return ApiResponse.ok(service.save(user.userId(),channel,null,input));}
    @PutMapping("/channels/{channel}/schedules/{id}") public ApiResponse<ScheduleService.Schedule> edit(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long channel,@PathVariable long id,@Valid @RequestBody ScheduleService.Input input) {return ApiResponse.ok(service.save(user.userId(),channel,id,input));}
    @DeleteMapping("/channels/{channel}/schedules/{id}") public ApiResponse<Void> cancel(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long channel,@PathVariable long id,@RequestParam long revision) {service.cancel(user.userId(),channel,id,revision);return ApiResponse.ok(null);}
    @GetMapping("/schedule-reminders") public ApiResponse<List<ScheduleService.Reminder>> pending(@AuthenticationPrincipal AuthenticatedUser user) {return ApiResponse.ok(service.pending(user.userId()));}
    @PostMapping("/schedule-reminders/{id}/ack") public ApiResponse<Void> ack(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long id) {service.act(user.userId(),id,false);return ApiResponse.ok(null);}
    @PostMapping("/schedule-reminders/{id}/snooze") public ApiResponse<Void> snooze(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable long id) {service.act(user.userId(),id,true);return ApiResponse.ok(null);}
}
