
package com.revo.backend.category;

import org.junit.jupiter.api.Test;

import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CategoryApiTest {

    @Test
    void shouldQueryActiveCategoriesAndReturnExpectedJson()
            throws Exception {

        CategoryRepository repository =
                mock(CategoryRepository.class);

        Category category = mock(Category.class);

        when(category.getId()).thenReturn(7L);
        when(category.getName()).thenReturn("Đồ điện tử");

        when(repository.findByStatusOrderByNameAsc(
                CategoryStatus.ACTIVE
        )).thenReturn(List.of(category));

        CategoryService service = new CategoryService(repository);
        CategoryController controller = new CategoryController(service);

        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .build();

        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(7))
                .andExpect(jsonPath("$.items[0].name").value("Đồ điện tử"))
                .andExpect(jsonPath("$.items[0].status").doesNotExist());

        verify(repository).findByStatusOrderByNameAsc(
                CategoryStatus.ACTIVE
        );
    }

    @Test
    void shouldReturnEmptyItemsWhenNoActiveCategories()
            throws Exception {

        CategoryRepository repository =
                mock(CategoryRepository.class);

        when(repository.findByStatusOrderByNameAsc(
                CategoryStatus.ACTIVE
        )).thenReturn(List.of());

        CategoryService service = new CategoryService(repository);
        CategoryController controller = new CategoryController(service);

        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .build();

        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }
}
