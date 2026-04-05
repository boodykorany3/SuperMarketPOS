package com.pos.api.service;

import com.pos.api.dto.ProductRequestDto;
import com.pos.api.dto.ProductResponseDto;
import com.pos.api.entity.Category;
import com.pos.api.entity.Product;
import com.pos.api.exception.ApiException;
import com.pos.api.repository.ProductRepository;
import com.pos.api.repository.SaleItemRepository;
import com.pos.api.repository.SaleRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryService categoryService;
    private final SaleRepository saleRepository;
    private final SaleItemRepository saleItemRepository;

    public ProductService(ProductRepository productRepository,
                          CategoryService categoryService,
                          SaleRepository saleRepository,
                          SaleItemRepository saleItemRepository) {
        this.productRepository = productRepository;
        this.categoryService = categoryService;
        this.saleRepository = saleRepository;
        this.saleItemRepository = saleItemRepository;
    }

    public List<ProductResponseDto> getAll() {
        return productRepository.findAll().stream().map(this::toResponse).toList();
    }

    public ProductResponseDto getById(Long id) {
        return toResponse(requireById(id));
    }

    public ProductResponseDto getByBarcode(String barcode) {
        Product product = productRepository.findByBarcode(barcode)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Product not found by barcode: " + barcode));
        return toResponse(product);
    }

    @Transactional
    public ProductResponseDto create(ProductRequestDto request) {
        if (productRepository.existsByBarcode(request.getBarcode().trim())) {
            throw new ApiException(HttpStatus.CONFLICT, "Barcode already exists.");
        }
        Product product = new Product();
        applyRequest(product, request);
        return toResponse(productRepository.save(product));
    }

    @Transactional
    public ProductResponseDto update(Long id, ProductRequestDto request) {
        Product product = requireById(id);
        String requestedBarcode = request.getBarcode().trim();
        if (!product.getBarcode().equals(requestedBarcode) && productRepository.existsByBarcode(requestedBarcode)) {
            throw new ApiException(HttpStatus.CONFLICT, "Barcode already exists.");
        }
        applyRequest(product, request);
        return toResponse(productRepository.save(product));
    }

    @Transactional
    public void delete(Long id) {
        delete(id, false);
    }

    @Transactional
    public void delete(Long id, boolean force) {
        Product product = requireById(id);
        if (product.getId() != null && saleItemRepository.existsByProductId(product.getId())) {
            if (!force) {
                throw new ApiException(HttpStatus.CONFLICT, "Product cannot be deleted because it is used in sales.");
            }
            purgeProductFromSales(product.getId());
        }
        try {
            productRepository.delete(product);
            // Force SQL execution inside this try block so FK violations are caught here.
            productRepository.flush();
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException(HttpStatus.CONFLICT, "Product cannot be deleted because it is used in sales.");
        } catch (RuntimeException ex) {
            if (isDeleteBlockedBySales(ex)) {
                throw new ApiException(HttpStatus.CONFLICT, "Product cannot be deleted because it is used in sales.");
            }
            throw ex;
        }
    }

    public Product requireById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Product not found: " + id));
    }

    private void applyRequest(Product product, ProductRequestDto request) {
        Category category = categoryService.requireById(request.getCategoryId());
        product.setName(request.getName().trim());
        product.setBarcode(request.getBarcode().trim());
        product.setPrice(request.getPrice());
        product.setQuantity(request.getQuantity());
        product.setCategory(category);
    }

    private ProductResponseDto toResponse(Product product) {
        ProductResponseDto dto = new ProductResponseDto();
        dto.setId(product.getId());
        dto.setName(product.getName());
        dto.setBarcode(product.getBarcode());
        dto.setPrice(product.getPrice());
        dto.setQuantity(product.getQuantity());
        dto.setCategoryId(product.getCategory().getId());
        dto.setCategoryName(product.getCategory().getName());
        return dto;
    }

    private boolean isDeleteBlockedBySales(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String lower = message.toLowerCase(Locale.ROOT);
                if (lower.contains("foreign key constraint")
                        || lower.contains("cannot delete or update a parent row")
                        || lower.contains("sale_items")
                        || lower.contains("product_id")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private void purgeProductFromSales(Long productId) {
        List<Long> affectedSaleIds = saleItemRepository.findSaleIdsByProductId(productId);
        if (affectedSaleIds == null || affectedSaleIds.isEmpty()) {
            return;
        }

        saleItemRepository.deleteByProductId(productId);

        for (Long saleId : affectedSaleIds) {
            if (saleId == null) {
                continue;
            }

            long itemCount = saleItemRepository.countBySaleId(saleId);
            if (itemCount <= 0) {
                saleRepository.deleteById(saleId);
                continue;
            }

            BigDecimal recalculatedTotal = saleItemRepository.sumTotalBySaleId(saleId);
            if (recalculatedTotal == null) {
                recalculatedTotal = BigDecimal.ZERO;
            }
            var saleOptional = saleRepository.findById(saleId);
            if (saleOptional.isPresent()) {
                saleOptional.get().setTotalAmount(recalculatedTotal);
            }
        }
    }
}
