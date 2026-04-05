package com.pos;

import com.pos.api.PosApiApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

public final class DesktopWithApiLauncher {

    private DesktopWithApiLauncher() {}

    public static void main(String[] args) {
        ConfigurableApplicationContext apiContext = null;

        if (BackendMode.useApiSync()) {
            try {
                apiContext = SpringApplication.run(PosApiApplication.class);
            } catch (Exception exception) {
                System.err.println("API startup failed. Desktop will continue and use configured API endpoint.");
                exception.printStackTrace();
            }
        }

        try {
            Main.main(args);
        } finally {
            if (apiContext != null) {
                apiContext.close();
            }
        }
    }
}
