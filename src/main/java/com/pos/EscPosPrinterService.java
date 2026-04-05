package com.pos;

import javax.print.Doc;
import javax.print.DocFlavor;
import javax.print.DocPrintJob;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.SimpleDoc;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.LocalDateTime;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Map;

public class EscPosPrinterService implements ReceiptPrinterService {
    private static final int CONNECT_TIMEOUT_MS = 3000;
    private static final DateTimeFormatter RECEIPT_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm:ss a");

    private final PrinterSettings settings;

    public EscPosPrinterService(PrinterSettings settings) {
        this.settings = settings;
    }

    @Override
    public void print(Sale sale, String paid, String change) throws Exception {
        if (sale == null) {
            throw new IllegalArgumentException("Sale is required.");
        }

        byte[] receipt = buildReceipt(sale, paid, change);

        if (settings.getMode() == PrinterSettings.Mode.LAN) {
            printLan(receipt);
            return;
        }

        if (settings.getMode() == PrinterSettings.Mode.WINDOWS) {
            printWindows(receipt);
            return;
        }

        throw new IllegalStateException("Printer mode is not configured.");
    }

    private void printLan(byte[] bytes) throws Exception {
        String host = settings.getLanHost();
        int port = settings.getLanPort();

        if (host.isBlank()) {
            throw new IllegalArgumentException("LAN host is empty.");
        }

        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("LAN port is invalid: " + port);
        }

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(CONNECT_TIMEOUT_MS);
            OutputStream out = socket.getOutputStream();
            out.write(bytes);
            out.flush();
        }
    }

    private void printWindows(byte[] bytes) throws Exception {
        String wanted = settings.getWindowsPrinterName();
        if (wanted.isBlank()) {
            throw new IllegalArgumentException("Windows printer name is empty.");
        }

        PrintService[] services = PrintServiceLookup.lookupPrintServices(null, null);
        PrintService selected = null;
        for (PrintService service : services) {
            if (service.getName().equalsIgnoreCase(wanted)) {
                selected = service;
                break;
            }
        }

        if (selected == null) {
            throw new IllegalStateException("Printer not found: " + wanted);
        }

        DocFlavor flavor = DocFlavor.BYTE_ARRAY.AUTOSENSE;
        Doc doc = new SimpleDoc(bytes, flavor, null);
        DocPrintJob job = selected.createPrintJob();
        job.print(doc, null);
    }

    private byte[] buildReceipt(Sale sale, String paid, String change) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Map<String, Integer> items = sale.getItems() == null
                ? Collections.emptyMap()
                : sale.getItems();
        LocalDateTime receiptTime = sale.getDateTime() == null
                ? LocalDateTime.now()
                : sale.getDateTime();
        String invoiceNumber = safeText(sale.getInvoiceNumber(), "N/A");

        out.write(new byte[]{0x1B, 0x40}); // init
        out.write(new byte[]{0x1B, 0x61, 0x01}); // center
        out.write(new byte[]{0x1D, 0x21, 0x11}); // double size
        writeLine(out, "ABU SAMIR MARKET");
        out.write(new byte[]{0x1D, 0x21, 0x00}); // normal
        writeLine(out, "Cairo - Egypt");
        writeLine(out, "------------------------------");
        out.write(new byte[]{0x1B, 0x61, 0x00}); // left

        writeLine(out, "Invoice: " + invoiceNumber);
        writeLine(out, "Time: " + receiptTime.format(RECEIPT_TIME_FORMAT));
        writeLine(out, "------------------------------");
        writeLine(out, "ITEM                 QTY");
        writeLine(out, "------------------------------");

        for (Map.Entry<String, Integer> item : items.entrySet()) {
            String itemName = safeText(item.getKey(), "ITEM");
            int qty = item.getValue() == null ? 0 : item.getValue();
            if (qty <= 0) {
                continue;
            }
            if (itemName.length() > 20) {
                itemName = itemName.substring(0, 20);
            }
            String line = String.format("%-20s %4d", itemName, qty);
            writeLine(out, line);
        }

        writeLine(out, "------------------------------");
        writeLine(out, String.format("TOTAL : %.2f", sale.getTotal()));
        writeLine(out, "PAID  : " + safeText(paid, "0.00"));
        writeLine(out, "CHANGE: " + safeText(change, "0.00"));
        writeLine(out, "------------------------------");
        out.write(new byte[]{0x1B, 0x61, 0x01}); // center
        writeLine(out, "Thank You!");
        writeLine(out, "");
        writeLine(out, "");
        out.write(new byte[]{0x1D, 0x56, 0x00}); // full cut

        return out.toByteArray();
    }

    private void writeLine(ByteArrayOutputStream out, String line) throws Exception {
        out.write(line.getBytes(StandardCharsets.US_ASCII));
        out.write('\n');
    }

    private String safeText(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }
}
