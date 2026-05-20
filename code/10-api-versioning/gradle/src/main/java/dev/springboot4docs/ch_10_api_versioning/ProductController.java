package dev.springboot4docs.ch_10_api_versioning;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/*
 * This controller uses header-based versioning from application.yml. To try the
 * same handlers with another exclusive strategy, replace that property with
 * path-segment, media-type-parameter, or query-parameter configuration.
 */
@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductService products;

    public ProductController(ProductService products) {
        this.products = products;
    }

    @GetMapping(value = "/{id}", version = "1")
    public ProductV1 getProductV1(@PathVariable long id, HttpServletResponse response) {
        response.setHeader("Deprecation", "@1767225600");
        response.setHeader("Sunset", "Fri, 31 Dec 2027 23:59:59 GMT");
        return this.products.findV1(id);
    }

    @GetMapping(value = "/{id}", version = "2")
    public ProductV2 getProductV2(@PathVariable long id) {
        return this.products.findV2(id);
    }

}
