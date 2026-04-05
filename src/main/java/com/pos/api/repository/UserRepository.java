package com.pos.api.repository;

import com.pos.api.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    @Override
    @EntityGraph(attributePaths = {"branch"})
    List<User> findAll();

    @Override
    @EntityGraph(attributePaths = {"branch"})
    Optional<User> findById(Long id);

    @EntityGraph(attributePaths = {"branch"})
    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    @EntityGraph(attributePaths = {"branch"})
    List<User> findAllByBranchIsNull();
}
