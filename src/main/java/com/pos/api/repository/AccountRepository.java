package com.pos.api.repository;

import com.pos.api.entity.Account;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    @Override
    @EntityGraph(attributePaths = {"parent"})
    List<Account> findAll();

    @EntityGraph(attributePaths = {"parent"})
    Optional<Account> findByCode(String code);

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);

    boolean existsByParentId(Long parentId);
}
