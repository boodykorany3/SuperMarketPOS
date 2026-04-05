package com.pos;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

public class Sale {

    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_CANCELED = "CANCELED";
    public static final String STATUS_RETURNED = "RETURNED";

    private Long id;
    private String invoiceNumber;
    private LocalDateTime dateTime;
    private Map<String, Integer> items;
    private Map<String, Integer> itemBarcodes;
    private Map<String, String> itemNamesByBarcode;
    private double total;
    private String status;

    public Sale(String invoiceNumber,
                LocalDateTime dateTime,
                Map<String, Integer> items,
                double total) {
        this(null, invoiceNumber, dateTime, items, null, null, total, STATUS_COMPLETED);
    }

    public Sale(String invoiceNumber,
                LocalDateTime dateTime,
                Map<String, Integer> items,
                Map<String, Integer> itemBarcodes,
                Map<String, String> itemNamesByBarcode,
                double total) {
        this(null, invoiceNumber, dateTime, items, itemBarcodes, itemNamesByBarcode, total, STATUS_COMPLETED);
    }

    public Sale(String invoiceNumber,
                LocalDateTime dateTime,
                Map<String, Integer> items,
                double total,
                String status) {
        this(null, invoiceNumber, dateTime, items, null, null, total, status);
    }

    public Sale(String invoiceNumber,
                LocalDateTime dateTime,
                Map<String, Integer> items,
                Map<String, Integer> itemBarcodes,
                Map<String, String> itemNamesByBarcode,
                double total,
                String status) {
        this(null, invoiceNumber, dateTime, items, itemBarcodes, itemNamesByBarcode, total, status);
    }

    public Sale(Long id,
                String invoiceNumber,
                LocalDateTime dateTime,
                Map<String, Integer> items,
                Map<String, Integer> itemBarcodes,
                Map<String, String> itemNamesByBarcode,
                double total,
                String status) {
        this.id = id;
        this.invoiceNumber = invoiceNumber;
        this.dateTime = dateTime;
        this.items = items == null ? new HashMap<>() : new HashMap<>(items);
        this.itemBarcodes = itemBarcodes == null ? new HashMap<>() : new HashMap<>(itemBarcodes);
        this.itemNamesByBarcode = itemNamesByBarcode == null ? new HashMap<>() : new HashMap<>(itemNamesByBarcode);
        this.total = total;
        this.status = status == null || status.isBlank() ? STATUS_COMPLETED : status;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public void setInvoiceNumber(String invoiceNumber) {
        this.invoiceNumber = invoiceNumber;
    }

    public LocalDateTime getDateTime() {
        return dateTime;
    }

    public Map<String, Integer> getItems() {
        return items;
    }

    public Map<String, Integer> getItemBarcodes() {
        return itemBarcodes;
    }

    public Map<String, String> getItemNamesByBarcode() {
        return itemNamesByBarcode;
    }

    public String getDisplayNameForBarcode(String barcode) {
        String name = itemNamesByBarcode.get(barcode);
        if (name != null && !name.isBlank()) {
            return name;
        }
        return barcode;
    }

    public double getTotal() {
        return total;
    }

    public String getStatus() {
        return status == null || status.isBlank() ? STATUS_COMPLETED : status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isCompleted() {
        return STATUS_COMPLETED.equals(getStatus());
    }
}
