package com.pos;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AccountsController {
    private static final Pattern API_MESSAGE_PATTERN =
            Pattern.compile("\"message\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"");
    private static final DecimalFormat MONEY_FORMAT = new DecimalFormat("#,##0.00");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String BRANCH_ALL = "كل الفروع";
    private static final String BRANCH_AUTO = "تلقائي (فرع المستخدم)";

    @FXML private VBox accountsRoot;

    @FXML private TextField accountCodeField;
    @FXML private TextField accountNameField;
    @FXML private ComboBox<String> accountTypeBox;
    @FXML private ComboBox<AccountingAccount> accountParentBox;
    @FXML private CheckBox accountPostingAllowedCheck;
    @FXML private CheckBox accountActiveCheck;
    @FXML private Button accountSaveBtn;
    @FXML private Button accountClearBtn;
    @FXML private Button accountRefreshBtn;
    @FXML private Label accountSummaryLabel;

    @FXML private TableView<AccountingAccount> accountsTable;
    @FXML private TableColumn<AccountingAccount, String> accountCodeCol;
    @FXML private TableColumn<AccountingAccount, String> accountNameCol;
    @FXML private TableColumn<AccountingAccount, String> accountTypeCol;
    @FXML private TableColumn<AccountingAccount, String> accountParentCol;
    @FXML private TableColumn<AccountingAccount, Integer> accountLevelCol;
    @FXML private TableColumn<AccountingAccount, String> accountPostingCol;
    @FXML private TableColumn<AccountingAccount, String> accountActiveCol;

    @FXML private TextField journalDescriptionField;
    @FXML private TextField journalRefTypeField;
    @FXML private TextField journalRefIdField;
    @FXML private javafx.scene.control.DatePicker journalDatePicker;
    @FXML private ComboBox<BranchOption> journalEntryBranchBox;

    @FXML private ComboBox<AccountingAccount> journalLineAccountBox;
    @FXML private TextField journalLineDebitField;
    @FXML private TextField journalLineCreditField;
    @FXML private TextField journalLineNoteField;
    @FXML private Button journalAddLineBtn;
    @FXML private Button journalRemoveLineBtn;
    @FXML private Button journalSaveEntryBtn;
    @FXML private Label journalTotalsLabel;

    @FXML private TableView<JournalDraftLine> journalLinesTable;
    @FXML private TableColumn<JournalDraftLine, String> journalLineAccountCol;
    @FXML private TableColumn<JournalDraftLine, String> journalLineDebitCol;
    @FXML private TableColumn<JournalDraftLine, String> journalLineCreditCol;
    @FXML private TableColumn<JournalDraftLine, String> journalLineNoteCol;

    @FXML private ComboBox<BranchOption> journalFilterBranchBox;
    @FXML private javafx.scene.control.DatePicker journalFromDatePicker;
    @FXML private javafx.scene.control.DatePicker journalToDatePicker;
    @FXML private Button journalLoadBtn;

    @FXML private TableView<AccountingJournalEntry> journalEntriesTable;
    @FXML private TableColumn<AccountingJournalEntry, String> journalEntryDateCol;
    @FXML private TableColumn<AccountingJournalEntry, String> journalEntryDescCol;
    @FXML private TableColumn<AccountingJournalEntry, String> journalEntryBranchCol;
    @FXML private TableColumn<AccountingJournalEntry, String> journalEntryDebitCol;
    @FXML private TableColumn<AccountingJournalEntry, String> journalEntryCreditCol;
    @FXML private TableColumn<AccountingJournalEntry, String> journalEntryByCol;
    @FXML private TextArea journalEntryDetailsArea;

    @FXML private ComboBox<BranchOption> trialBranchBox;
    @FXML private javafx.scene.control.DatePicker trialFromDatePicker;
    @FXML private javafx.scene.control.DatePicker trialToDatePicker;
    @FXML private CheckBox trialIncludeZeroCheck;
    @FXML private Button trialLoadBtn;
    @FXML private Label trialSummaryLabel;

    @FXML private TableView<TrialBalanceLine> trialTable;
    @FXML private TableColumn<TrialBalanceLine, String> trialCodeCol;
    @FXML private TableColumn<TrialBalanceLine, String> trialNameCol;
    @FXML private TableColumn<TrialBalanceLine, String> trialTypeCol;
    @FXML private TableColumn<TrialBalanceLine, String> trialDebitCol;
    @FXML private TableColumn<TrialBalanceLine, String> trialCreditCol;
    @FXML private TableColumn<TrialBalanceLine, String> trialBalanceCol;
    @FXML private TableColumn<TrialBalanceLine, String> trialNatureCol;

    private final ObservableList<JournalDraftLine> journalDraftLines = FXCollections.observableArrayList();
    private AccountingAccount editingAccount;

    @FXML
    public void initialize() {
        configureAccountControls();
        configureJournalControls();
        configureTrialControls();

        if (!BackendMode.useApiSync()) {
            disableForLocalMode();
            return;
        }

        loadAllAccountingData();
    }

    private void configureAccountControls() {
        accountCodeField.setVisible(false);
        accountCodeField.setManaged(false);
        accountCodeCol.setVisible(false);
        accountCodeCol.setMinWidth(0);
        accountCodeCol.setPrefWidth(0);
        accountCodeCol.setMaxWidth(0);

        accountTypeBox.setItems(FXCollections.observableArrayList("ASSET", "LIABILITY", "EQUITY", "REVENUE", "EXPENSE"));
        accountTypeBox.getSelectionModel().selectFirst();
        accountTypeBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(String value) {
                return localizeAccountType(value);
            }

            @Override
            public String fromString(String string) {
                return null;
            }
        });

        accountParentBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(AccountingAccount account) {
                if (account == null) {
                    return "بدون أب (رئيسي)";
                }
                return account.getDisplayName();
            }

            @Override
            public AccountingAccount fromString(String string) {
                return null;
            }
        });

        accountCodeCol.setCellValueFactory(new PropertyValueFactory<>("code"));
        accountNameCol.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue() == null ? "" : cell.getValue().getDisplayName()));
        accountTypeCol.setCellValueFactory(cell ->
                new SimpleStringProperty(localizeAccountType(cell.getValue() == null ? "" : cell.getValue().getType())));
        accountParentCol.setCellValueFactory(cell ->
                new SimpleStringProperty(resolveParentAccountDisplay(cell.getValue())));
        accountLevelCol.setCellValueFactory(new PropertyValueFactory<>("level"));
        accountPostingCol.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue() != null && cell.getValue().isPostingAllowed() ? "نعم" : "لا"));
        accountActiveCol.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue() != null && cell.getValue().isActive() ? "نشط" : "غير نشط"));

        alignIntegerColumn(accountLevelCol);

        accountsTable.setItems(AccountingStore.getAccounts());
        accountsTable.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, selected) -> fillAccountForm(selected));

        accountSaveBtn.setOnAction(e -> saveAccount());
        accountClearBtn.setOnAction(e -> clearAccountForm());
        accountRefreshBtn.setOnAction(e -> {
            reloadAccountsAndAccountCombos();
            showInfo("تم تحديث الحسابات.");
        });
        clearAccountForm();
    }

    private void configureJournalControls() {
        journalDatePicker.setValue(LocalDate.now());
        journalFromDatePicker.setValue(LocalDate.now().withDayOfMonth(1));
        journalToDatePicker.setValue(LocalDate.now());

        journalLineAccountBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(AccountingAccount account) {
                return account == null ? "" : account.getDisplayName();
            }

            @Override
            public AccountingAccount fromString(String string) {
                return null;
            }
        });

        journalLineAccountCol.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue() == null ? "" : cell.getValue().getAccountDisplay()));
        journalLineDebitCol.setCellValueFactory(cell ->
                new SimpleStringProperty(formatMoney(cell.getValue() == null ? BigDecimal.ZERO : cell.getValue().getDebit())));
        journalLineCreditCol.setCellValueFactory(cell ->
                new SimpleStringProperty(formatMoney(cell.getValue() == null ? BigDecimal.ZERO : cell.getValue().getCredit())));
        journalLineNoteCol.setCellValueFactory(new PropertyValueFactory<>("note"));

        alignMoneyStringColumn(journalLineDebitCol);
        alignMoneyStringColumn(journalLineCreditCol);

        journalLinesTable.setItems(journalDraftLines);

        journalEntryDateCol.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue() == null || cell.getValue().getEntryDate() == null
                        ? ""
                        : cell.getValue().getEntryDate().format(DATE_FORMAT)));
        journalEntryDescCol.setCellValueFactory(new PropertyValueFactory<>("description"));
        journalEntryBranchCol.setCellValueFactory(cell ->
                new SimpleStringProperty(resolveBranchDisplay(cell.getValue())));
        journalEntryDebitCol.setCellValueFactory(cell ->
                new SimpleStringProperty(formatMoney(cell.getValue() == null ? BigDecimal.ZERO : cell.getValue().getTotalDebit())));
        journalEntryCreditCol.setCellValueFactory(cell ->
                new SimpleStringProperty(formatMoney(cell.getValue() == null ? BigDecimal.ZERO : cell.getValue().getTotalCredit())));
        journalEntryByCol.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue() == null ? "" : safe(cell.getValue().getCreatedBy())));

        alignMoneyStringColumn(journalEntryDebitCol);
        alignMoneyStringColumn(journalEntryCreditCol);

        journalEntriesTable.setItems(AccountingStore.getJournalEntries());
        journalEntriesTable.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, selected) -> showEntryDetails(selected));

        journalAddLineBtn.setOnAction(e -> addJournalLine());
        journalRemoveLineBtn.setOnAction(e -> removeSelectedJournalLine());
        journalSaveEntryBtn.setOnAction(e -> saveJournalEntry());
        journalLoadBtn.setOnAction(e -> loadJournalEntries());
    }

    private void configureTrialControls() {
        trialFromDatePicker.setValue(LocalDate.now().withDayOfMonth(1));
        trialToDatePicker.setValue(LocalDate.now());
        trialIncludeZeroCheck.setSelected(false);

        trialCodeCol.setVisible(false);
        trialCodeCol.setMinWidth(0);
        trialCodeCol.setPrefWidth(0);
        trialCodeCol.setMaxWidth(0);

        trialCodeCol.setCellValueFactory(new PropertyValueFactory<>("accountCode"));
        trialNameCol.setCellValueFactory(new PropertyValueFactory<>("accountName"));
        trialTypeCol.setCellValueFactory(cell ->
                new SimpleStringProperty(localizeAccountType(cell.getValue() == null ? "" : cell.getValue().getAccountType())));
        trialDebitCol.setCellValueFactory(cell ->
                new SimpleStringProperty(formatMoney(cell.getValue() == null ? BigDecimal.ZERO : cell.getValue().getTotalDebit())));
        trialCreditCol.setCellValueFactory(cell ->
                new SimpleStringProperty(formatMoney(cell.getValue() == null ? BigDecimal.ZERO : cell.getValue().getTotalCredit())));
        trialBalanceCol.setCellValueFactory(cell ->
                new SimpleStringProperty(formatMoney(cell.getValue() == null ? BigDecimal.ZERO : cell.getValue().getBalance())));
        trialNatureCol.setCellValueFactory(new PropertyValueFactory<>("balanceNature"));

        alignMoneyStringColumn(trialDebitCol);
        alignMoneyStringColumn(trialCreditCol);
        alignMoneyStringColumn(trialBalanceCol);

        trialTable.setItems(AccountingStore.getTrialBalanceLines());
        trialLoadBtn.setOnAction(e -> loadTrialBalance());
    }

    private void disableForLocalMode() {
        accountsRoot.setDisable(true);
        showWarning("شاشة الحسابات تحتاج تشغيل وضع API. فعّل POS_API_SYNC=true ثم سجّل الدخول مرة أخرى.");
    }

    private void loadAllAccountingData() {
        try {
            reloadAccountsAndAccountCombos();
            reloadBranchOptions();
            loadJournalEntries();
            loadTrialBalance();
            updateAccountSummary();
        } catch (Exception e) {
            showErrorFromThrowable(e, "تعذر تحميل بيانات الحسابات.");
        }
    }

    private void reloadAccountsAndAccountCombos() {
        AccountingStore.reloadAccounts();

        List<AccountingAccount> parents = new ArrayList<>();
        parents.add(null);
        parents.addAll(AccountingStore.getAccounts());
        accountParentBox.setItems(FXCollections.observableArrayList(parents));
        accountParentBox.getSelectionModel().selectFirst();

        List<AccountingAccount> postingAccounts = AccountingStore.getAccounts()
                .stream()
                .filter(account -> account.isPostingAllowed() && account.isActive())
                .toList();
        journalLineAccountBox.setItems(FXCollections.observableArrayList(postingAccounts));
        updateAccountSummary();
    }

    private void reloadBranchOptions() {
        AccountingStore.reloadBranches();

        ObservableList<BranchOption> allBranchOptions = FXCollections.observableArrayList();
        allBranchOptions.add(new BranchOption(null, BRANCH_ALL));
        for (AccountingBranch branch : AccountingStore.getBranches()) {
            allBranchOptions.add(new BranchOption(branch.getId(), branch.getDisplayName()));
        }

        ObservableList<BranchOption> entryBranchOptions = FXCollections.observableArrayList();
        entryBranchOptions.add(new BranchOption(null, BRANCH_AUTO));
        for (AccountingBranch branch : AccountingStore.getBranches()) {
            entryBranchOptions.add(new BranchOption(branch.getId(), branch.getDisplayName()));
        }

        configureBranchCombo(journalFilterBranchBox, allBranchOptions, BRANCH_ALL);
        configureBranchCombo(trialBranchBox, allBranchOptions, BRANCH_ALL);
        configureBranchCombo(journalEntryBranchBox, entryBranchOptions, BRANCH_AUTO);
    }

    private void configureBranchCombo(ComboBox<BranchOption> comboBox,
                                      ObservableList<BranchOption> options,
                                      String fallbackLabel) {
        comboBox.setItems(options);
        comboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(BranchOption option) {
                return option == null ? fallbackLabel : option.getLabel();
            }

            @Override
            public BranchOption fromString(String string) {
                return null;
            }
        });
        if (!options.isEmpty()) {
            comboBox.getSelectionModel().selectFirst();
        }
    }

    private void saveAccount() {
        String name = safe(accountNameField.getText()).trim();
        String type = accountTypeBox.getValue();

        if (name.isBlank() || type == null || type.isBlank()) {
            showWarning("كود الحساب والاسم والنوع حقول مطلوبة.");
            return;
        }

        AccountingAccount parent = accountParentBox.getValue();
        if (editingAccount != null && parent != null && editingAccount.getId() != null
                && editingAccount.getId().equals(parent.getId())) {
            showWarning("لا يمكن للحساب أن يكون أبًا لنفسه.");
            return;
        }

        AccountingAccount account = new AccountingAccount();
        if (editingAccount != null) {
            account.setId(editingAccount.getId());
        }
        account.setCode("");
        account.setName(name);
        account.setType(type);
        account.setParentId(parent == null ? null : parent.getId());
        account.setPostingAllowed(accountPostingAllowedCheck.isSelected());
        account.setActive(accountActiveCheck.isSelected());

        try {
            AccountingStore.saveAccount(account);
            reloadAccountsAndAccountCombos();
            clearAccountForm();
            showInfo("تم حفظ الحساب بنجاح.");
        } catch (Exception e) {
            showErrorFromThrowable(e, "تعذر حفظ الحساب.");
        }
    }

    private void clearAccountForm() {
        editingAccount = null;
        accountCodeField.clear();
        accountNameField.clear();
        accountTypeBox.getSelectionModel().selectFirst();
        accountParentBox.getSelectionModel().selectFirst();
        accountPostingAllowedCheck.setSelected(true);
        accountActiveCheck.setSelected(true);
        accountsTable.getSelectionModel().clearSelection();
        accountSaveBtn.setText("حفظ الحساب");
    }

    private void fillAccountForm(AccountingAccount account) {
        if (account == null) {
            return;
        }

        editingAccount = account;
        accountCodeField.clear();
        accountNameField.setText(safe(account.getName()));
        accountTypeBox.setValue(safe(account.getType()));
        accountPostingAllowedCheck.setSelected(account.isPostingAllowed());
        accountActiveCheck.setSelected(account.isActive());
        accountSaveBtn.setText("تعديل الحساب");

        if (account.getParentId() == null) {
            accountParentBox.getSelectionModel().selectFirst();
            return;
        }

        for (AccountingAccount option : accountParentBox.getItems()) {
            if (option != null && account.getParentId().equals(option.getId())) {
                accountParentBox.setValue(option);
                return;
            }
        }
        accountParentBox.getSelectionModel().selectFirst();
    }

    private void updateAccountSummary() {
        int total = AccountingStore.getAccounts().size();
        long active = AccountingStore.getAccounts().stream().filter(AccountingAccount::isActive).count();
        long posting = AccountingStore.getAccounts().stream().filter(AccountingAccount::isPostingAllowed).count();
        accountSummaryLabel.setText("الإجمالي: " + total + " | النشط: " + active + " | يسمح بالترحيل: " + posting);
    }

    private void addJournalLine() {
        AccountingAccount selectedAccount = journalLineAccountBox.getValue();
        if (selectedAccount == null || selectedAccount.getId() == null) {
            showWarning("اختر حساب الترحيل أولًا.");
            return;
        }

        BigDecimal debit = parseDecimal(journalLineDebitField.getText());
        BigDecimal credit = parseDecimal(journalLineCreditField.getText());
        if (debit == null || credit == null) {
            showWarning("قيم المدين والدائن يجب أن تكون أرقامًا صحيحة.");
            return;
        }

        boolean invalidLine = (debit.compareTo(BigDecimal.ZERO) <= 0 && credit.compareTo(BigDecimal.ZERO) <= 0)
                || (debit.compareTo(BigDecimal.ZERO) > 0 && credit.compareTo(BigDecimal.ZERO) > 0);
        if (invalidLine) {
            showWarning("كل بند يجب أن يحتوي على مدين أو دائن فقط.");
            return;
        }

        JournalDraftLine line = new JournalDraftLine(
                selectedAccount.getId(),
                selectedAccount.getCode(),
                selectedAccount.getName(),
                debit,
                credit,
                safe(journalLineNoteField.getText()).trim()
        );
        journalDraftLines.add(line);
        clearJournalLineInputs();
        updateDraftTotals();
    }

    private void removeSelectedJournalLine() {
        JournalDraftLine selected = journalLinesTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showWarning("اختر بندًا للحذف.");
            return;
        }
        journalDraftLines.remove(selected);
        updateDraftTotals();
    }

    private void clearJournalLineInputs() {
        journalLineAccountBox.getSelectionModel().clearSelection();
        journalLineDebitField.clear();
        journalLineCreditField.clear();
        journalLineNoteField.clear();
    }

    private void updateDraftTotals() {
        BigDecimal debitTotal = BigDecimal.ZERO;
        BigDecimal creditTotal = BigDecimal.ZERO;
        for (JournalDraftLine line : journalDraftLines) {
            debitTotal = debitTotal.add(line.getDebit());
            creditTotal = creditTotal.add(line.getCredit());
        }
        BigDecimal difference = debitTotal.subtract(creditTotal);
        journalTotalsLabel.setText("مدين: " + formatMoney(debitTotal)
                + " | دائن: " + formatMoney(creditTotal)
                + " | الفرق: " + formatMoney(difference));
    }

    private void saveJournalEntry() {
        String description = safe(journalDescriptionField.getText()).trim();
        if (description.isBlank()) {
            showWarning("وصف القيد مطلوب.");
            return;
        }

        if (journalDraftLines.size() < 2) {
            showWarning("القيد يحتاج على الأقل بندين.");
            return;
        }

        BigDecimal debitTotal = BigDecimal.ZERO;
        BigDecimal creditTotal = BigDecimal.ZERO;
        for (JournalDraftLine line : journalDraftLines) {
            debitTotal = debitTotal.add(line.getDebit());
            creditTotal = creditTotal.add(line.getCredit());
        }
        if (debitTotal.compareTo(BigDecimal.ZERO) <= 0 || creditTotal.compareTo(BigDecimal.ZERO) <= 0) {
            showWarning("إجمالي المدين والدائن يجب أن يكون أكبر من صفر.");
            return;
        }
        if (debitTotal.compareTo(creditTotal) != 0) {
            showWarning("القيد غير متوازن. يجب أن يساوي المدين الدائن.");
            return;
        }

        AccountingJournalEntryRequest request = new AccountingJournalEntryRequest();
        request.setEntryDate(journalDatePicker.getValue() == null ? LocalDate.now() : journalDatePicker.getValue());
        request.setDescription(description);
        request.setReferenceType(safe(journalRefTypeField.getText()).trim());
        request.setReferenceId(parseLongOrNull(journalRefIdField.getText()));
        BranchOption entryBranch = journalEntryBranchBox.getValue();
        request.setBranchId(entryBranch == null ? null : entryBranch.getId());

        List<AccountingJournalLineRequest> lines = new ArrayList<>();
        for (JournalDraftLine draft : journalDraftLines) {
            AccountingJournalLineRequest line = new AccountingJournalLineRequest();
            line.setAccountId(draft.getAccountId());
            line.setDebit(draft.getDebit());
            line.setCredit(draft.getCredit());
            line.setNote(draft.getNote());
            lines.add(line);
        }
        request.setLines(lines);

        try {
            AccountingStore.createJournalEntry(request);
            journalDraftLines.clear();
            updateDraftTotals();
            journalDescriptionField.clear();
            journalRefTypeField.clear();
            journalRefIdField.clear();
            loadJournalEntries();
            loadTrialBalance();
            showInfo("تم إنشاء القيد بنجاح.");
        } catch (Exception e) {
            showErrorFromThrowable(e, "تعذر إنشاء القيد.");
        }
    }

    private void loadJournalEntries() {
        BranchOption branch = journalFilterBranchBox.getValue();
        Long branchId = branch == null ? null : branch.getId();
        LocalDate fromDate = journalFromDatePicker.getValue();
        LocalDate toDate = journalToDatePicker.getValue();

        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            showWarning("نطاق التاريخ في فلتر القيود غير صحيح.");
            return;
        }

        try {
            AccountingStore.reloadJournalEntries(branchId, fromDate, toDate);
            journalEntryDetailsArea.clear();
        } catch (Exception e) {
            showErrorFromThrowable(e, "تعذر تحميل القيود اليومية.");
        }
    }

    private void showEntryDetails(AccountingJournalEntry entry) {
        if (entry == null) {
            journalEntryDetailsArea.clear();
            return;
        }

        StringBuilder text = new StringBuilder();
        text.append("القيد رقم: ").append(entry.getId() == null ? "-" : entry.getId()).append("\n");
        text.append("التاريخ: ").append(entry.getEntryDate() == null ? "-" : entry.getEntryDate()).append("\n");
        text.append("الفرع: ").append(resolveBranchDisplay(entry)).append("\n");
        text.append("تم بواسطة: ").append(safe(entry.getCreatedBy())).append("\n");
        text.append("الوصف: ").append(safe(entry.getDescription())).append("\n");
        if (!safe(entry.getReferenceType()).isBlank()) {
            text.append("المرجع: ").append(localizeReferenceType(safe(entry.getReferenceType())));
            if (entry.getReferenceId() != null) {
                text.append(" / ").append(entry.getReferenceId());
            }
            text.append("\n");
        }
        text.append("البنود:\n");
        if (entry.getLines() != null) {
            for (AccountingJournalLine line : entry.getLines()) {
                text.append("- ")
                        .append(resolveAccountDisplayName(line.getAccountName(), line.getAccountCode()))
                        .append(" | مدين ")
                        .append(formatMoney(line.getDebit()))
                        .append(" | دائن ")
                        .append(formatMoney(line.getCredit()));
                if (!safe(line.getNote()).isBlank()) {
                    text.append(" | ").append(line.getNote());
                }
                text.append("\n");
            }
        }
        text.append("إجمالي المدين: ").append(formatMoney(entry.getTotalDebit())).append("\n");
        text.append("إجمالي الدائن: ").append(formatMoney(entry.getTotalCredit())).append("\n");
        journalEntryDetailsArea.setText(text.toString());
    }

    private void loadTrialBalance() {
        BranchOption branch = trialBranchBox.getValue();
        Long branchId = branch == null ? null : branch.getId();
        LocalDate fromDate = trialFromDatePicker.getValue();
        LocalDate toDate = trialToDatePicker.getValue();

        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            showWarning("نطاق التاريخ في ميزان المراجعة غير صحيح.");
            return;
        }

        try {
            AccountingStore.reloadTrialBalance(branchId, fromDate, toDate, trialIncludeZeroCheck.isSelected());
            TrialBalanceReport report = AccountingStore.getLastTrialBalance();
            if (report == null) {
                trialSummaryLabel.setText("لا توجد بيانات لميزان المراجعة.");
                return;
            }
            String branchName = safe(report.getBranchName());
            if (branchName.isBlank()) {
                branchName = BRANCH_ALL;
            }
            trialSummaryLabel.setText(
                    "الفرع: " + branchName
                            + " | المدين: " + formatMoney(report.getTotalDebit())
                            + " | الدائن: " + formatMoney(report.getTotalCredit())
                            + " | الفرق: " + formatMoney(report.getDifference())
            );
        } catch (Exception e) {
            showErrorFromThrowable(e, "تعذر تحميل ميزان المراجعة.");
        }
    }

    private String resolveParentAccountDisplay(AccountingAccount account) {
        if (account == null || account.getParentId() == null) {
            return "-";
        }
        for (AccountingAccount candidate : AccountingStore.getAccounts()) {
            if (candidate != null && account.getParentId().equals(candidate.getId())) {
                return resolveAccountDisplayName(candidate.getName(), candidate.getCode());
            }
        }
        return "-";
    }

    private String resolveAccountDisplayName(String name, String fallbackCode) {
        String normalizedName = safe(name).trim();
        if (!normalizedName.isBlank()) {
            return normalizedName;
        }
        String normalizedCode = safe(fallbackCode).trim();
        return normalizedCode.isBlank() ? "-" : normalizedCode;
    }

    private String resolveBranchDisplay(AccountingJournalEntry entry) {
        if (entry == null) {
            return "";
        }
        String code = safe(entry.getBranchCode());
        String name = safe(entry.getBranchName());
        if (code.isBlank() && name.isBlank()) {
            return "-";
        }
        if (code.isBlank()) {
            return name;
        }
        if (name.isBlank()) {
            return code;
        }
        return code + " - " + name;
    }

    private void alignIntegerColumn(TableColumn<AccountingAccount, Integer> column) {
        column.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Integer value, boolean empty) {
                super.updateItem(value, empty);
                setAlignment(Pos.CENTER);
                setText(empty || value == null ? null : String.valueOf(value));
            }
        });
    }

    private <T> void alignMoneyStringColumn(TableColumn<T, String> column) {
        column.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                setAlignment(Pos.CENTER_RIGHT);
                setText(empty ? null : value);
            }
        });
    }

    private BigDecimal parseDecimal(String value) {
        String text = safe(value).trim();
        if (text.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            BigDecimal parsed = new BigDecimal(text);
            return parsed.compareTo(BigDecimal.ZERO) < 0 ? null : parsed;
        } catch (Exception ignored) {
            return null;
        }
    }

    private Long parseLongOrNull(String value) {
        String text = safe(value).trim();
        if (text.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(text);
        } catch (Exception ignored) {
            showWarning("رقم المرجع يجب أن يكون رقمًا صحيحًا.");
            return null;
        }
    }

    private String formatMoney(BigDecimal value) {
        return MONEY_FORMAT.format(value == null ? BigDecimal.ZERO : value) + " ج.م";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private void showWarning(String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setHeaderText(message);
        alert.show();
    }

    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setHeaderText(message);
        alert.show();
    }

    private void showErrorFromThrowable(Throwable throwable, String fallback) {
        String apiMessage = extractApiMessage(throwable);
        String message = apiMessage.isBlank() ? fallback : apiMessage;
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setHeaderText(message);
        alert.show();
    }

    private String extractApiMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && !message.isBlank()) {
                Matcher matcher = API_MESSAGE_PATTERN.matcher(message);
                if (matcher.find()) {
                    String extracted = matcher.group(1)
                            .replace("\\\"", "\"")
                            .replace("\\\\", "\\")
                            .trim();
                    if (!extracted.isBlank()) {
                        return extracted;
                    }
                }
            }
            current = current.getCause();
        }
        return "";
    }

    private String localizeReferenceType(String referenceType) {
        if ("SALE_COMPLETED".equalsIgnoreCase(referenceType)) {
            return "بيع مكتمل";
        }
        if ("SALE_CANCELED".equalsIgnoreCase(referenceType)) {
            return "بيع ملغي";
        }
        if ("SALE_RETURNED".equalsIgnoreCase(referenceType)) {
            return "مرتجع بيع";
        }
        return referenceType;
    }

    private String localizeAccountType(String accountType) {
        if ("ASSET".equalsIgnoreCase(accountType)) {
            return "أصول";
        }
        if ("LIABILITY".equalsIgnoreCase(accountType)) {
            return "التزامات";
        }
        if ("EQUITY".equalsIgnoreCase(accountType)) {
            return "حقوق ملكية";
        }
        if ("REVENUE".equalsIgnoreCase(accountType)) {
            return "إيرادات";
        }
        if ("EXPENSE".equalsIgnoreCase(accountType)) {
            return "مصروفات";
        }
        return safe(accountType);
    }

    private static class BranchOption {
        private final Long id;
        private final String label;

        private BranchOption(Long id, String label) {
            this.id = id;
            this.label = label == null ? "" : label;
        }

        public Long getId() {
            return id;
        }

        public String getLabel() {
            return label;
        }
    }

    public static class JournalDraftLine {
        private final Long accountId;
        private final String accountCode;
        private final String accountName;
        private final BigDecimal debit;
        private final BigDecimal credit;
        private final String note;

        public JournalDraftLine(Long accountId,
                                String accountCode,
                                String accountName,
                                BigDecimal debit,
                                BigDecimal credit,
                                String note) {
            this.accountId = accountId;
            this.accountCode = accountCode;
            this.accountName = accountName;
            this.debit = debit == null ? BigDecimal.ZERO : debit;
            this.credit = credit == null ? BigDecimal.ZERO : credit;
            this.note = note == null ? "" : note;
        }

        public Long getAccountId() {
            return accountId;
        }

        public String getAccountDisplay() {
            String name = safePart(accountName).trim();
            if (!name.isBlank()) {
                return name;
            }
            String code = safePart(accountCode).trim();
            return code.isBlank() ? "-" : code;
        }

        public BigDecimal getDebit() {
            return debit;
        }

        public BigDecimal getCredit() {
            return credit;
        }

        public String getNote() {
            return note;
        }

        private String safePart(String value) {
            return value == null ? "" : value;
        }
    }
}
