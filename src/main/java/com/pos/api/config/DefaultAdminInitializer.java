package com.pos.api.config;

import com.pos.api.entity.Role;
import com.pos.api.entity.User;
import com.pos.api.entity.Branch;
import com.pos.api.repository.BranchRepository;
import com.pos.api.repository.SaleRepository;
import com.pos.api.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DefaultAdminInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final BranchRepository branchRepository;
    private final SaleRepository saleRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${pos.bootstrap.admin.username:admin}")
    private String adminUsername;

    @Value("${pos.bootstrap.admin.password:admin123}")
    private String adminPassword;

    @Value("${pos.bootstrap.owner.username:owner}")
    private String ownerUsername;

    @Value("${pos.bootstrap.owner.password:1234}")
    private String ownerPassword;

    @Value("${pos.bootstrap.main-branch.code:MAIN}")
    private String mainBranchCode;

    @Value("${pos.bootstrap.main-branch.name:Main Branch}")
    private String mainBranchName;

    public DefaultAdminInitializer(UserRepository userRepository,
                                   BranchRepository branchRepository,
                                   SaleRepository saleRepository,
                                   PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.branchRepository = branchRepository;
        this.saleRepository = saleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        Branch mainBranch = ensureMainBranch();
        backfillUsersWithoutBranch(mainBranch);
        backfillSalesWithoutBranch(mainBranch);

        ensureBootstrapUser(ownerUsername, ownerPassword, Role.OWNER, mainBranch);
        ensureBootstrapUser(adminUsername, adminPassword, Role.ADMIN, mainBranch);
    }

    private void backfillUsersWithoutBranch(Branch mainBranch) {
        var usersWithoutBranch = userRepository.findAllByBranchIsNull();
        if (usersWithoutBranch.isEmpty()) {
            return;
        }
        for (User user : usersWithoutBranch) {
            user.setBranch(mainBranch);
        }
        userRepository.saveAll(usersWithoutBranch);
    }

    private void backfillSalesWithoutBranch(Branch mainBranch) {
        var salesWithoutBranch = saleRepository.findAllByBranchIsNull();
        if (salesWithoutBranch.isEmpty()) {
            return;
        }
        for (var sale : salesWithoutBranch) {
            Branch userBranch = sale.getUser() == null ? null : sale.getUser().getBranch();
            sale.setBranch(userBranch != null ? userBranch : mainBranch);
        }
        saleRepository.saveAll(salesWithoutBranch);
    }

    private Branch ensureMainBranch() {
        return branchRepository.findByMainBranchTrue().orElseGet(() -> {
            Branch branch = new Branch();
            String code = mainBranchCode == null ? "" : mainBranchCode.trim();
            String name = mainBranchName == null ? "" : mainBranchName.trim();
            branch.setCode(code.isEmpty() ? "MAIN" : code.toUpperCase());
            branch.setName(name.isEmpty() ? "Main Branch" : name);
            branch.setMainBranch(true);
            branch.setActive(true);
            return branchRepository.save(branch);
        });
    }

    private void ensureBootstrapUser(String rawUsername,
                                     String rawPassword,
                                     Role role,
                                     Branch mainBranch) {
        String username = rawUsername == null ? "" : rawUsername.trim();
        String password = rawPassword == null ? "" : rawPassword.trim();
        if (username.isEmpty() || password.isEmpty()) {
            return;
        }

        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            User created = new User();
            created.setUsername(username);
            created.setPassword(passwordEncoder.encode(password));
            created.setRole(role);
            created.setBranch(mainBranch);
            userRepository.save(created);
            return;
        }

        boolean changed = false;
        if (user.getRole() != role) {
            user.setRole(role);
            changed = true;
        }
        if (user.getBranch() == null) {
            user.setBranch(mainBranch);
            changed = true;
        }
        if (changed) {
            userRepository.save(user);
        }
    }
}
