package com.pos.api.service;

import com.pos.api.dto.CategoryRequestDto;
import com.pos.api.dto.CategoryResponseDto;
import com.pos.api.entity.Category;
import com.pos.api.exception.ApiException;
import com.pos.api.repository.CategoryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public List<CategoryResponseDto> getAll() {
        return categoryRepository.findAll().stream().map(this::toResponse).toList();
    }

    public CategoryResponseDto create(CategoryRequestDto request) {
        categoryRepository.findByNameIgnoreCase(request.getName().trim())
                .ifPresent(c -> {
                    throw new ApiException(HttpStatus.CONFLICT, "Category name already exists.");
                });

        Category category = new Category();
        category.setName(request.getName().trim());
        category.setDescription(request.getDescription() == null ? null : request.getDescription().trim());
        return toResponse(categoryRepository.save(category));
    }

    public Category requireById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Category not found: " + id));
    }

    private CategoryResponseDto toResponse(Category category) {
        CategoryResponseDto dto = new CategoryResponseDto();
        dto.setId(category.getId());
        dto.setName(category.getName());
        dto.setDescription(category.getDescription());
        return dto;
    }
}
