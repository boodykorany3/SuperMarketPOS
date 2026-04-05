package com.pos.api.controller;

import com.pos.api.dto.AccountRequestDto;
import com.pos.api.dto.AccountResponseDto;
import com.pos.api.dto.BalanceSheetResponseDto;
import com.pos.api.dto.JournalEntryRequestDto;
import com.pos.api.dto.JournalEntryResponseDto;
import com.pos.api.dto.ProfitLossResponseDto;
import com.pos.api.dto.SalesJournalSyncResponseDto;
import com.pos.api.dto.TrialBalanceResponseDto;
import com.pos.api.service.AccountingService;
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
public class AccountingController {

    private final AccountingService accountingService;

    public AccountingController(AccountingService accountingService) {
        this.accountingService = accountingService;
    }

    @GetMapping("/accounts")
    public List<AccountResponseDto> getAccounts(
            @RequestParam(value = "tree", defaultValue = "false") boolean tree
    ) {
        return tree ? accountingService.getAccountsTree() : accountingService.getAccountsFlat();
    }

    @PostMapping("/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponseDto createAccount(@Valid @RequestBody AccountRequestDto request) {
        return accountingService.createAccount(request);
    }

    @PutMapping("/accounts/{id}")
    public AccountResponseDto updateAccount(@PathVariable("id") Long id,
                                            @Valid @RequestBody AccountRequestDto request) {
        return accountingService.updateAccount(id, request);
    }

    @GetMapping("/accounts/trial-balance")
    public TrialBalanceResponseDto getTrialBalance(
            @RequestParam(value = "branchId", required = false) Long branchId,
            @RequestParam(value = "fromDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(value = "toDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(value = "includeZero", defaultValue = "false") boolean includeZero
    ) {
        return accountingService.getTrialBalance(fromDate, toDate, branchId, includeZero);
    }

    @GetMapping("/accounts/profit-loss")
    public ProfitLossResponseDto getProfitLoss(
            @RequestParam(value = "branchId", required = false) Long branchId,
            @RequestParam(value = "fromDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(value = "toDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate
    ) {
        return accountingService.getProfitLoss(fromDate, toDate, branchId);
    }

    @GetMapping("/accounts/balance-sheet")
    public BalanceSheetResponseDto getBalanceSheet(
            @RequestParam(value = "branchId", required = false) Long branchId,
            @RequestParam(value = "asOfDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOfDate
    ) {
        return accountingService.getBalanceSheet(asOfDate, branchId);
    }

    @PostMapping("/accounts/sales-sync")
    public SalesJournalSyncResponseDto syncSalesAccounting(Authentication authentication,
                                                           @RequestParam(value = "branchId", required = false) Long branchId,
                                                           @RequestParam(value = "fromDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
                                                           @RequestParam(value = "toDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return accountingService.syncSalesJournalEntries(fromDate, toDate, branchId,
                authentication == null ? null : authentication.getName());
    }

    @GetMapping("/journal-entries")
    public List<JournalEntryResponseDto> getJournalEntries(
            @RequestParam(value = "branchId", required = false) Long branchId,
            @RequestParam(value = "fromDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(value = "toDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate
    ) {
        return accountingService.getJournalEntries(fromDate, toDate, branchId);
    }

    @PostMapping("/journal-entries")
    @ResponseStatus(HttpStatus.CREATED)
    public JournalEntryResponseDto createJournalEntry(Authentication authentication,
                                                      @Valid @RequestBody JournalEntryRequestDto request) {
        return accountingService.createJournalEntry(request, authentication == null ? null : authentication.getName());
    }
}
