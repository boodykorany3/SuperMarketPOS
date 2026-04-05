package com.pos.api.controller;

import com.pos.api.dto.AttendanceRecordRequestDto;
import com.pos.api.dto.AttendanceRecordResponseDto;
import com.pos.api.dto.AttendanceWaiveRequestDto;
import com.pos.api.dto.EmployeeProfileRequestDto;
import com.pos.api.dto.EmployeeProfileResponseDto;
import com.pos.api.dto.PayrollSummaryResponseDto;
import com.pos.api.service.EmployeeAttendanceService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping
public class WorkforceController {

    private final EmployeeAttendanceService employeeAttendanceService;

    public WorkforceController(EmployeeAttendanceService employeeAttendanceService) {
        this.employeeAttendanceService = employeeAttendanceService;
    }

    @GetMapping("/employees")
    public List<EmployeeProfileResponseDto> getEmployees(
            @RequestParam(value = "branchId", required = false) Long branchId,
            @RequestParam(value = "active", required = false) Boolean active
    ) {
        return employeeAttendanceService.getEmployees(branchId, active);
    }

    @PostMapping("/employees")
    @ResponseStatus(HttpStatus.CREATED)
    public EmployeeProfileResponseDto createEmployee(@Valid @RequestBody EmployeeProfileRequestDto request) {
        return employeeAttendanceService.createEmployee(request);
    }

    @PutMapping("/employees/{id}")
    public EmployeeProfileResponseDto updateEmployee(@PathVariable("id") Long id,
                                                     @Valid @RequestBody EmployeeProfileRequestDto request) {
        return employeeAttendanceService.updateEmployee(id, request);
    }

    @GetMapping("/attendance")
    public List<AttendanceRecordResponseDto> getAttendance(
            @RequestParam(value = "employeeId", required = false) Long employeeId,
            @RequestParam(value = "branchId", required = false) Long branchId,
            @RequestParam(value = "fromDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(value = "toDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate
    ) {
        return employeeAttendanceService.getAttendance(employeeId, branchId, fromDate, toDate);
    }

    @PostMapping("/attendance")
    @ResponseStatus(HttpStatus.CREATED)
    public AttendanceRecordResponseDto upsertAttendance(@Valid @RequestBody AttendanceRecordRequestDto request) {
        return employeeAttendanceService.upsertAttendance(request);
    }

    @PostMapping("/attendance/{id}/waive-deduction")
    public AttendanceRecordResponseDto waiveDeduction(@PathVariable("id") Long id,
                                                      Authentication authentication,
                                                      @Valid @RequestBody AttendanceWaiveRequestDto request) {
        return employeeAttendanceService.waiveDeduction(id, request, authentication == null ? null : authentication.getName());
    }

    @GetMapping("/payroll/summary")
    public PayrollSummaryResponseDto getPayrollSummary(
            @RequestParam(value = "month", required = false) String month,
            @RequestParam(value = "branchId", required = false) Long branchId
    ) {
        return employeeAttendanceService.getPayrollSummary(month, branchId);
    }
}
