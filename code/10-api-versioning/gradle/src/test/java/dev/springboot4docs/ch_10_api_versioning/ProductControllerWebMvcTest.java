package dev.springboot4docs.ch_10_api_versioning;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@WebMvcTest(ProductController.class)
class ProductControllerWebMvcTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private ProductService products;

    @Test
    void versionOneHeaderReturnsVersionOneShape() throws Exception {
        when(this.products.findV1(1)).thenReturn(new ProductV1(1, "Widget"));

        MvcTestResult result = this.mvc.get().uri("/products/{id}", 1)
                .header("X-API-Version", "1")
                .exchange();

        assertThat(result).hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .bodyJson()
                .extractingPath("$.name").asString().isEqualTo("Widget");
        assertThat(result.getResponse().getContentAsString()).doesNotContain("priceCents");
    }

    @Test
    void versionTwoHeaderReturnsVersionTwoShape() {
        when(this.products.findV2(1)).thenReturn(new ProductV2(1, "Widget", 1999));

        this.mvc.get().uri("/products/{id}", 1)
                .header("X-API-Version", "2")
                .assertThat()
                .hasStatusOk()
                .hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .bodyJson()
                .extractingPath("$.priceCents").isEqualTo(1999);
    }

    @Test
    void missingHeaderFallsBackToDefaultVersion() throws Exception {
        when(this.products.findV1(1)).thenReturn(new ProductV1(1, "Widget"));

        MvcTestResult result = this.mvc.get().uri("/products/{id}", 1)
                .exchange();

        assertThat(result).hasStatusOk()
                .bodyJson()
                .extractingPath("$.id").isEqualTo(1);
        assertThat(result.getResponse().getContentAsString()).doesNotContain("priceCents");
    }

    @Test
    void unsupportedVersionDoesNotReturnSuccess() {
        MvcTestResult result = this.mvc.get().uri("/products/{id}", 1)
                .header("X-API-Version", "99")
                .exchange();

        assertThat(result.getResponse().getStatus()).isBetween(400, 599);
    }

}
