
package com.revo.backend.category.repository;

import com.revo.backend.category.CategoryStatus;
import com.revo.backend.category.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
public interface CategoryRepository
        extends JpaRepository<Category, Long> {

    List<Category> findByStatusOrderByNameAsc(
            CategoryStatus status
    );

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT c FROM Category c WHERE c.id = :id")
    Optional<Category> findByIdForListing(
            @Param("id") Long id
    );
}
