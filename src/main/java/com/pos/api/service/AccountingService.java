package com.pos.api.service;

import com.pos.api.dto.AccountRequestDto;
import com.pos.api.dto.AccountResponseDto;
import com.pos.api.dto.BalanceSheetLineDto;
import com.pos.api.dto.BalanceSheetResponseDto;
import com.pos.api.dto.JournalEntryRequestDto;
import com.pos.api.dto.JournalEntryResponseDto;
import com.pos.api.dto.JournalLineRequestDto;
import com.pos.api.dto.JournalLineResponseDto;
import com.pos.api.dto.ProfitLossLineDto;
import com.pos.api.dto.ProfitLossResponseDto;
import com.pos.api.dto.SalesJournalSyncResponseDto;
import com.pos.api.dto.TrialBalanceLineDto;
import com.pos.api.dto.TrialBalanceResponseDto;
import com.pos.api.entity.Account;
import com.pos.api.entity.AccountType;
import com.pos.api.entity.Branch;
import com.pos.api.entity.JournalEntry;
import com.pos.api.entity.JournalLine;
import com.pos.api.entity.Sale;
import com.pos.api.exception.ApiException;
import com.pos.api.repository.AccountRepository;
import com.pos.api.repository.JournalEntryRepository;
import com.pos.api.repository.JournalLineRepository;
import com.pos.api.repository.SaleRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class AccountingService {

    private static final String ACCOUNT_CASH_ON_HAND = "1110";
    private static final String ACCOUNT_SALES_REVENUE = "4100";
    private static final String REF_SALE_COMPLETED = "SALE_COMPLETED";
    private static final String REF_SALE_CANCELED = "SALE_CANCELED";
    private static final String REF_SALE_RETURNED = "SALE_RETURNED";

    private final AccountRepository accountRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final JournalLineRepository journalLineRepository;
    private final SaleRepository saleRepository;
    private final BranchService branchService;

    public AccountingService(AccountRepository accountRepository,
                             JournalEntryRepository journalEntryRepository,
                             JournalLineRepository journalLineRepository,
                             SaleRepository saleRepository,
                             BranchService branchService) {
        this.accountRepository = accountRepository;
        this.journalEntryRepository = journalEntryRepository;
        this.journalLineRepository = journalLineRepository;
        this.saleRepository = saleRepository;
        this.branchService = branchService;
    }

    public List<AccountResponseDto> getAccountsFlat() {
        return accountRepository.findAll().stream()
                .sorted(Comparator.comparing(Account::getCode))
                .map(this::toAccountResponse)
                .toList();
    }

    public List<AccountResponseDto> getAccountsTree() {
        List<Account> accounts = accountRepository.findAll().stream()
                .sorted(Comparator.comparing(Account::getCode))
                .toList();

        Map<Long, AccountResponseDto> nodes = new LinkedHashMap<>();
        for (Account account : accounts) {
            nodes.put(account.getId(), toAccountResponse(account));
        }

        List<AccountResponseDto> roots = new ArrayList<>();
        for (Account account : accounts) {
            AccountResponseDto node = nodes.get(account.getId());
            Account parent = account.getParent();
            if (parent == null || parent.getId() == null) {
                roots.add(node);
                continue;
            }

            AccountResponseDto parentNode = nodes.get(parent.getId());
            if (parentNode == null) {
                roots.add(node);
                continue;
            }
            parentNode.getChildren().add(node);
        }
        return roots;
    }

    @Transactional
    public AccountResponseDto createAccount(AccountRequestDto request) {
        String code = normalizeOptionalCode(request.getCode());
        if (code == null) {
            code = generateAutoAccountCode(request.getType());
        } else if (accountRepository.existsByCode(code)) {
            throw new ApiException(HttpStatus.CONFLICT, "كود الحساب موجود بالفعل.");
        }

        Account account = new Account();
        applyAccountRequest(account, request, true);
        account.setCode(code);
        return toAccountResponse(accountRepository.save(account));
    }

    @Transactional
    public AccountResponseDto updateAccount(Long id, AccountRequestDto request) {
        Account account = requireAccountById(id);
        String requestedCode = normalizeOptionalCode(request.getCode());
        String code = requestedCode == null ? account.getCode() : requestedCode;
        if (accountRepository.existsByCodeAndIdNot(code, id)) {
            throw new ApiException(HttpStatus.CONFLICT, "كود الحساب موجود بالفعل.");
        }

        applyAccountRequest(account, request, false);
        account.setCode(code);
        return toAccountResponse(accountRepository.save(account));
    }

    public List<JournalEntryResponseDto> getJournalEntries(LocalDate fromDate, LocalDate toDate, Long branchId) {
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "تاريخ البداية يجب أن يكون قبل أو يساوي تاريخ النهاية.");
        }
        if (branchId != null) {
            branchService.requireById(branchId);
        }

        return journalEntryRepository.findForListing(branchId, fromDate, toDate).stream()
                .map(this::toJournalResponse)
                .toList();
    }

    public TrialBalanceResponseDto getTrialBalance(LocalDate fromDate,
                                                   LocalDate toDate,
                                                   Long branchId,
                                                   boolean includeZeroLines) {
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "تاريخ البداية يجب أن يكون قبل أو يساوي تاريخ النهاية.");
        }

        Branch branch = null;
        if (branchId != null) {
            branch = branchService.requireById(branchId);
        }

        Map<Long, BigDecimal> totalDebitByAccount = new LinkedHashMap<>();
        Map<Long, BigDecimal> totalCreditByAccount = new LinkedHashMap<>();
        for (Object[] row : journalLineRepository.aggregateByAccount(fromDate, toDate, branchId)) {
            if (row == null || row.length < 3 || row[0] == null) {
                continue;
            }
            Long accountId = ((Number) row[0]).longValue();
            totalDebitByAccount.put(accountId, nonNull((BigDecimal) row[1]));
            totalCreditByAccount.put(accountId, nonNull((BigDecimal) row[2]));
        }

        List<TrialBalanceLineDto> lines = new ArrayList<>();
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;

        List<Account> accounts = accountRepository.findAll().stream()
                .sorted(Comparator.comparing(Account::getCode))
                .toList();

        for (Account account : accounts) {
            BigDecimal debit = nonNull(totalDebitByAccount.get(account.getId()));
            BigDecimal credit = nonNull(totalCreditByAccount.get(account.getId()));
            if (!includeZeroLines && debit.compareTo(BigDecimal.ZERO) == 0 && credit.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }

            TrialBalanceLineDto line = new TrialBalanceLineDto();
            line.setAccountId(account.getId());
            line.setAccountCode(account.getCode());
            line.setAccountName(account.getName());
            line.setAccountType(account.getType());
            line.setTotalDebit(debit);
            line.setTotalCredit(credit);
            BigDecimal balance = debit.subtract(credit);
            line.setBalance(balance);
            line.setBalanceNature(resolveBalanceNature(balance));
            lines.add(line);

            totalDebit = totalDebit.add(debit);
            totalCredit = totalCredit.add(credit);
        }

        TrialBalanceResponseDto response = new TrialBalanceResponseDto();
        response.setFromDate(fromDate);
        response.setToDate(toDate);
        if (branch != null) {
            response.setBranchId(branch.getId());
            response.setBranchCode(branch.getCode());
            response.setBranchName(branch.getName());
        }
        response.setLines(lines);
        response.setTotalDebit(totalDebit);
        response.setTotalCredit(totalCredit);
        response.setDifference(totalDebit.subtract(totalCredit));
        return response;
    }

    public ProfitLossResponseDto getProfitLoss(LocalDate fromDate, LocalDate toDate, Long branchId) {
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "تاريخ البداية يجب أن يكون قبل أو يساوي تاريخ النهاية.");
        }

        Branch branch = null;
        if (branchId != null) {
            branch = branchService.requireById(branchId);
        }

        Map<Long, Account> accountById = new LinkedHashMap<>();
        for (Account account : accountRepository.findAll()) {
            accountById.put(account.getId(), account);
        }

        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal totalExpense = BigDecimal.ZERO;
        List<ProfitLossLineDto> revenueLines = new ArrayList<>();
        List<ProfitLossLineDto> expenseLines = new ArrayList<>();

        for (Object[] row : journalLineRepository.aggregateByAccount(fromDate, toDate, branchId)) {
            if (row == null || row.length < 3 || row[0] == null) {
                continue;
            }

            Long accountId = ((Number) row[0]).longValue();
            Account account = accountById.get(accountId);
            if (account == null) {
                continue;
            }

            BigDecimal debit = nonNull((BigDecimal) row[1]);
            BigDecimal credit = nonNull((BigDecimal) row[2]);

            if (account.getType() == AccountType.REVENUE) {
                BigDecimal amount = credit.subtract(debit);
                if (amount.compareTo(BigDecimal.ZERO) == 0) {
                    continue;
                }
                ProfitLossLineDto line = toProfitLossLine(account, amount);
                revenueLines.add(line);
                totalRevenue = totalRevenue.add(amount);
            } else if (account.getType() == AccountType.EXPENSE) {
                BigDecimal amount = debit.subtract(credit);
                if (amount.compareTo(BigDecimal.ZERO) == 0) {
                    continue;
                }
                ProfitLossLineDto line = toProfitLossLine(account, amount);
                expenseLines.add(line);
                totalExpense = totalExpense.add(amount);
            }
        }

        revenueLines.sort(Comparator.comparing(ProfitLossLineDto::getAccountCode, Comparator.nullsLast(String::compareTo)));
        expenseLines.sort(Comparator.comparing(ProfitLossLineDto::getAccountCode, Comparator.nullsLast(String::compareTo)));

        ProfitLossResponseDto response = new ProfitLossResponseDto();
        response.setFromDate(fromDate);
        response.setToDate(toDate);
        if (branch != null) {
            response.setBranchId(branch.getId());
            response.setBranchCode(branch.getCode());
            response.setBranchName(branch.getName());
        }
        response.setRevenueLines(revenueLines);
        response.setExpenseLines(expenseLines);
        response.setTotalRevenue(totalRevenue);
        response.setTotalExpense(totalExpense);
        response.setNetProfit(totalRevenue.subtract(totalExpense));
        return response;
    }

    public BalanceSheetResponseDto getBalanceSheet(LocalDate asOfDate, Long branchId) {
        LocalDate balanceDate = asOfDate == null ? LocalDate.now() : asOfDate;

        Branch branch = null;
        if (branchId != null) {
            branch = branchService.requireById(branchId);
        }

        Map<Long, Account> accountById = new LinkedHashMap<>();
        for (Account account : accountRepository.findAll()) {
            accountById.put(account.getId(), account);
        }

        BigDecimal totalAssets = BigDecimal.ZERO;
        BigDecimal totalLiabilities = BigDecimal.ZERO;
        BigDecimal totalEquity = BigDecimal.ZERO;
        List<BalanceSheetLineDto> assetLines = new ArrayList<>();
        List<BalanceSheetLineDto> liabilityLines = new ArrayList<>();
        List<BalanceSheetLineDto> equityLines = new ArrayList<>();

        for (Object[] row : journalLineRepository.aggregateByAccount(null, balanceDate, branchId)) {
            if (row == null || row.length < 3 || row[0] == null) {
                continue;
            }

            Long accountId = ((Number) row[0]).longValue();
            Account account = accountById.get(accountId);
            if (account == null) {
                continue;
            }

            BigDecimal debit = nonNull((BigDecimal) row[1]);
            BigDecimal credit = nonNull((BigDecimal) row[2]);

            if (account.getType() == AccountType.ASSET) {
                BigDecimal amount = debit.subtract(credit);
                if (amount.compareTo(BigDecimal.ZERO) == 0) {
                    continue;
                }
                assetLines.add(toBalanceSheetLine(account, amount));
                totalAssets = totalAssets.add(amount);
            } else if (account.getType() == AccountType.LIABILITY) {
                BigDecimal amount = credit.subtract(debit);
                if (amount.compareTo(BigDecimal.ZERO) == 0) {
                    continue;
                }
                liabilityLines.add(toBalanceSheetLine(account, amount));
                totalLiabilities = totalLiabilities.add(amount);
            } else if (account.getType() == AccountType.EQUITY) {
                BigDecimal amount = credit.subtract(debit);
                if (amount.compareTo(BigDecimal.ZERO) == 0) {
                    continue;
                }
                equityLines.add(toBalanceSheetLine(account, amount));
                totalEquity = totalEquity.add(amount);
            }
        }

        assetLines.sort(Comparator.comparing(BalanceSheetLineDto::getAccountCode, Comparator.nullsLast(String::compareTo)));
        liabilityLines.sort(Comparator.comparing(BalanceSheetLineDto::getAccountCode, Comparator.nullsLast(String::compareTo)));
        equityLines.sort(Comparator.comparing(BalanceSheetLineDto::getAccountCode, Comparator.nullsLast(String::compareTo)));

        BalanceSheetResponseDto response = new BalanceSheetResponseDto();
        response.setAsOfDate(balanceDate);
        if (branch != null) {
            response.setBranchId(branch.getId());
            response.setBranchCode(branch.getCode());
            response.setBranchName(branch.getName());
        }
        response.setAssetLines(assetLines);
        response.setLiabilityLines(liabilityLines);
        response.setEquityLines(equityLines);
        response.setTotalAssets(totalAssets);
        response.setTotalLiabilities(totalLiabilities);
        response.setTotalEquity(totalEquity);
        BigDecimal totalLiabilitiesAndEquity = totalLiabilities.add(totalEquity);
        response.setTotalLiabilitiesAndEquity(totalLiabilitiesAndEquity);
        response.setDifference(totalAssets.subtract(totalLiabilitiesAndEquity));
        return response;
    }

    @Transactional
    public SalesJournalSyncResponseDto syncSalesJournalEntries(LocalDate fromDate,
                                                               LocalDate toDate,
                                                               Long branchId,
                                                               String actor) {
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "تاريخ البداية يجب أن يكون قبل أو يساوي تاريخ النهاية.");
        }

        Branch branch = null;
        if (branchId != null) {
            branch = branchService.requireById(branchId);
        }

        LocalDateTime fromDateTime = fromDate == null ? null : fromDate.atStartOfDay();
        LocalDateTime toDateTimeExclusive = toDate == null ? null : toDate.plusDays(1).atStartOfDay();

        List<Sale> sales = saleRepository.findForAccountingSync(branchId, fromDateTime, toDateTimeExclusive);

        SalesJournalSyncResponseDto response = new SalesJournalSyncResponseDto();
        response.setFromDate(fromDate);
        response.setToDate(toDate);
        if (branch != null) {
            response.setBranchId(branch.getId());
            response.setBranchCode(branch.getCode());
            response.setBranchName(branch.getName());
        }

        int createdEntries = 0;
        int existingEntries = 0;
        int skippedSales = 0;
        int failedEntries = 0;
        List<String> failedInvoices = new ArrayList<>();

        for (Sale sale : sales) {
            String referenceType = resolveSaleReferenceType(sale.getStatus());
            if (referenceType == null) {
                skippedSales++;
                continue;
            }
            if (sale.getTotalAmount() == null || sale.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
                skippedSales++;
                continue;
            }

            if (journalEntryRepository.existsByReferenceTypeAndReferenceId(referenceType, sale.getId())) {
                existingEntries++;
                continue;
            }

            try {
                createSaleJournalEntry(sale, referenceType, actor);
                createdEntries++;
            } catch (Exception ex) {
                failedEntries++;
                failedInvoices.add(sale.getInvoiceNumber() == null ? ("بيع#" + sale.getId()) : sale.getInvoiceNumber());
            }
        }

        response.setProcessedSales(sales.size());
        response.setCreatedEntries(createdEntries);
        response.setExistingEntries(existingEntries);
        response.setSkippedSales(skippedSales);
        response.setFailedEntries(failedEntries);
        response.setFailedInvoices(failedInvoices);
        return response;
    }

    @Transactional
    public void postSaleCompletedJournal(Sale sale) {
        createSaleJournalEntry(sale, REF_SALE_COMPLETED, saleActor(sale));
    }

    @Transactional
    public void postSaleCanceledJournal(Sale sale) {
        createSaleJournalEntry(sale, REF_SALE_CANCELED, saleActor(sale));
    }

    @Transactional
    public void postSaleReturnedJournal(Sale sale) {
        createSaleJournalEntry(sale, REF_SALE_RETURNED, saleActor(sale));
    }

    @Transactional
    public JournalEntryResponseDto createJournalEntry(JournalEntryRequestDto request, String createdBy) {
        JournalEntry entry = new JournalEntry();
        entry.setEntryDate(request.getEntryDate() == null ? LocalDate.now() : request.getEntryDate());
        entry.setDescription(normalizeText(request.getDescription(), "وصف القيد مطلوب."));
        entry.setReferenceType(trimToNull(request.getReferenceType()));
        entry.setReferenceId(request.getReferenceId());
        entry.setCreatedBy(resolveCreatedBy(createdBy));

        Branch branch = request.getBranchId() == null ? null : branchService.requireById(request.getBranchId());
        entry.setBranch(branch);

        List<JournalLine> lines = new ArrayList<>();
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;

        for (JournalLineRequestDto lineRequest : request.getLines()) {
            Account account = requireAccountById(lineRequest.getAccountId());
            if (!account.isActive()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "الحساب غير نشط: " + account.getCode());
            }
            if (!account.isPostingAllowed()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "الحساب لا يسمح بالترحيل: " + account.getCode());
            }

            BigDecimal debit = normalizeMoney(lineRequest.getDebit());
            BigDecimal credit = normalizeMoney(lineRequest.getCredit());
            validateJournalLineAmounts(debit, credit);

            JournalLine line = new JournalLine();
            line.setEntry(entry);
            line.setAccount(account);
            line.setDebit(debit);
            line.setCredit(credit);
            line.setNote(trimToNull(lineRequest.getNote()));
            lines.add(line);

            totalDebit = totalDebit.add(debit);
            totalCredit = totalCredit.add(credit);
        }

        if (totalDebit.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "إجمالي القيد يجب أن يكون أكبر من صفر.");
        }
        if (totalDebit.compareTo(totalCredit) != 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "القيد غير متوازن.");
        }

        entry.setLines(lines);
        JournalEntry saved = journalEntryRepository.save(entry);
        return toJournalResponse(saved);
    }

    @Transactional
    public void ensureDefaultChartOfAccounts() {
        Map<String, Account> byCode = new LinkedHashMap<>();
        for (Account account : accountRepository.findAll()) {
            byCode.put(account.getCode().toUpperCase(Locale.ROOT), account);
        }

        createBootstrapAccount(byCode, "1000", "الأصول", AccountType.ASSET, null, false);
        createBootstrapAccount(byCode, "1100", "الأصول المتداولة", AccountType.ASSET, "1000", false);
        createBootstrapAccount(byCode, "1110", "النقدية بالصندوق", AccountType.ASSET, "1100", true);
        createBootstrapAccount(byCode, "1120", "الحسابات البنكية", AccountType.ASSET, "1100", true);
        createBootstrapAccount(byCode, "1130", "المخزون", AccountType.ASSET, "1100", true);
        createBootstrapAccount(byCode, "1200", "الذمم المدينة", AccountType.ASSET, "1000", true);

        createBootstrapAccount(byCode, "2000", "الالتزامات", AccountType.LIABILITY, null, false);
        createBootstrapAccount(byCode, "2100", "الذمم الدائنة", AccountType.LIABILITY, "2000", true);
        createBootstrapAccount(byCode, "2200", "مصروفات مستحقة", AccountType.LIABILITY, "2000", true);
        createBootstrapAccount(byCode, "2210", "رواتب مستحقة", AccountType.LIABILITY, "2000", true);
        createBootstrapAccount(byCode, "2220", "استقطاعات موظفين مستحقة", AccountType.LIABILITY, "2000", true);

        createBootstrapAccount(byCode, "3000", "حقوق الملكية", AccountType.EQUITY, null, false);
        createBootstrapAccount(byCode, "3100", "رأس مال المالك", AccountType.EQUITY, "3000", true);
        createBootstrapAccount(byCode, "3200", "أرباح محتجزة", AccountType.EQUITY, "3000", true);

        createBootstrapAccount(byCode, "4000", "الإيرادات", AccountType.REVENUE, null, false);
        createBootstrapAccount(byCode, "4100", "إيراد المبيعات", AccountType.REVENUE, "4000", true);
        createBootstrapAccount(byCode, "4200", "إيراد التوصيل", AccountType.REVENUE, "4000", true);

        createBootstrapAccount(byCode, "5000", "المصروفات", AccountType.EXPENSE, null, false);
        createBootstrapAccount(byCode, "5100", "تكلفة البضاعة المباعة", AccountType.EXPENSE, "5000", true);
        createBootstrapAccount(byCode, "5200", "مصروف الرواتب", AccountType.EXPENSE, "5000", true);
        createBootstrapAccount(byCode, "5300", "مصروف الإيجار", AccountType.EXPENSE, "5000", true);
        createBootstrapAccount(byCode, "5400", "مصروف المرافق", AccountType.EXPENSE, "5000", true);
        createBootstrapAccount(byCode, "5500", "مصروف الخصومات", AccountType.EXPENSE, "5000", true);
    }

    private void createBootstrapAccount(Map<String, Account> byCode,
                                        String code,
                                        String name,
                                        AccountType type,
                                        String parentCode,
                                        boolean postingAllowed) {
        String normalizedCode = normalizeCode(code);
        if (byCode.containsKey(normalizedCode)) {
            return;
        }

        Account parent = null;
        if (parentCode != null) {
            parent = byCode.get(normalizeCode(parentCode));
            if (parent == null) {
                throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "الحساب الأب الافتراضي غير موجود: " + parentCode);
            }
            if (parent.isPostingAllowed()) {
                parent.setPostingAllowed(false);
                accountRepository.save(parent);
            }
        }

        Account account = new Account();
        account.setCode(normalizedCode);
        account.setName(name);
        account.setType(type);
        account.setParent(parent);
        account.setLevel(parent == null ? 1 : ((parent.getLevel() == null ? 1 : parent.getLevel()) + 1));
        account.setPostingAllowed(postingAllowed);
        account.setActive(true);
        Account saved = accountRepository.save(account);
        byCode.put(normalizedCode, saved);
    }

    private void applyAccountRequest(Account account, AccountRequestDto request, boolean creating) {
        account.setName(normalizeText(request.getName(), "اسم الحساب مطلوب."));
        account.setType(request.getType());

        Account parent = null;
        if (request.getParentId() != null) {
            if (account.getId() != null && account.getId().equals(request.getParentId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "لا يمكن أن يكون الحساب أبًا لنفسه.");
            }
            parent = requireAccountById(request.getParentId());
            ensureNoCycle(account, parent);
            if (parent.getType() != request.getType()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "نوع الحساب الأب يجب أن يطابق نوع الحساب الفرعي.");
            }
            if (parent.isPostingAllowed()) {
                parent.setPostingAllowed(false);
                accountRepository.save(parent);
            }
        }

        account.setParent(parent);
        account.setLevel(parent == null ? 1 : ((parent.getLevel() == null ? 1 : parent.getLevel()) + 1));

        boolean postingAllowed = request.getPostingAllowed() != null
                ? request.getPostingAllowed()
                : (creating || account.isPostingAllowed());

        if (account.getId() != null && postingAllowed && accountRepository.existsByParentId(account.getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "الحساب الذي لديه حسابات فرعية لا يمكن السماح له بالترحيل المباشر.");
        }
        account.setPostingAllowed(postingAllowed);

        if (request.getActive() != null) {
            account.setActive(request.getActive());
        } else if (creating) {
            account.setActive(true);
        }
    }

    private void ensureNoCycle(Account account, Account parent) {
        if (account.getId() == null) {
            return;
        }
        Account current = parent;
        while (current != null) {
            if (account.getId().equals(current.getId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "لا يُسمح بدورة داخل شجرة الحسابات.");
            }
            current = current.getParent();
        }
    }

    private void validateJournalLineAmounts(BigDecimal debit, BigDecimal credit) {
        boolean debitPositive = debit.compareTo(BigDecimal.ZERO) > 0;
        boolean creditPositive = credit.compareTo(BigDecimal.ZERO) > 0;
        if (debitPositive == creditPositive) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "كل بند يجب أن يحتوي على مدين أو دائن فقط.");
        }
    }

    private BigDecimal normalizeMoney(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return value.max(BigDecimal.ZERO);
    }

    private ProfitLossLineDto toProfitLossLine(Account account, BigDecimal amount) {
        ProfitLossLineDto line = new ProfitLossLineDto();
        line.setAccountId(account.getId());
        line.setAccountCode(account.getCode());
        line.setAccountName(account.getName());
        line.setAccountType(account.getType());
        line.setAmount(amount);
        return line;
    }

    private BalanceSheetLineDto toBalanceSheetLine(Account account, BigDecimal amount) {
        BalanceSheetLineDto line = new BalanceSheetLineDto();
        line.setAccountId(account.getId());
        line.setAccountCode(account.getCode());
        line.setAccountName(account.getName());
        line.setAccountType(account.getType());
        line.setAmount(amount);
        return line;
    }

    private String resolveSaleReferenceType(String saleStatus) {
        if (Sale.STATUS_COMPLETED.equalsIgnoreCase(saleStatus)) {
            return REF_SALE_COMPLETED;
        }
        if (Sale.STATUS_CANCELED.equalsIgnoreCase(saleStatus)) {
            return REF_SALE_CANCELED;
        }
        if (Sale.STATUS_RETURNED.equalsIgnoreCase(saleStatus)) {
            return REF_SALE_RETURNED;
        }
        return null;
    }

    private String saleActor(Sale sale) {
        if (sale == null || sale.getUser() == null || sale.getUser().getUsername() == null) {
            return "النظام";
        }
        return sale.getUser().getUsername();
    }

    private void createSaleJournalEntry(Sale sale, String referenceType, String actor) {
        if (sale == null || sale.getId() == null || referenceType == null || referenceType.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "بيانات ترحيل قيد البيع غير صالحة.");
        }
        if (journalEntryRepository.existsByReferenceTypeAndReferenceId(referenceType, sale.getId())) {
            return;
        }

        BigDecimal amount = nonNull(sale.getTotalAmount());
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "إجمالي البيع يجب أن يكون أكبر من صفر للترحيل المحاسبي.");
        }

        Account cashAccount = requirePostingAccountByCode(ACCOUNT_CASH_ON_HAND);
        Account revenueAccount = requirePostingAccountByCode(ACCOUNT_SALES_REVENUE);

        JournalEntry entry = new JournalEntry();
        entry.setEntryDate(sale.getDate() == null ? LocalDate.now() : sale.getDate().toLocalDate());
        entry.setDescription(buildSaleJournalDescription(sale, referenceType));
        entry.setReferenceType(referenceType);
        entry.setReferenceId(sale.getId());
        entry.setCreatedBy(resolveCreatedBy(actor));
        Branch branch = sale.getBranch() != null
                ? sale.getBranch()
                : (sale.getUser() == null ? null : sale.getUser().getBranch());
        entry.setBranch(branch);

        boolean reverse = REF_SALE_CANCELED.equals(referenceType) || REF_SALE_RETURNED.equals(referenceType);

        JournalLine firstLine = new JournalLine();
        firstLine.setEntry(entry);
        firstLine.setAccount(reverse ? revenueAccount : cashAccount);
        firstLine.setDebit(amount);
        firstLine.setCredit(BigDecimal.ZERO);
        firstLine.setNote("ترحيل تلقائي");

        JournalLine secondLine = new JournalLine();
        secondLine.setEntry(entry);
        secondLine.setAccount(reverse ? cashAccount : revenueAccount);
        secondLine.setDebit(BigDecimal.ZERO);
        secondLine.setCredit(amount);
        secondLine.setNote("ترحيل تلقائي");

        List<JournalLine> lines = new ArrayList<>();
        lines.add(firstLine);
        lines.add(secondLine);
        entry.setLines(lines);

        journalEntryRepository.save(entry);
    }

    private Account requirePostingAccountByCode(String code) {
        Account account = accountRepository.findByCode(normalizeCode(code))
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "الحساب المطلوب غير موجود: " + code));

        if (!account.isActive()) {
            throw new ApiException(HttpStatus.CONFLICT, "الحساب المطلوب غير نشط: " + code);
        }
        if (!account.isPostingAllowed()) {
            throw new ApiException(HttpStatus.CONFLICT, "الحساب المطلوب لا يسمح بالترحيل: " + code);
        }
        return account;
    }

    private String buildSaleJournalDescription(Sale sale, String referenceType) {
        String invoiceNumber = sale.getInvoiceNumber() == null ? ("بيع#" + sale.getId()) : sale.getInvoiceNumber();
        if (REF_SALE_CANCELED.equals(referenceType)) {
            return "قيد عكسي تلقائي لبيع ملغي " + invoiceNumber;
        }
        if (REF_SALE_RETURNED.equals(referenceType)) {
            return "قيد عكسي تلقائي لمرتجع بيع " + invoiceNumber;
        }
        return "ترحيل تلقائي لبيع مكتمل " + invoiceNumber;
    }

    private AccountResponseDto toAccountResponse(Account account) {
        AccountResponseDto dto = new AccountResponseDto();
        dto.setId(account.getId());
        dto.setCode(account.getCode());
        dto.setName(account.getName());
        dto.setType(account.getType());
        dto.setParentId(account.getParent() == null ? null : account.getParent().getId());
        dto.setParentCode(account.getParent() == null ? null : account.getParent().getCode());
        dto.setLevel(account.getLevel());
        dto.setPostingAllowed(account.isPostingAllowed());
        dto.setActive(account.isActive());
        return dto;
    }

    private JournalEntryResponseDto toJournalResponse(JournalEntry entry) {
        JournalEntryResponseDto dto = new JournalEntryResponseDto();
        dto.setId(entry.getId());
        dto.setEntryDate(entry.getEntryDate());
        dto.setDescription(entry.getDescription());
        dto.setReferenceType(entry.getReferenceType());
        dto.setReferenceId(entry.getReferenceId());
        dto.setCreatedBy(entry.getCreatedBy());
        if (entry.getBranch() != null) {
            dto.setBranchId(entry.getBranch().getId());
            dto.setBranchCode(entry.getBranch().getCode());
            dto.setBranchName(entry.getBranch().getName());
        }

        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        List<JournalLineResponseDto> lines = new ArrayList<>();
        for (JournalLine line : entry.getLines()) {
            JournalLineResponseDto lineDto = new JournalLineResponseDto();
            lineDto.setId(line.getId());
            lineDto.setAccountId(line.getAccount().getId());
            lineDto.setAccountCode(line.getAccount().getCode());
            lineDto.setAccountName(line.getAccount().getName());
            lineDto.setDebit(line.getDebit());
            lineDto.setCredit(line.getCredit());
            lineDto.setNote(line.getNote());
            lines.add(lineDto);

            totalDebit = totalDebit.add(line.getDebit() == null ? BigDecimal.ZERO : line.getDebit());
            totalCredit = totalCredit.add(line.getCredit() == null ? BigDecimal.ZERO : line.getCredit());
        }
        lines.sort(Comparator.comparing(JournalLineResponseDto::getAccountCode, Comparator.nullsLast(String::compareTo)));

        dto.setLines(lines);
        dto.setTotalDebit(totalDebit);
        dto.setTotalCredit(totalCredit);
        return dto;
    }

    private Account requireAccountById(Long id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "الحساب غير موجود: " + id));
    }

    private String generateAutoAccountCode(AccountType type) {
        String prefix = accountTypePrefix(type);
        int sequence = 1;
        while (sequence <= 999999) {
            String candidate = prefix + "-" + String.format(Locale.ROOT, "%06d", sequence);
            if (!accountRepository.existsByCode(candidate)) {
                return candidate;
            }
            sequence++;
        }
        throw new ApiException(HttpStatus.CONFLICT, "Unable to generate an internal account code.");
    }

    private String accountTypePrefix(AccountType type) {
        if (type == null) {
            return "ACC";
        }
        return switch (type) {
            case ASSET -> "AST";
            case LIABILITY -> "LIA";
            case EQUITY -> "EQT";
            case REVENUE -> "REV";
            case EXPENSE -> "EXP";
        };
    }

    private String normalizeOptionalCode(String code) {
        String value = trimToNull(code);
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }

    private String normalizeCode(String code) {
        String value = code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
        if (value.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "كود الحساب مطلوب.");
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

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String resolveCreatedBy(String createdBy) {
        String value = trimToNull(createdBy);
        return value == null ? "النظام" : value;
    }

    private String resolveBalanceNature(BigDecimal balance) {
        if (balance.compareTo(BigDecimal.ZERO) > 0) {
            return "مدين";
        }
        if (balance.compareTo(BigDecimal.ZERO) < 0) {
            return "دائن";
        }
        return "صفر";
    }

    private BigDecimal nonNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
