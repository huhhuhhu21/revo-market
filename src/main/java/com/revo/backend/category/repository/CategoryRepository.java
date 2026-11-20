
package com.revo.backend.category.repository;

import com.revo.backend.category.CategoryStatus;
import com.revo.backend.category.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoryRepository
        extends JpaRepository<Category, Long> {

    List<Category> findByStatusOrderByNameAsc(
            CategoryStatus status
    );
}
