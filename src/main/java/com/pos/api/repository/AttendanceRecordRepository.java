package com.pos.api.repository;

import com.pos.api.entity.AttendanceRecord;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {

    @Override
    @EntityGraph(attributePaths = {"employee", "employee.branch"})
    Optional<AttendanceRecord> findById(Long id);

    @EntityGraph(attributePaths = {"employee", "employee.branch"})
    Optional<AttendanceRecord> findByEmployeeIdAndAttendanceDate(Long employeeId, LocalDate attendanceDate);

    @EntityGraph(attributePaths = {"employee", "employee.branch"})
    @Query("""
            select ar
            from AttendanceRecord ar
            where (:employeeId is null or ar.employee.id = :employeeId)
              and (:branchId is null or ar.employee.branch.id = :branchId)
              and (:fromDate is null or ar.attendanceDate >= :fromDate)
              and (:toDate is null or ar.attendanceDate <= :toDate)
            order by ar.attendanceDate desc, ar.id desc
            """)
    List<AttendanceRecord> findForListing(@Param("employeeId") Long employeeId,
                                          @Param("branchId") Long branchId,
                                          @Param("fromDate") LocalDate fromDate,
                                          @Param("toDate") LocalDate toDate);

    @EntityGraph(attributePaths = {"employee", "employee.branch"})
    @Query("""
            select ar
            from AttendanceRecord ar
            where ar.attendanceDate >= :fromDate
              and ar.attendanceDate <= :toDate
              and (:branchId is null or ar.employee.branch.id = :branchId)
            order by ar.employee.employeeCode asc, ar.attendanceDate asc
            """)
    List<AttendanceRecord> findForPayroll(@Param("fromDate") LocalDate fromDate,
                                          @Param("toDate") LocalDate toDate,
                                          @Param("branchId") Long branchId);
}
