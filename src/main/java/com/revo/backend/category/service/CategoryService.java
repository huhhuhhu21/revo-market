
package com.revo.backend.category.service;

import com.revo.backend.category.CategoryStatus;
import com.revo.backend.category.dto.CategoryListResponse;

import com.revo.backend.category.repository.CategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public CategoryListResponse getActiveCategories() {

        List<CategoryListResponse.CategoryItem> items =
                categoryRepository
                        .findByStatusOrderByNameAsc(CategoryStatus.ACTIVE)
                        .stream()
                        .map(category -> new CategoryListResponse.CategoryItem(
                                category.getId(),
                                category.getName()
                        ))
                        .toList();

        return new CategoryListResponse(items);
    }
}
