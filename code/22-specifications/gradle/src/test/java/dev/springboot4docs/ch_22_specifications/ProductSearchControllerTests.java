package dev.springboot4docs.ch_22_specifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProductSearchController.class)
class ProductSearchControllerTests {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ProductRepository products;

    @Test
    void bindsOptionalFiltersAndPageableBeforeCallingRepository() throws Exception {
        when(this.products.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        this.mvc.perform(get("/products")
            .param("q", "run")
            .param("category", "shoes")
            .param("minPrice", "5000")
            .param("maxPrice", "15000")
            .param("inStock", "true")
            .param("page", "1")
            .param("size", "5")
            .param("sort", "priceCents,desc"))
            .andExpect(status().isOk());

        ArgumentCaptor<Specification<Product>> spec = ArgumentCaptor.captor();
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(this.products).findAll(spec.capture(), pageable.capture());

        assertThat(spec.getValue()).isNotNull();
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
        assertThat(pageable.getValue().getSort().getOrderFor("priceCents"))
            .extracting(Sort.Order::getDirection)
            .isEqualTo(Sort.Direction.DESC);
    }

}
