package com.pos.api.repository;

import com.pos.api.entity.JournalEntry;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {

    @EntityGraph(attributePaths = {"branch", "lines", "lines.account"})
    @Query("""
            select distinct e
            from JournalEntry e
            where (:branchId is null or e.branch.id = :branchId)
              and (:fromDate is null or e.entryDate >= :fromDate)
              and (:toDate is null or e.entryDate <= :toDate)
            order by e.entryDate desc, e.id desc
            """)
    List<JournalEntry> findForListing(@Param("branchId") Long branchId,
                                      @Param("fromDate") LocalDate fromDate,
                                      @Param("toDate") LocalDate toDate);

    boolean existsByReferenceTypeAndReferenceId(String referenceType, Long referenceId);
}
