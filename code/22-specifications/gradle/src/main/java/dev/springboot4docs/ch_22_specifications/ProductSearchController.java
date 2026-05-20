package dev.springboot4docs.ch_22_specifications;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProductSearchController {

    private final ProductRepository products;

    public ProductSearchController(ProductRepository products) {
        this.products = products;
    }

    @GetMapping("/products")
    Page<Product> search(@RequestParam Optional<String> q, @RequestParam Optional<String> category,
            @RequestParam Optional<Integer> minPrice, @RequestParam Optional<Integer> maxPrice,
            @RequestParam Optional<Boolean> inStock, Pageable pageable) {
        Specification<Product> spec = Specification.allOf(
                q.map(ProductSpecifications::nameContains).orElse(null),
                category.map(ProductSpecifications::category).orElse(null),
                priceRange(minPrice, maxPrice),
                inStock.filter(Boolean::booleanValue).map((ignored) -> ProductSpecifications.inStock()).orElse(null));

        return this.products.findAll(spec, pageable);
    }

    private static Specification<Product> priceRange(Optional<Integer> minPrice, Optional<Integer> maxPrice) {
        if (minPrice.isEmpty() && maxPrice.isEmpty()) {
            return null;
        }
        return ProductSpecifications.priceBetween(minPrice.orElse(0), maxPrice.orElse(Integer.MAX_VALUE));
    }

}
