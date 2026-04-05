package com.pos.api.service;

import com.pos.api.dto.AttendanceRecordRequestDto;
import com.pos.api.dto.AttendanceRecordResponseDto;
import com.pos.api.dto.AttendanceWaiveRequestDto;
import com.pos.api.dto.EmployeeProfileRequestDto;
import com.pos.api.dto.EmployeeProfileResponseDto;
import com.pos.api.dto.PayrollEmployeeSummaryDto;
import com.pos.api.dto.PayrollSummaryResponseDto;
import com.pos.api.entity.AttendanceRecord;
import com.pos.api.entity.AttendanceStatus;
import com.pos.api.entity.Branch;
import com.pos.api.entity.EmployeeProfile;
import com.pos.api.exception.ApiException;
import com.pos.api.repository.AttendanceRecordRepository;
import com.pos.api.repository.EmployeeProfileRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class EmployeeAttendanceService {

    private static final BigDecimal MONTH_DAYS = BigDecimal.valueOf(30);
    private static final BigDecimal WORK_MINUTES_PER_DAY = BigDecimal.valueOf(8 * 60);

    private final EmployeeProfileRepository employeeProfileRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final BranchService branchService;

    public EmployeeAttendanceService(EmployeeProfileRepository employeeProfileRepository,
                                     AttendanceRecordRepository attendanceRecordRepository,
                                     BranchService branchService) {
        this.employeeProfileRepository = employeeProfileRepository;
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.branchService = branchService;
    }

    public List<EmployeeProfileResponseDto> getEmployees(Long branchId, Boolean active) {
        if (branchId != null) {
            branchService.requireById(branchId);
        }

        List<EmployeeProfile> employees = branchId == null
                ? employeeProfileRepository.findAll()
                : employeeProfileRepository.findByBranchId(branchId);

        return employees.stream()
                .sorted(Comparator.comparing(EmployeeProfile::getEmployeeCode, Comparator.nullsLast(String::compareTo)))
                .filter(employee -> active == null || employee.isActive() == active)
                .map(this::toEmployeeResponse)
                .toList();
    }

    @Transactional
    public EmployeeProfileResponseDto createEmployee(EmployeeProfileRequestDto request) {
        String code = normalizeEmployeeCode(request.getEmployeeCode());
        if (employeeProfileRepository.existsByEmployeeCode(code)) {
            throw new ApiException(HttpStatus.CONFLICT, "Employee code already exists.");
        }

        EmployeeProfile employee = new EmployeeProfile();
        applyEmployeeRequest(employee, request, true);
        employee.setEmployeeCode(code);
        return toEmployeeResponse(employeeProfileRepository.save(employee));
    }

    @Transactional
    public EmployeeProfileResponseDto updateEmployee(Long id, EmployeeProfileRequestDto request) {
        EmployeeProfile employee = requireEmployeeById(id);
        String code = normalizeEmployeeCode(request.getEmployeeCode());
        if (employeeProfileRepository.existsByEmployeeCodeAndIdNot(code, id)) {
            throw new ApiException(HttpStatus.CONFLICT, "Employee code already exists.");
        }

        applyEmployeeRequest(employee, request, false);
        employee.setEmployeeCode(code);
        return toEmployeeResponse(employeeProfileRepository.save(employee));
    }

    public List<AttendanceRecordResponseDto> getAttendance(Long employeeId,
                                                           Long branchId,
                                                           LocalDate fromDate,
                                                           LocalDate toDate) {
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "fromDate must be before or equal to toDate.");
        }
        if (employeeId != null) {
            requireEmployeeById(employeeId);
        }
        if (branchId != null) {
            branchService.requireById(branchId);
        }

        return attendanceRecordRepository.findForListing(employeeId, branchId, fromDate, toDate).stream()
                .map(this::toAttendanceResponse)
                .toList();
    }

    @Transactional
    public AttendanceRecordResponseDto upsertAttendance(AttendanceRecordRequestDto request) {
        EmployeeProfile employee = requireEmployeeById(request.getEmployeeId());

        AttendanceRecord record = attendanceRecordRepository.findByEmployeeIdAndAttendanceDate(
                        request.getEmployeeId(),
                        request.getAttendanceDate())
                .orElseGet(AttendanceRecord::new);

        record.setEmployee(employee);
        record.setAttendanceDate(request.getAttendanceDate());
        record.setStatus(request.getStatus());

        int lateMinutes = resolveLateMinutes(request.getStatus(), request.getLateMinutes());
        record.setLateMinutes(lateMinutes);

        if (request.getStatus() == AttendanceStatus.ABSENT
                || request.getStatus() == AttendanceStatus.NO_FINGERPRINT
                || request.getStatus() == AttendanceStatus.LEAVE) {
            record.setCheckInTime(null);
        } else {
            record.setCheckInTime(request.getCheckInTime());
        }

        BigDecimal deduction = calculateDeduction(employee.getMonthlySalary(), request.getStatus(), lateMinutes);
        record.setDeductionAmount(deduction);
        record.setDeductionWaived(false);
        record.setWaivedBy(null);
        record.setWaivedReason(null);
        record.setWaivedAt(null);

        AttendanceRecord saved = attendanceRecordRepository.save(record);
        return toAttendanceResponse(saved);
    }

    @Transactional
    public AttendanceRecordResponseDto waiveDeduction(Long attendanceId,
                                                      AttendanceWaiveRequestDto request,
                                                      String waivedBy) {
        AttendanceRecord record = requireAttendanceById(attendanceId);
        BigDecimal deduction = zeroIfNull(record.getDeductionAmount());
        if (deduction.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(HttpStatus.CONFLICT, "No deduction to waive for this record.");
        }

        record.setDeductionWaived(true);
        record.setWaivedBy(normalizeActor(waivedBy));
        record.setWaivedReason(trimToNull(request == null ? null : request.getReason()));
        record.setWaivedAt(LocalDateTime.now());

        AttendanceRecord saved = attendanceRecordRepository.save(record);
        return toAttendanceResponse(saved);
    }

    public PayrollSummaryResponseDto getPayrollSummary(String monthValue, Long branchId) {
        YearMonth month = parseMonth(monthValue);
        LocalDate fromDate = month.atDay(1);
        LocalDate toDate = month.atEndOfMonth();

        Branch branch = null;
        if (branchId != null) {
            branch = branchService.requireById(branchId);
        }

        List<EmployeeProfile> employees = branchId == null
                ? employeeProfileRepository.findAll()
                : employeeProfileRepository.findByBranchId(branchId);
        employees = employees.stream()
                .filter(EmployeeProfile::isActive)
                .sorted(Comparator.comparing(EmployeeProfile::getEmployeeCode, Comparator.nullsLast(String::compareTo)))
                .toList();

        List<AttendanceRecord> attendance = attendanceRecordRepository.findForPayroll(fromDate, toDate, branchId);
        Map<Long, List<AttendanceRecord>> attendanceByEmployee = new LinkedHashMap<>();
        for (AttendanceRecord record : attendance) {
            attendanceByEmployee
                    .computeIfAbsent(record.getEmployee().getId(), key -> new ArrayList<>())
                    .add(record);
        }

        PayrollSummaryResponseDto summary = new PayrollSummaryResponseDto();
        summary.setMonth(month.toString());
        if (branch != null) {
            summary.setBranchId(branch.getId());
            summary.setBranchCode(branch.getCode());
            summary.setBranchName(branch.getName());
        }

        BigDecimal totalBaseSalary = BigDecimal.ZERO;
        BigDecimal totalDeduction = BigDecimal.ZERO;
        List<PayrollEmployeeSummaryDto> employeeSummaries = new ArrayList<>();

        for (EmployeeProfile employee : employees) {
            PayrollEmployeeSummaryDto employeeSummary = new PayrollEmployeeSummaryDto();
            employeeSummary.setEmployeeId(employee.getId());
            employeeSummary.setEmployeeCode(employee.getEmployeeCode());
            employeeSummary.setEmployeeName(employee.getFullName());
            employeeSummary.setBranchId(employee.getBranch().getId());
            employeeSummary.setBranchCode(employee.getBranch().getCode());
            employeeSummary.setBranchName(employee.getBranch().getName());
            employeeSummary.setMonthlySalary(normalizeSalary(employee.getMonthlySalary()));

            BigDecimal employeeDeduction = BigDecimal.ZERO;
            List<AttendanceRecord> records = attendanceByEmployee.getOrDefault(employee.getId(), List.of());
            for (AttendanceRecord record : records) {
                switch (record.getStatus()) {
                    case PRESENT -> employeeSummary.setPresentDays(employeeSummary.getPresentDays() + 1);
                    case LATE -> employeeSummary.setLateDays(employeeSummary.getLateDays() + 1);
                    case ABSENT -> employeeSummary.setAbsentDays(employeeSummary.getAbsentDays() + 1);
                    case NO_FINGERPRINT -> employeeSummary.setNoFingerprintDays(employeeSummary.getNoFingerprintDays() + 1);
                    case LEAVE -> employeeSummary.setLeaveDays(employeeSummary.getLeaveDays() + 1);
                }
                employeeDeduction = employeeDeduction.add(effectiveDeduction(record));
            }

            employeeSummary.setTotalDeduction(employeeDeduction);
            BigDecimal netSalary = employeeSummary.getMonthlySalary().subtract(employeeDeduction);
            if (netSalary.compareTo(BigDecimal.ZERO) < 0) {
                netSalary = BigDecimal.ZERO;
            }
            employeeSummary.setNetSalary(netSalary);

            employeeSummaries.add(employeeSummary);
            totalBaseSalary = totalBaseSalary.add(employeeSummary.getMonthlySalary());
            totalDeduction = totalDeduction.add(employeeSummary.getTotalDeduction());
        }

        summary.setEmployees(employeeSummaries);
        summary.setTotalEmployees((long) employeeSummaries.size());
        summary.setTotalBaseSalary(totalBaseSalary);
        summary.setTotalDeduction(totalDeduction);
        summary.setTotalNetSalary(totalBaseSalary.subtract(totalDeduction).max(BigDecimal.ZERO));

        return summary;
    }

    private void applyEmployeeRequest(EmployeeProfile employee, EmployeeProfileRequestDto request, boolean creating) {
        employee.setFullName(normalizeText(request.getFullName(), "fullName is required"));
        employee.setBranch(branchService.requireById(request.getBranchId()));
        employee.setMonthlySalary(normalizeSalary(request.getMonthlySalary()));
        if (request.getActive() != null) {
            employee.setActive(request.getActive());
        } else if (creating) {
            employee.setActive(true);
        }
    }

    private BigDecimal calculateDeduction(BigDecimal monthlySalary, AttendanceStatus status, int lateMinutes) {
        BigDecimal salary = normalizeSalary(monthlySalary);
        BigDecimal dailySalary = salary.divide(MONTH_DAYS, 2, RoundingMode.HALF_UP);

        return switch (status) {
            case ABSENT, NO_FINGERPRINT -> dailySalary;
            case LATE -> {
                if (lateMinutes <= 0) {
                    yield BigDecimal.ZERO;
                }
                BigDecimal perMinute = dailySalary.divide(WORK_MINUTES_PER_DAY, 6, RoundingMode.HALF_UP);
                BigDecimal deduction = perMinute.multiply(BigDecimal.valueOf(lateMinutes)).setScale(2, RoundingMode.HALF_UP);
                if (deduction.compareTo(dailySalary) > 0) {
                    deduction = dailySalary;
                }
                yield deduction;
            }
            case PRESENT, LEAVE -> BigDecimal.ZERO;
        };
    }

    private int resolveLateMinutes(AttendanceStatus status, Integer lateMinutes) {
        if (status != AttendanceStatus.LATE) {
            return 0;
        }
        if (lateMinutes == null || lateMinutes <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "lateMinutes must be greater than zero for LATE status.");
        }
        return lateMinutes;
    }

    private BigDecimal effectiveDeduction(AttendanceRecord record) {
        if (record.isDeductionWaived()) {
            return BigDecimal.ZERO;
        }
        return zeroIfNull(record.getDeductionAmount());
    }

    private EmployeeProfileResponseDto toEmployeeResponse(EmployeeProfile employee) {
        EmployeeProfileResponseDto dto = new EmployeeProfileResponseDto();
        dto.setId(employee.getId());
        dto.setEmployeeCode(employee.getEmployeeCode());
        dto.setFullName(employee.getFullName());
        dto.setBranchId(employee.getBranch().getId());
        dto.setBranchCode(employee.getBranch().getCode());
        dto.setBranchName(employee.getBranch().getName());
        dto.setMonthlySalary(normalizeSalary(employee.getMonthlySalary()));
        dto.setActive(employee.isActive());
        return dto;
    }

    private AttendanceRecordResponseDto toAttendanceResponse(AttendanceRecord record) {
        AttendanceRecordResponseDto dto = new AttendanceRecordResponseDto();
        dto.setId(record.getId());
        dto.setEmployeeId(record.getEmployee().getId());
        dto.setEmployeeCode(record.getEmployee().getEmployeeCode());
        dto.setEmployeeName(record.getEmployee().getFullName());
        dto.setBranchId(record.getEmployee().getBranch().getId());
        dto.setBranchCode(record.getEmployee().getBranch().getCode());
        dto.setBranchName(record.getEmployee().getBranch().getName());
        dto.setAttendanceDate(record.getAttendanceDate());
        dto.setCheckInTime(record.getCheckInTime());
        dto.setStatus(record.getStatus());
        dto.setLateMinutes(record.getLateMinutes());
        dto.setDeductionAmount(zeroIfNull(record.getDeductionAmount()));
        dto.setDeductionWaived(record.isDeductionWaived());
        dto.setEffectiveDeduction(effectiveDeduction(record));
        dto.setWaivedBy(record.getWaivedBy());
        dto.setWaivedReason(record.getWaivedReason());
        dto.setWaivedAt(record.getWaivedAt());
        return dto;
    }

    private EmployeeProfile requireEmployeeById(Long id) {
        return employeeProfileRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Employee not found: " + id));
    }

    private AttendanceRecord requireAttendanceById(Long id) {
        return attendanceRecordRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Attendance record not found: " + id));
    }

    private YearMonth parseMonth(String monthValue) {
        if (monthValue == null || monthValue.isBlank()) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(monthValue.trim());
        } catch (Exception ex) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "month must be in format yyyy-MM.");
        }
    }

    private String normalizeEmployeeCode(String code) {
        String value = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        if (value.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "employeeCode is required.");
        }
        return value;
    }

    private String normalizeText(String value, String errorMessage) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorMessage);
        }
        return normalized;
    }

    private BigDecimal normalizeSalary(BigDecimal salary) {
        if (salary == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "monthlySalary is required.");
        }
        if (salary.compareTo(BigDecimal.ZERO) < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "monthlySalary must be >= 0.");
        }
        return salary.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String normalizeActor(String username) {
        String normalized = trimToNull(username);
        return normalized == null ? "system" : normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
