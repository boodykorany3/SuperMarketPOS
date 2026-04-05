package com.pos.api.service;

import com.pos.api.dto.SaleItemRequestDto;
import com.pos.api.dto.SaleItemResponseDto;
import com.pos.api.dto.SaleRequestDto;
import com.pos.api.dto.SaleResponseDto;
import com.pos.api.entity.Customer;
import com.pos.api.entity.Product;
import com.pos.api.entity.Sale;
import com.pos.api.entity.SaleItem;
import com.pos.api.entity.User;
import com.pos.api.exception.ApiException;
import com.pos.api.repository.ProductRepository;
import com.pos.api.repository.SaleRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class SaleService {

    private final SaleRepository saleRepository;
    private final ProductRepository productRepository;
    private final UserService userService;
    private final CustomerService customerService;
    private final BranchService branchService;
    private final AccountingService accountingService;

    public SaleService(SaleRepository saleRepository,
                       ProductRepository productRepository,
                       UserService userService,
                       CustomerService customerService,
                       BranchService branchService,
                       AccountingService accountingService) {
        this.saleRepository = saleRepository;
        this.productRepository = productRepository;
        this.userService = userService;
        this.customerService = customerService;
        this.branchService = branchService;
        this.accountingService = accountingService;
    }

    @Transactional
    public SaleResponseDto createSale(SaleRequestDto request) {
        User user = userService.requireById(request.getUserId());
        Customer customer = request.getCustomerId() == null ? null : customerService.requireById(request.getCustomerId());

        Sale sale = new Sale();
        sale.setDate(LocalDateTime.now());
        sale.setInvoiceNumber("TMP-" + UUID.randomUUID());
        sale.setStatus(Sale.STATUS_COMPLETED);
        sale.setUser(user);
        sale.setBranch(user.getBranch() != null ? user.getBranch() : branchService.ensureMainBranch());
        sale.setCustomer(customer);

        BigDecimal totalAmount = BigDecimal.ZERO;
        List<SaleItem> saleItems = new ArrayList<>();

        for (SaleItemRequestDto itemRequest : request.getItems()) {
            Product product = productRepository.findByIdForUpdate(itemRequest.getProductId())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Product not found: " + itemRequest.getProductId()));

            int requestedQty = itemRequest.getQuantity();
            if (product.getQuantity() < requestedQty) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "Insufficient stock for product " + product.getName() + ". Available: " + product.getQuantity());
            }

            product.setQuantity(product.getQuantity() - requestedQty);

            SaleItem saleItem = new SaleItem();
            saleItem.setSale(sale);
            saleItem.setProduct(product);
            saleItem.setQuantity(requestedQty);
            saleItem.setPrice(product.getPrice());

            BigDecimal lineTotal = product.getPrice().multiply(BigDecimal.valueOf(requestedQty));
            saleItem.setTotal(lineTotal);
            saleItems.add(saleItem);

            totalAmount = totalAmount.add(lineTotal);
        }

        sale.setItems(saleItems);
        sale.setTotalAmount(totalAmount);
        Sale saved = saleRepository.save(sale);
        saved.setInvoiceNumber(formatInvoiceNumber(saved.getId()));
        saved = saleRepository.save(saved);
        accountingService.postSaleCompletedJournal(saved);
        return toResponse(saved);
    }

    public List<SaleResponseDto> getAll(Long branchId) {
        if (branchId == null) {
            return saleRepository.findAll().stream().map(this::toResponse).toList();
        }
        branchService.requireById(branchId);
        return saleRepository.findByBranchIdOrderByDateDesc(branchId).stream().map(this::toResponse).toList();
    }

    public SaleResponseDto getById(Long id) {
        Sale sale = saleRepository.findDetailedById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Sale not found: " + id));
        return toResponse(sale);
    }

    @Transactional
    public SaleResponseDto cancelSale(Long id) {
        return processSaleAction(id, Sale.STATUS_CANCELED);
    }

    @Transactional
    public SaleResponseDto returnSale(Long id) {
        return processSaleAction(id, Sale.STATUS_RETURNED);
    }

    private SaleResponseDto toResponse(Sale sale) {
        SaleResponseDto dto = new SaleResponseDto();
        dto.setId(sale.getId());
        dto.setInvoiceNumber(sale.getInvoiceNumber());
        dto.setStatus(sale.getStatus());
        dto.setDate(sale.getDate());
        dto.setTotalAmount(sale.getTotalAmount());
        dto.setUserId(sale.getUser().getId());
        dto.setUsername(sale.getUser().getUsername());
        var branch = sale.getBranch() != null ? sale.getBranch() : sale.getUser().getBranch();
        if (branch != null) {
            dto.setBranchId(branch.getId());
            dto.setBranchCode(branch.getCode());
            dto.setBranchName(branch.getName());
        }
        if (sale.getCustomer() != null) {
            dto.setCustomerId(sale.getCustomer().getId());
            dto.setCustomerName(sale.getCustomer().getName());
        }
        dto.setItems(sale.getItems().stream().map(this::toItemResponse).toList());
        return dto;
    }

    private SaleItemResponseDto toItemResponse(SaleItem item) {
        SaleItemResponseDto dto = new SaleItemResponseDto();
        dto.setId(item.getId());
        dto.setProductId(item.getProduct().getId());
        dto.setProductName(item.getProduct().getName());
        dto.setQuantity(item.getQuantity());
        dto.setPrice(item.getPrice());
        dto.setTotal(item.getTotal());
        return dto;
    }

    private SaleResponseDto processSaleAction(Long id, String newStatus) {
        Sale sale = saleRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Sale not found: " + id));

        String currentStatus = sale.getStatus() == null ? Sale.STATUS_COMPLETED : sale.getStatus();
        if (!Sale.STATUS_COMPLETED.equals(currentStatus)) {
            throw new ApiException(HttpStatus.CONFLICT, "Invoice already processed.");
        }

        for (SaleItem item : sale.getItems()) {
            Product product = item.getProduct();
            product.setQuantity(product.getQuantity() + item.getQuantity());
        }

        sale.setStatus(newStatus);
        Sale saved = saleRepository.save(sale);
        if (Sale.STATUS_CANCELED.equals(newStatus)) {
            accountingService.postSaleCanceledJournal(saved);
        } else if (Sale.STATUS_RETURNED.equals(newStatus)) {
            accountingService.postSaleReturnedJournal(saved);
        }
        return toResponse(saved);
    }

    private String formatInvoiceNumber(Long id) {
        if (id == null) {
            return "Invoice #AUTO";
        }
        return "Invoice #" + String.format("%06d", id);
    }
}
