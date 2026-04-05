package com.pos.api.repository;

import com.pos.api.entity.Branch;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BranchRepository extends JpaRepository<Branch, Long> {
    Optional<Branch> findByCode(String code);

    boolean existsByCode(String code);

    Optional<Branch> findByMainBranchTrue();

    List<Branch> findAllByOrderByCodeAsc();
}
