package com.pos.api.controller;

import com.pos.api.dto.SaleRequestDto;
import com.pos.api.dto.SaleResponseDto;
import com.pos.api.service.SaleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/sales")
public class SaleController {

    private final SaleService saleService;

    public SaleController(SaleService saleService) {
        this.saleService = saleService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SaleResponseDto create(@Valid @RequestBody SaleRequestDto request) {
        return saleService.createSale(request);
    }

    @GetMapping
    public List<SaleResponseDto> getAll(
            @RequestParam(value = "branchId", required = false) Long branchId
    ) {
        return saleService.getAll(branchId);
    }

    @GetMapping("/{id}")
    public SaleResponseDto getById(@PathVariable("id") Long id) {
        return saleService.getById(id);
    }

    @PostMapping("/{id}/cancel")
    public SaleResponseDto cancel(@PathVariable("id") Long id) {
        return saleService.cancelSale(id);
    }

    @PostMapping("/{id}/return")
    public SaleResponseDto returnInvoice(@PathVariable("id") Long id) {
        return saleService.returnSale(id);
    }
}
