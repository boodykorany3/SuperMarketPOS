package com.pos;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class PosApiBridge {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6))
            .build();

    private static final Gson GSON = new Gson();
    private static Long defaultCategoryId;

    private PosApiBridge() {}

    public static LoginResult login(String username, String password) {
        JsonObject payload = new JsonObject();
        payload.addProperty("username", username);
        payload.addProperty("password", password);

        try {
            JsonObject response = request("POST", "/auth/login", payload, false, 200).getAsJsonObject();
            LoginResult result = new LoginResult();
            result.userId = response.has("userId") ? response.get("userId").getAsLong() : null;
            result.username = response.has("username") ? response.get("username").getAsString() : username;
            result.role = response.has("role") ? response.get("role").getAsString() : "CASHIER";
            result.token = response.has("token") ? response.get("token").getAsString() : null;
            result.branchId = asLong(response, "branchId");
            result.branchCode = asString(response, "branchCode", "");
            result.branchName = asString(response, "branchName", "");
            return result;
        } catch (ApiHttpException e) {
            if (e.statusCode == 401) {
                return null;
            }
            throw e;
        }
    }

    public static List<User> getUsers() {
        JsonArray array = request("GET", "/users", null, true, 200).getAsJsonArray();
        List<User> users = new ArrayList<>();
        for (JsonElement element : array) {
            JsonObject obj = element.getAsJsonObject();
            String username = asString(obj, "username", "");
            String role = asString(obj, "role", "CASHIER");
            Long branchId = asLong(obj, "branchId");
            String branchCode = asString(obj, "branchCode", "");
            String branchName = asString(obj, "branchName", "");
            users.add(new User(username, "", role, branchId, branchCode, branchName));
        }
        return users;
    }

    public static boolean changePassword(String currentPassword, String newPassword) {
        JsonObject payload = new JsonObject();
        payload.addProperty("currentPassword", currentPassword);
        payload.addProperty("newPassword", newPassword);
        JsonElement response = request("POST", "/users/change-password", payload, true, 200);
        return response != null && response.isJsonPrimitive() && response.getAsBoolean();
    }

    public static List<Product> getProducts() {
        JsonArray array = request("GET", "/products", null, true, 200).getAsJsonArray();
        List<Product> products = new ArrayList<>();
        for (JsonElement element : array) {
            JsonObject obj = element.getAsJsonObject();
            products.add(parseProduct(obj));
        }
        return products;
    }

    public static List<AccountingBranch> getBranches() {
        JsonArray array = request("GET", "/branches", null, true, 200).getAsJsonArray();
        List<AccountingBranch> branches = new ArrayList<>();
        for (JsonElement element : array) {
            JsonObject obj = element.getAsJsonObject();
            AccountingBranch branch = new AccountingBranch();
            branch.setId(asLong(obj, "id"));
            branch.setCode(asString(obj, "code", ""));
            branch.setName(asString(obj, "name", ""));
            branch.setMainBranch(asBoolean(obj, "mainBranch", false));
            branch.setActive(asBoolean(obj, "active", true));
            branches.add(branch);
        }
        return branches;
    }

    public static List<AccountingAccount> getAccounts(boolean tree) {
        String path = "/accounts?tree=" + (tree ? "true" : "false");
        JsonArray array = request("GET", path, null, true, 200).getAsJsonArray();
        List<AccountingAccount> accounts = new ArrayList<>();
        for (JsonElement element : array) {
            accounts.add(parseAccount(element.getAsJsonObject()));
        }
        return accounts;
    }

    public static AccountingAccount createAccount(AccountingAccount account) {
        JsonObject payload = buildAccountPayload(account);
        JsonObject response = request("POST", "/accounts", payload, true, 201).getAsJsonObject();
        return parseAccount(response);
    }

    public static AccountingAccount updateAccount(Long accountId, AccountingAccount account) {
        if (accountId == null) {
            throw new IllegalArgumentException("accountId is required.");
        }
        JsonObject payload = buildAccountPayload(account);
        JsonObject response = request("PUT", "/accounts/" + accountId, payload, true, 200).getAsJsonObject();
        return parseAccount(response);
    }

    public static List<AccountingJournalEntry> getJournalEntries(Long branchId, LocalDate fromDate, LocalDate toDate) {
        Map<String, String> params = new HashMap<>();
        params.put("branchId", branchId == null ? null : String.valueOf(branchId));
        params.put("fromDate", fromDate == null ? null : fromDate.toString());
        params.put("toDate", toDate == null ? null : toDate.toString());
        String path = buildQueryPath("/journal-entries", params);
        JsonArray array = request("GET", path, null, true, 200).getAsJsonArray();
        List<AccountingJournalEntry> entries = new ArrayList<>();
        for (JsonElement element : array) {
            entries.add(parseJournalEntry(element.getAsJsonObject()));
        }
        return entries;
    }

    public static AccountingJournalEntry createJournalEntry(AccountingJournalEntryRequest requestPayload) {
        JsonObject payload = new JsonObject();
        if (requestPayload.getEntryDate() != null) {
            payload.addProperty("entryDate", requestPayload.getEntryDate().toString());
        }
        payload.addProperty("description", requestPayload.getDescription() == null ? "" : requestPayload.getDescription());
        if (requestPayload.getReferenceType() != null && !requestPayload.getReferenceType().isBlank()) {
            payload.addProperty("referenceType", requestPayload.getReferenceType());
        }
        if (requestPayload.getReferenceId() != null) {
            payload.addProperty("referenceId", requestPayload.getReferenceId());
        }
        if (requestPayload.getBranchId() != null) {
            payload.addProperty("branchId", requestPayload.getBranchId());
        }

        JsonArray linesArray = new JsonArray();
        for (AccountingJournalLineRequest line : requestPayload.getLines()) {
            JsonObject lineObj = new JsonObject();
            lineObj.addProperty("accountId", line.getAccountId());
            lineObj.addProperty("debit", line.getDebit());
            lineObj.addProperty("credit", line.getCredit());
            if (line.getNote() != null && !line.getNote().isBlank()) {
                lineObj.addProperty("note", line.getNote());
            }
            linesArray.add(lineObj);
        }
        payload.add("lines", linesArray);

        JsonObject response = request("POST", "/journal-entries", payload, true, 201).getAsJsonObject();
        return parseJournalEntry(response);
    }

    public static TrialBalanceReport getTrialBalance(Long branchId,
                                                     LocalDate fromDate,
                                                     LocalDate toDate,
                                                     boolean includeZeroLines) {
        Map<String, String> params = new HashMap<>();
        params.put("branchId", branchId == null ? null : String.valueOf(branchId));
        params.put("fromDate", fromDate == null ? null : fromDate.toString());
        params.put("toDate", toDate == null ? null : toDate.toString());
        params.put("includeZero", includeZeroLines ? "true" : "false");
        String path = buildQueryPath("/accounts/trial-balance", params);
        JsonObject response = request("GET", path, null, true, 200).getAsJsonObject();
        return parseTrialBalanceReport(response);
    }

    public static String addOrUpdateProduct(Product product, String normalizedBarcode) {
        if (normalizedBarcode == null || normalizedBarcode.isBlank()) {
            throw new IllegalStateException("Barcode is required for API sync.");
        }

        Long existingId = product.getId();
        if (existingId == null) {
            Product existingByBarcode = getProductByBarcodeOptional(normalizedBarcode);
            existingId = existingByBarcode == null ? null : existingByBarcode.getId();
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("name", product.getName());
        payload.addProperty("barcode", normalizedBarcode);
        payload.addProperty("price", product.getPrice());
        payload.addProperty("quantity", product.getQuantity());
        payload.addProperty("categoryId", ensureDefaultCategoryId());

        JsonObject response;
        if (existingId == null) {
            response = request("POST", "/products", payload, true, 201).getAsJsonObject();
        } else {
            response = request("PUT", "/products/" + existingId, payload, true, 200).getAsJsonObject();
        }

        Product saved = parseProduct(response);
        product.setId(saved.getId());
        product.setBarcode(saved.getBarcode());
        return saved.getBarcode();
    }

    public static void deleteProduct(Product product) {
        deleteProduct(product, false);
    }

    public static void deleteProduct(Product product, boolean force) {
        if (product == null) {
            return;
        }

        Long id = product.getId();
        if (id == null && product.getBarcode() != null && !product.getBarcode().isBlank()) {
            Product existing = getProductByBarcodeOptional(product.getBarcode());
            if (existing != null) {
                id = existing.getId();
            }
        }

        if (id == null) {
            return;
        }
        String path = "/products/" + id + (force ? "?force=true" : "");
        request("DELETE", path, null, true, 204);
    }

    public static List<Sale> getSales() {
        JsonArray array = request("GET", "/sales", null, true, 200).getAsJsonArray();
        List<Sale> sales = new ArrayList<>();

        for (JsonElement element : array) {
            JsonObject obj = element.getAsJsonObject();
            Long saleId = asLong(obj, "id");
            String invoiceNumber = asString(obj, "invoiceNumber", "Invoice #AUTO");
            String dateValue = asString(obj, "date", null);
            LocalDateTime dateTime = dateValue == null || dateValue.isBlank()
                    ? LocalDateTime.now()
                    : LocalDateTime.parse(dateValue);
            String status = asString(obj, "status", Sale.STATUS_COMPLETED);
            double total = asDouble(obj, "totalAmount", 0.0);

            Map<String, Integer> items = new HashMap<>();
            if (obj.has("items") && obj.get("items").isJsonArray()) {
                JsonArray itemsArray = obj.getAsJsonArray("items");
                for (JsonElement itemEl : itemsArray) {
                    JsonObject itemObj = itemEl.getAsJsonObject();
                    String productName = asString(itemObj, "productName", "Unknown product");
                    int quantity = asInt(itemObj, "quantity", 0);
                    if (quantity > 0) {
                        items.merge(productName, quantity, Integer::sum);
                    }
                }
            }

            sales.add(new Sale(
                    saleId,
                    invoiceNumber,
                    dateTime,
                    items,
                    new HashMap<>(),
                    new HashMap<>(),
                    total,
                    status
            ));
        }

        sales.sort(Comparator.comparing(Sale::getDateTime, Comparator.nullsLast(Comparator.naturalOrder())).reversed());
        return sales;
    }

    public static SalesStore.AddSaleResult addSale(Sale sale) {
        if (sale == null) {
            return SalesStore.AddSaleResult.FAILED;
        }

        Long userId = UserSession.getUserId();
        if (userId == null) {
            return SalesStore.AddSaleResult.FAILED;
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("userId", userId);

        JsonArray itemsArray = new JsonArray();
        if (sale.getItemBarcodes() != null && !sale.getItemBarcodes().isEmpty()) {
            for (Map.Entry<String, Integer> entry : sale.getItemBarcodes().entrySet()) {
                int quantity = entry.getValue() == null ? 0 : entry.getValue();
                if (quantity <= 0) {
                    continue;
                }
                Product product = InventoryStore.findByBarcode(entry.getKey());
                if (product == null || product.getId() == null) {
                    Product remote = getProductByBarcodeOptional(entry.getKey());
                    if (remote != null) {
                        product = remote;
                    }
                }
                if (product == null || product.getId() == null) {
                    return SalesStore.AddSaleResult.FAILED;
                }

                JsonObject itemObj = new JsonObject();
                itemObj.addProperty("productId", product.getId());
                itemObj.addProperty("quantity", quantity);
                itemsArray.add(itemObj);
            }
        } else {
            for (Map.Entry<String, Integer> entry : sale.getItems().entrySet()) {
                int quantity = entry.getValue() == null ? 0 : entry.getValue();
                if (quantity <= 0) {
                    continue;
                }
                Product product = InventoryStore.findByName(entry.getKey());
                if (product == null || product.getId() == null) {
                    return SalesStore.AddSaleResult.FAILED;
                }

                JsonObject itemObj = new JsonObject();
                itemObj.addProperty("productId", product.getId());
                itemObj.addProperty("quantity", quantity);
                itemsArray.add(itemObj);
            }
        }

        if (itemsArray.isEmpty()) {
            return SalesStore.AddSaleResult.FAILED;
        }

        payload.add("items", itemsArray);

        try {
            JsonObject response = request("POST", "/sales", payload, true, 201).getAsJsonObject();
            sale.setId(asLong(response, "id"));
            sale.setInvoiceNumber(asString(response, "invoiceNumber", sale.getInvoiceNumber()));
            sale.setStatus(asString(response, "status", Sale.STATUS_COMPLETED));
            return SalesStore.AddSaleResult.SUCCESS;
        } catch (ApiHttpException e) {
            if (e.statusCode == 409) {
                return SalesStore.AddSaleResult.OUT_OF_STOCK;
            }
            return SalesStore.AddSaleResult.FAILED;
        }
    }

    public static SalesStore.ActionResult cancelSale(String invoiceNumber) {
        return processSaleAction(invoiceNumber, "cancel");
    }

    public static SalesStore.ActionResult returnSale(String invoiceNumber) {
        return processSaleAction(invoiceNumber, "return");
    }

    public static int getCustomerPoints(String phone) {
        if (phone == null || phone.isBlank()) {
            return 0;
        }
        String encoded = encodePath(phone.trim());
        JsonElement response = request("GET", "/customers/phone/" + encoded + "/points", null, true, 200);
        return response == null || !response.isJsonPrimitive() ? 0 : response.getAsInt();
    }

    public static void addCustomerPoints(String phone, int value) {
        if (phone == null || phone.isBlank() || value <= 0) {
            return;
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("phone", phone.trim());
        payload.addProperty("value", value);
        request("POST", "/customers/points/add", payload, true, 200);
    }

    public static boolean redeemCustomerPoints(String phone, int cost) {
        if (phone == null || phone.isBlank() || cost <= 0) {
            return false;
        }
        JsonObject payload = new JsonObject();
        payload.addProperty("phone", phone.trim());
        payload.addProperty("cost", cost);
        JsonObject response = request("POST", "/customers/points/redeem", payload, true, 200).getAsJsonObject();
        return response.has("redeemed") && response.get("redeemed").getAsBoolean();
    }

    public static Map<String, Integer> getAllCustomerPoints() {
        JsonObject response = request("GET", "/customers/points", null, true, 200).getAsJsonObject();
        Map<String, Integer> points = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : response.entrySet()) {
            points.put(entry.getKey(), entry.getValue().getAsInt());
        }
        return points;
    }

    private static SalesStore.ActionResult processSaleAction(String invoiceNumber, String action) {
        if (invoiceNumber == null || invoiceNumber.isBlank()) {
            return SalesStore.ActionResult.NOT_FOUND;
        }

        Long saleId = null;
        for (Sale sale : SalesStore.getSales()) {
            if (invoiceNumber.equals(sale.getInvoiceNumber()) && sale.getId() != null) {
                saleId = sale.getId();
                break;
            }
        }

        if (saleId == null) {
            for (Sale sale : getSales()) {
                if (invoiceNumber.equals(sale.getInvoiceNumber())) {
                    saleId = sale.getId();
                    break;
                }
            }
        }

        if (saleId == null) {
            return SalesStore.ActionResult.NOT_FOUND;
        }

        try {
            request("POST", "/sales/" + saleId + "/" + action, new JsonObject(), true, 200);
            return SalesStore.ActionResult.SUCCESS;
        } catch (ApiHttpException e) {
            if (e.statusCode == 409) {
                return SalesStore.ActionResult.ALREADY_PROCESSED;
            }
            if (e.statusCode == 404) {
                return SalesStore.ActionResult.NOT_FOUND;
            }
            return SalesStore.ActionResult.NOT_FOUND;
        }
    }

    private static Product getProductByBarcodeOptional(String barcode) {
        if (barcode == null || barcode.isBlank()) {
            return null;
        }
        String encoded = encodePath(InventoryStore.sanitizeBarcode(barcode));
        try {
            JsonObject response = request("GET", "/products/barcode/" + encoded, null, true, 200).getAsJsonObject();
            return parseProduct(response);
        } catch (ApiHttpException e) {
            if (e.statusCode == 404) {
                return null;
            }
            throw e;
        }
    }

    private static Long ensureDefaultCategoryId() {
        if (defaultCategoryId != null) {
            return defaultCategoryId;
        }

        JsonArray categories = request("GET", "/categories", null, true, 200).getAsJsonArray();
        for (JsonElement element : categories) {
            JsonObject obj = element.getAsJsonObject();
            String name = asString(obj, "name", "");
            if ("general".equalsIgnoreCase(name)) {
                defaultCategoryId = asLong(obj, "id");
                return defaultCategoryId;
            }
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("name", "General");
        payload.addProperty("description", "Auto-created category for POS inventory sync");

        JsonObject created = request("POST", "/categories", payload, true, 201).getAsJsonObject();
        defaultCategoryId = asLong(created, "id");
        return defaultCategoryId;
    }

    private static Product parseProduct(JsonObject obj) {
        Long id = asLong(obj, "id");
        String name = asString(obj, "name", "");
        String barcode = asString(obj, "barcode", "");
        double price = asDouble(obj, "price", 0.0);
        int quantity = asInt(obj, "quantity", 0);
        return new Product(id, name, barcode, price, quantity);
    }

    private static AccountingAccount parseAccount(JsonObject obj) {
        AccountingAccount account = new AccountingAccount();
        account.setId(asLong(obj, "id"));
        account.setCode(asString(obj, "code", ""));
        account.setName(asString(obj, "name", ""));
        account.setType(asString(obj, "type", ""));
        account.setParentId(asLong(obj, "parentId"));
        account.setParentCode(asString(obj, "parentCode", ""));
        account.setLevel(asInt(obj, "level", 0));
        account.setPostingAllowed(asBoolean(obj, "postingAllowed", true));
        account.setActive(asBoolean(obj, "active", true));

        if (obj.has("children") && obj.get("children").isJsonArray()) {
            List<AccountingAccount> children = new ArrayList<>();
            for (JsonElement childEl : obj.getAsJsonArray("children")) {
                children.add(parseAccount(childEl.getAsJsonObject()));
            }
            account.setChildren(children);
        }

        return account;
    }

    private static JsonObject buildAccountPayload(AccountingAccount account) {
        JsonObject payload = new JsonObject();
        payload.addProperty("code", account.getCode() == null ? "" : account.getCode());
        payload.addProperty("name", account.getName() == null ? "" : account.getName());
        payload.addProperty("type", account.getType() == null ? "" : account.getType());
        if (account.getParentId() != null) {
            payload.addProperty("parentId", account.getParentId());
        }
        payload.addProperty("postingAllowed", account.isPostingAllowed());
        payload.addProperty("active", account.isActive());
        return payload;
    }

    private static AccountingJournalEntry parseJournalEntry(JsonObject obj) {
        AccountingJournalEntry entry = new AccountingJournalEntry();
        entry.setId(asLong(obj, "id"));
        entry.setEntryDate(asLocalDate(obj, "entryDate"));
        entry.setDescription(asString(obj, "description", ""));
        entry.setReferenceType(asString(obj, "referenceType", ""));
        entry.setReferenceId(asLong(obj, "referenceId"));
        entry.setBranchId(asLong(obj, "branchId"));
        entry.setBranchCode(asString(obj, "branchCode", ""));
        entry.setBranchName(asString(obj, "branchName", ""));
        entry.setCreatedBy(asString(obj, "createdBy", ""));
        entry.setTotalDebit(asBigDecimal(obj, "totalDebit", BigDecimal.ZERO));
        entry.setTotalCredit(asBigDecimal(obj, "totalCredit", BigDecimal.ZERO));

        List<AccountingJournalLine> lines = new ArrayList<>();
        if (obj.has("lines") && obj.get("lines").isJsonArray()) {
            for (JsonElement lineEl : obj.getAsJsonArray("lines")) {
                lines.add(parseJournalLine(lineEl.getAsJsonObject()));
            }
        }
        entry.setLines(lines);
        return entry;
    }

    private static AccountingJournalLine parseJournalLine(JsonObject obj) {
        AccountingJournalLine line = new AccountingJournalLine();
        line.setId(asLong(obj, "id"));
        line.setAccountId(asLong(obj, "accountId"));
        line.setAccountCode(asString(obj, "accountCode", ""));
        line.setAccountName(asString(obj, "accountName", ""));
        line.setDebit(asBigDecimal(obj, "debit", BigDecimal.ZERO));
        line.setCredit(asBigDecimal(obj, "credit", BigDecimal.ZERO));
        line.setNote(asString(obj, "note", ""));
        return line;
    }

    private static TrialBalanceReport parseTrialBalanceReport(JsonObject obj) {
        TrialBalanceReport report = new TrialBalanceReport();
        report.setFromDate(asLocalDate(obj, "fromDate"));
        report.setToDate(asLocalDate(obj, "toDate"));
        report.setBranchId(asLong(obj, "branchId"));
        report.setBranchCode(asString(obj, "branchCode", ""));
        report.setBranchName(asString(obj, "branchName", ""));
        report.setTotalDebit(asBigDecimal(obj, "totalDebit", BigDecimal.ZERO));
        report.setTotalCredit(asBigDecimal(obj, "totalCredit", BigDecimal.ZERO));
        report.setDifference(asBigDecimal(obj, "difference", BigDecimal.ZERO));

        List<TrialBalanceLine> lines = new ArrayList<>();
        if (obj.has("lines") && obj.get("lines").isJsonArray()) {
            for (JsonElement lineEl : obj.getAsJsonArray("lines")) {
                lines.add(parseTrialBalanceLine(lineEl.getAsJsonObject()));
            }
        }
        report.setLines(lines);
        return report;
    }

    private static TrialBalanceLine parseTrialBalanceLine(JsonObject obj) {
        TrialBalanceLine line = new TrialBalanceLine();
        line.setAccountId(asLong(obj, "accountId"));
        line.setAccountCode(asString(obj, "accountCode", ""));
        line.setAccountName(asString(obj, "accountName", ""));
        line.setAccountType(asString(obj, "accountType", ""));
        line.setTotalDebit(asBigDecimal(obj, "totalDebit", BigDecimal.ZERO));
        line.setTotalCredit(asBigDecimal(obj, "totalCredit", BigDecimal.ZERO));
        line.setBalance(asBigDecimal(obj, "balance", BigDecimal.ZERO));
        line.setBalanceNature(asString(obj, "balanceNature", ""));
        return line;
    }

    private static JsonElement request(String method,
                                       String path,
                                       JsonElement payload,
                                       boolean requiresAuth,
                                       int... expectedStatuses) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(BackendMode.apiBaseUrl() + path))
                    .timeout(Duration.ofSeconds(12))
                    .header("Accept", "application/json");

            if (requiresAuth) {
                String token = UserSession.getAuthToken();
                if (token == null || token.isBlank()) {
                    throw new IllegalStateException("API token is missing.");
                }
                builder.header("Authorization", "Bearer " + token);
            }

            if ("GET".equals(method) || "DELETE".equals(method)) {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                String json = payload == null ? "{}" : GSON.toJson(payload);
                builder.header("Content-Type", "application/json");
                builder.method(method, HttpRequest.BodyPublishers.ofString(json));
            }

            HttpResponse<String> response = CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (!isExpected(status, expectedStatuses)) {
                throw new ApiHttpException(status, response.body());
            }

            String body = response.body() == null ? "" : response.body().trim();
            if (body.isEmpty()) {
                return null;
            }
            return JsonParser.parseString(body);
        } catch (ApiHttpException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("API request failed: " + method + " " + path + " | " + e.getMessage(), e);
        }
    }

    private static boolean isExpected(int status, int... expected) {
        for (int value : expected) {
            if (status == value) {
                return true;
            }
        }
        return false;
    }

    private static String asString(JsonObject obj, String key, String fallback) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return fallback;
        }
        return obj.get(key).getAsString();
    }

    private static Long asLong(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return null;
        }
        return obj.get(key).getAsLong();
    }

    private static int asInt(JsonObject obj, String key, int fallback) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return fallback;
        }
        return obj.get(key).getAsInt();
    }

    private static boolean asBoolean(JsonObject obj, String key, boolean fallback) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return fallback;
        }
        return obj.get(key).getAsBoolean();
    }

    private static double asDouble(JsonObject obj, String key, double fallback) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return fallback;
        }
        return obj.get(key).getAsDouble();
    }

    private static BigDecimal asBigDecimal(JsonObject obj, String key, BigDecimal fallback) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return obj.get(key).getAsBigDecimal();
        } catch (Exception ignored) {
            try {
                return new BigDecimal(obj.get(key).getAsString());
            } catch (Exception innerIgnored) {
                return fallback;
            }
        }
    }

    private static LocalDate asLocalDate(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return null;
        }
        String value = obj.get(key).getAsString();
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String encodePath(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String buildQueryPath(String basePath, Map<String, String> params) {
        StringBuilder path = new StringBuilder(basePath);
        boolean hasQuery = false;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (entry.getValue() == null || entry.getValue().isBlank()) {
                continue;
            }
            path.append(hasQuery ? "&" : "?");
            path.append(encodePath(entry.getKey())).append("=").append(encodePath(entry.getValue()));
            hasQuery = true;
        }
        return path.toString();
    }

    public static final class LoginResult {
        private Long userId;
        private String username;
        private String role;
        private String token;
        private Long branchId;
        private String branchCode;
        private String branchName;

        public Long getUserId() {
            return userId;
        }

        public String getUsername() {
            return username;
        }

        public String getRole() {
            return role;
        }

        public String getToken() {
            return token;
        }

        public Long getBranchId() {
            return branchId;
        }

        public String getBranchCode() {
            return branchCode;
        }

        public String getBranchName() {
            return branchName;
        }
    }

    private static final class ApiHttpException extends RuntimeException {
        private final int statusCode;
        private final String responseBody;

        private ApiHttpException(int statusCode, String responseBody) {
            super("API error " + statusCode + ": " + responseBody);
            this.statusCode = statusCode;
            this.responseBody = responseBody;
        }
    }
}
