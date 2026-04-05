package com.pos.api.repository;

import com.pos.api.entity.JournalLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface JournalLineRepository extends JpaRepository<JournalLine, Long> {

    @Query("""
            select jl.account.id, sum(jl.debit), sum(jl.credit)
            from JournalLine jl
            join jl.entry e
            where (:branchId is null or e.branch.id = :branchId)
              and (:fromDate is null or e.entryDate >= :fromDate)
              and (:toDate is null or e.entryDate <= :toDate)
            group by jl.account.id
            """)
    List<Object[]> aggregateByAccount(@Param("fromDate") LocalDate fromDate,
                                      @Param("toDate") LocalDate toDate,
                                      @Param("branchId") Long branchId);
}
