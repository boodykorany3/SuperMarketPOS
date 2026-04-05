package com.pos;

public class PrinterSettings {

    public enum Mode {
        NONE,
        LAN,
        WINDOWS
    }

    private Mode mode;
    private String lanHost;
    private int lanPort;
    private String windowsPrinterName;

    public PrinterSettings() {
        this.mode = Mode.NONE;
        this.lanHost = "";
        this.lanPort = 9100;
        this.windowsPrinterName = "";
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode == null ? Mode.NONE : mode;
    }

    public String getLanHost() {
        return lanHost;
    }

    public void setLanHost(String lanHost) {
        this.lanHost = lanHost == null ? "" : lanHost.trim();
    }

    public int getLanPort() {
        return lanPort;
    }

    public void setLanPort(int lanPort) {
        if (lanPort < 1 || lanPort > 65535) {
            this.lanPort = 9100;
            return;
        }
        this.lanPort = lanPort;
    }

    public String getWindowsPrinterName() {
        return windowsPrinterName;
    }

    public void setWindowsPrinterName(String windowsPrinterName) {
        this.windowsPrinterName = windowsPrinterName == null ? "" : windowsPrinterName.trim();
    }
}
