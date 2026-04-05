package com.pos.api.repository;

import com.pos.api.entity.EmployeeProfile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmployeeProfileRepository extends JpaRepository<EmployeeProfile, Long> {

    @Override
    @EntityGraph(attributePaths = {"branch"})
    List<EmployeeProfile> findAll();

    @Override
    @EntityGraph(attributePaths = {"branch"})
    Optional<EmployeeProfile> findById(Long id);

    boolean existsByEmployeeCode(String employeeCode);

    boolean existsByEmployeeCodeAndIdNot(String employeeCode, Long id);

    @EntityGraph(attributePaths = {"branch"})
    List<EmployeeProfile> findByBranchId(Long branchId);
}
