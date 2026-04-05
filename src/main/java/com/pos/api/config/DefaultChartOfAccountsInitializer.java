package com.pos.api.config;

import com.pos.api.service.AccountingService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DefaultChartOfAccountsInitializer implements CommandLineRunner {

    private final AccountingService accountingService;

    public DefaultChartOfAccountsInitializer(AccountingService accountingService) {
        this.accountingService = accountingService;
    }

    @Override
    public void run(String... args) {
        accountingService.ensureDefaultChartOfAccounts();
    }
}
