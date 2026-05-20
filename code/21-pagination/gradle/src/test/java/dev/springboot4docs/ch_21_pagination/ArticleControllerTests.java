package dev.springboot4docs.ch_21_pagination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ArticleController.class)
class ArticleControllerTests {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ArticleRepository articles;

    @Test
    void bindsPageSizeAndSortQueryParametersToPageable() throws Exception {
        when(this.articles.findByTitleContainingIgnoreCase(eq("spring"), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of()));

        this.mvc.perform(get("/articles")
            .param("q", "spring")
            .param("page", "2")
            .param("size", "15")
            .param("sort", "publishedAt,desc"))
            .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(this.articles).findByTitleContainingIgnoreCase(eq("spring"), pageable.capture());

        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(15);
        assertThat(pageable.getValue().getSort().getOrderFor("publishedAt"))
            .extracting(Sort.Order::getDirection)
            .isEqualTo(Sort.Direction.DESC);
    }

}
