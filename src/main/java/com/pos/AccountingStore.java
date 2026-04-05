package com.pos;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class AccountingStore {
    private static final ObservableList<AccountingAccount> accounts = FXCollections.observableArrayList();
    private static final ObservableList<AccountingJournalEntry> journalEntries = FXCollections.observableArrayList();
    private static final ObservableList<TrialBalanceLine> trialBalanceLines = FXCollections.observableArrayList();
    private static final ObservableList<AccountingBranch> branches = FXCollections.observableArrayList();

    private static TrialBalanceReport lastTrialBalance;

    private AccountingStore() {}

    public static ObservableList<AccountingAccount> getAccounts() {
        return accounts;
    }

    public static ObservableList<AccountingJournalEntry> getJournalEntries() {
        return journalEntries;
    }

    public static ObservableList<TrialBalanceLine> getTrialBalanceLines() {
        return trialBalanceLines;
    }

    public static ObservableList<AccountingBranch> getBranches() {
        return branches;
    }

    public static TrialBalanceReport getLastTrialBalance() {
        return lastTrialBalance;
    }

    public static void reloadAccounts() {
        requireApiMode();
        List<AccountingAccount> tree = PosApiBridge.getAccounts(true);
        List<AccountingAccount> flat = new ArrayList<>();
        for (AccountingAccount root : tree) {
            flatten(root, 0, flat);
        }
        accounts.setAll(flat);
    }

    public static void reloadBranches() {
        requireApiMode();
        List<AccountingBranch> remote = PosApiBridge.getBranches();
        remote.sort(
                Comparator
                        .comparing(AccountingBranch::isMainBranch).reversed()
                        .thenComparing(a -> a.getCode() == null ? "" : a.getCode())
                        .thenComparing(a -> a.getName() == null ? "" : a.getName())
        );
        branches.setAll(remote);
    }

    public static AccountingAccount saveAccount(AccountingAccount account) {
        requireApiMode();
        AccountingAccount saved;
        if (account.getId() == null) {
            saved = PosApiBridge.createAccount(account);
        } else {
            saved = PosApiBridge.updateAccount(account.getId(), account);
        }
        reloadAccounts();
        return saved;
    }

    public static void reloadJournalEntries(Long branchId, LocalDate fromDate, LocalDate toDate) {
        requireApiMode();
        List<AccountingJournalEntry> entries = PosApiBridge.getJournalEntries(branchId, fromDate, toDate);
        entries.sort(Comparator
                .comparing(AccountingJournalEntry::getEntryDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(AccountingJournalEntry::getId, Comparator.nullsLast(Comparator.naturalOrder()))
                .reversed());
        journalEntries.setAll(entries);
    }

    public static AccountingJournalEntry createJournalEntry(AccountingJournalEntryRequest request) {
        requireApiMode();
        AccountingJournalEntry created = PosApiBridge.createJournalEntry(request);
        return created;
    }

    public static void reloadTrialBalance(Long branchId,
                                          LocalDate fromDate,
                                          LocalDate toDate,
                                          boolean includeZeroLines) {
        requireApiMode();
        TrialBalanceReport report = PosApiBridge.getTrialBalance(branchId, fromDate, toDate, includeZeroLines);
        lastTrialBalance = report;
        List<TrialBalanceLine> lines = report == null || report.getLines() == null
                ? new ArrayList<>()
                : new ArrayList<>(report.getLines());
        lines.sort(Comparator
                .comparing((TrialBalanceLine line) -> line.getAccountCode() == null ? "" : line.getAccountCode())
                .thenComparing(line -> line.getAccountName() == null ? "" : line.getAccountName()));
        trialBalanceLines.setAll(lines);
    }

    private static void flatten(AccountingAccount node, int level, List<AccountingAccount> output) {
        if (node == null) {
            return;
        }
        node.setLevel(level);
        output.add(node);
        if (node.getChildren() == null || node.getChildren().isEmpty()) {
            return;
        }
        for (AccountingAccount child : node.getChildren()) {
            flatten(child, level + 1, output);
        }
    }

    private static void requireApiMode() {
        if (!BackendMode.useApiSync()) {
            throw new IllegalStateException("Accounting screen is available only in API sync mode.");
        }
    }
}
