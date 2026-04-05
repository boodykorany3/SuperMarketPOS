package com.pos.api.repository;

import com.pos.api.entity.Sale;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SaleRepository extends JpaRepository<Sale, Long> {
    @Override
    @EntityGraph(attributePaths = {"user", "user.branch", "branch", "customer", "items", "items.product"})
    List<Sale> findAll();

    @EntityGraph(attributePaths = {"user", "user.branch", "branch", "customer", "items", "items.product"})
    List<Sale> findByBranchIdOrderByDateDesc(Long branchId);

    @EntityGraph(attributePaths = {"user", "user.branch"})
    List<Sale> findAllByBranchIsNull();

    @EntityGraph(attributePaths = {"user", "user.branch", "branch", "customer", "items", "items.product"})
    @Query("select s from Sale s where s.id = :id")
    Optional<Sale> findDetailedById(@Param("id") Long id);

    Optional<Sale> findByInvoiceNumber(String invoiceNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Sale s where s.id = :id")
    Optional<Sale> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select count(s)
            from Sale s
            where s.branch.id = :branchId
              and s.status = :status
              and s.date >= :fromDate
              and s.date < :toDate
            """)
    long countByBranchAndStatusBetween(@Param("branchId") Long branchId,
                                       @Param("status") String status,
                                       @Param("fromDate") LocalDateTime fromDate,
                                       @Param("toDate") LocalDateTime toDate);

    @Query("""
            select sum(s.totalAmount)
            from Sale s
            where s.branch.id = :branchId
              and s.status = :status
              and s.date >= :fromDate
              and s.date < :toDate
            """)
    BigDecimal sumByBranchAndStatusBetween(@Param("branchId") Long branchId,
                                           @Param("status") String status,
                                           @Param("fromDate") LocalDateTime fromDate,
                                           @Param("toDate") LocalDateTime toDate);

    @EntityGraph(attributePaths = {"user", "user.branch", "branch", "items", "items.product"})
    @Query("""
            select s
            from Sale s
            where (:branchId is null or (s.branch is not null and s.branch.id = :branchId))
              and (:fromDate is null or s.date >= :fromDate)
              and (:toDate is null or s.date < :toDate)
            order by s.date asc, s.id asc
            """)
    List<Sale> findForAccountingSync(@Param("branchId") Long branchId,
                                     @Param("fromDate") LocalDateTime fromDate,
                                     @Param("toDate") LocalDateTime toDate);
}
