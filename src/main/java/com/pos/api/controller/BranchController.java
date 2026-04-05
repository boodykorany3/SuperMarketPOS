package com.pos.api.controller;

import com.pos.api.dto.BranchOverviewDto;
import com.pos.api.dto.BranchRequestDto;
import com.pos.api.dto.BranchResponseDto;
import com.pos.api.service.BranchService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/branches")
public class BranchController {

    private final BranchService branchService;

    public BranchController(BranchService branchService) {
        this.branchService = branchService;
    }

    @GetMapping
    public List<BranchResponseDto> getAll() {
        return branchService.getAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BranchResponseDto create(@Valid @RequestBody BranchRequestDto request) {
        return branchService.create(request);
    }

    @PutMapping("/{id}")
    public BranchResponseDto update(@PathVariable("id") Long id, @Valid @RequestBody BranchRequestDto request) {
        return branchService.update(id, request);
    }

    @GetMapping("/overview")
    public List<BranchOverviewDto> getOverview(
            @RequestParam(value = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return branchService.getOverview(date);
    }
}
