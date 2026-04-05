package com.pos.api.service;

import com.pos.api.dto.BranchOverviewDto;
import com.pos.api.dto.BranchRequestDto;
import com.pos.api.dto.BranchResponseDto;
import com.pos.api.entity.Branch;
import com.pos.api.entity.Sale;
import com.pos.api.exception.ApiException;
import com.pos.api.repository.BranchRepository;
import com.pos.api.repository.SaleRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class BranchService {

    private final BranchRepository branchRepository;
    private final SaleRepository saleRepository;

    public BranchService(BranchRepository branchRepository, SaleRepository saleRepository) {
        this.branchRepository = branchRepository;
        this.saleRepository = saleRepository;
    }

    public List<BranchResponseDto> getAll() {
        return branchRepository.findAllByOrderByCodeAsc().stream().map(this::toResponse).toList();
    }

    @Transactional
    public Branch ensureMainBranch() {
        return branchRepository.findByMainBranchTrue().orElseGet(() -> {
            Branch branch = new Branch();
            branch.setCode("MAIN");
            branch.setName("Main Branch");
            branch.setMainBranch(true);
            branch.setActive(true);
            return branchRepository.save(branch);
        });
    }

    public Branch requireById(Long id) {
        return branchRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Branch not found: " + id));
    }

    @Transactional
    public BranchResponseDto create(BranchRequestDto request) {
        String code = normalizeCode(request.getCode());
        if (branchRepository.existsByCode(code)) {
            throw new ApiException(HttpStatus.CONFLICT, "Branch code already exists.");
        }

        Branch branch = new Branch();
        applyRequest(branch, request, true);
        branch.setCode(code);
        return toResponse(branchRepository.save(branch));
    }

    @Transactional
    public BranchResponseDto update(Long id, BranchRequestDto request) {
        Branch branch = requireById(id);
        String code = normalizeCode(request.getCode());
        if (!branch.getCode().equalsIgnoreCase(code) && branchRepository.existsByCode(code)) {
            throw new ApiException(HttpStatus.CONFLICT, "Branch code already exists.");
        }

        applyRequest(branch, request, false);
        branch.setCode(code);
        return toResponse(branchRepository.save(branch));
    }

    public List<BranchOverviewDto> getOverview(LocalDate baseDate) {
        LocalDate referenceDate = baseDate == null ? LocalDate.now() : baseDate;
        LocalDateTime todayStart = referenceDate.atStartOfDay();
        LocalDateTime tomorrowStart = referenceDate.plusDays(1).atStartOfDay();

        YearMonth thisMonth = YearMonth.from(referenceDate);
        YearMonth prevMonth = thisMonth.minusMonths(1);
        LocalDateTime thisMonthStart = thisMonth.atDay(1).atStartOfDay();
        LocalDateTime nextMonthStart = thisMonth.plusMonths(1).atDay(1).atStartOfDay();
        LocalDateTime prevMonthStart = prevMonth.atDay(1).atStartOfDay();

        List<BranchOverviewDto> output = new ArrayList<>();
        for (Branch branch : branchRepository.findAllByOrderByCodeAsc()) {
            BranchOverviewDto dto = new BranchOverviewDto();
            dto.setBranchId(branch.getId());
            dto.setBranchCode(branch.getCode());
            dto.setBranchName(branch.getName());
            dto.setTodayInvoices(saleRepository.countByBranchAndStatusBetween(branch.getId(), Sale.STATUS_COMPLETED, todayStart, tomorrowStart));
            dto.setTodaySales(nonNull(saleRepository.sumByBranchAndStatusBetween(branch.getId(), Sale.STATUS_COMPLETED, todayStart, tomorrowStart)));
            dto.setCurrentMonthInvoices(saleRepository.countByBranchAndStatusBetween(branch.getId(), Sale.STATUS_COMPLETED, thisMonthStart, nextMonthStart));
            dto.setCurrentMonthSales(nonNull(saleRepository.sumByBranchAndStatusBetween(branch.getId(), Sale.STATUS_COMPLETED, thisMonthStart, nextMonthStart)));
            dto.setPreviousMonthInvoices(saleRepository.countByBranchAndStatusBetween(branch.getId(), Sale.STATUS_COMPLETED, prevMonthStart, thisMonthStart));
            dto.setPreviousMonthSales(nonNull(saleRepository.sumByBranchAndStatusBetween(branch.getId(), Sale.STATUS_COMPLETED, prevMonthStart, thisMonthStart)));
            output.add(dto);
        }

        return output;
    }

    private void applyRequest(Branch branch, BranchRequestDto request, boolean creating) {
        String name = request.getName() == null ? "" : request.getName().trim();
        if (name.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Branch name is required.");
        }
        branch.setName(name);

        if (request.getMainBranch() != null && request.getMainBranch()) {
            clearCurrentMainBranch();
            branch.setMainBranch(true);
        } else if (creating && branchRepository.findByMainBranchTrue().isEmpty()) {
            branch.setMainBranch(true);
        } else if (request.getMainBranch() != null) {
            branch.setMainBranch(false);
        }

        if (request.getActive() != null) {
            branch.setActive(request.getActive());
        } else if (creating) {
            branch.setActive(true);
        }
    }

    @Transactional
    private void clearCurrentMainBranch() {
        branchRepository.findByMainBranchTrue().ifPresent(existing -> {
            existing.setMainBranch(false);
            branchRepository.save(existing);
        });
    }

    private String normalizeCode(String code) {
        String value = code == null ? "" : code.trim().toUpperCase();
        if (value.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Branch code is required.");
        }
        return value;
    }

    private BranchResponseDto toResponse(Branch branch) {
        BranchResponseDto dto = new BranchResponseDto();
        dto.setId(branch.getId());
        dto.setCode(branch.getCode());
        dto.setName(branch.getName());
        dto.setMainBranch(branch.isMainBranch());
        dto.setActive(branch.isActive());
        return dto;
    }

    private BigDecimal nonNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
