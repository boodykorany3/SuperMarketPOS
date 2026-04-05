package com.pos.api.repository;

import com.pos.api.entity.SaleItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface SaleItemRepository extends JpaRepository<SaleItem, Long> {

    @Query("select (count(si) > 0) from SaleItem si where si.product.id = :productId")
    boolean existsByProductId(@Param("productId") Long productId);

    @Query("select distinct si.sale.id from SaleItem si where si.product.id = :productId")
    List<Long> findSaleIdsByProductId(@Param("productId") Long productId);

    @Modifying
    @Query("delete from SaleItem si where si.product.id = :productId")
    int deleteByProductId(@Param("productId") Long productId);

    @Query("select count(si) from SaleItem si where si.sale.id = :saleId")
    long countBySaleId(@Param("saleId") Long saleId);

    @Query("select sum(si.total) from SaleItem si where si.sale.id = :saleId")
    BigDecimal sumTotalBySaleId(@Param("saleId") Long saleId);
}
