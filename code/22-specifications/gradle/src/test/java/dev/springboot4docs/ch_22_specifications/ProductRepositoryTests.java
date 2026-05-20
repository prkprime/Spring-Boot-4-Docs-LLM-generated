package dev.springboot4docs.ch_22_specifications;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

@DataJpaTest
class ProductRepositoryTests {

    private static final Instant BASE_TIME = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired
    private ProductRepository products;

    @BeforeEach
    void seedProducts() {
        this.products.saveAll(List.of(
                product("Trail Running Shoes", "shoes", 12999, true, 1),
                product("City Walking Shoes", "shoes", 8999, true, 2),
                product("Leather Dress Shoes", "shoes", 15999, false, 3),
                product("Canvas Skate Shoes", "shoes", 6499, true, 4),
                product("Waterproof Hiking Boots", "shoes", 18999, true, 5),
                product("Merino Running Socks", "accessories", 1999, true, 6),
                product("Compression Socks", "accessories", 2499, false, 7),
                product("Wool Beanie", "accessories", 2299, true, 8),
                product("Cycling Gloves", "accessories", 3499, true, 9),
                product("Reflective Running Vest", "accessories", 4999, false, 10),
                product("Trail Backpack", "bags", 7999, true, 11),
                product("Laptop Backpack", "bags", 10999, true, 12),
                product("Duffel Bag", "bags", 6999, false, 13),
                product("Hydration Pack", "bags", 5999, true, 14),
                product("Travel Tote", "bags", 5499, false, 15),
                product("Insulated Water Bottle", "gear", 2999, true, 16),
                product("Foam Roller", "gear", 3999, true, 17),
                product("Yoga Mat", "gear", 4599, false, 18),
                product("Resistance Bands", "gear", 1999, true, 19),
                product("Training Stopwatch", "gear", 2599, true, 20)));
    }

    @Test
    void filtersByCaseInsensitiveNameAndCategory() {
        Specification<Product> spec = Specification.allOf(
                ProductSpecifications.nameContains("running"),
                ProductSpecifications.category("shoes"));

        Page<Product> page = this.products.findAll(spec, PageRequest.of(0, 10, Sort.by("name")));

        assertThat(page.getContent()).extracting(Product::getName).containsExactly("Trail Running Shoes");
    }

    @Test
    void combinesCategoryPriceRangeAndStockFilters() {
        Specification<Product> spec = Specification.allOf(
                ProductSpecifications.category("bags"),
                ProductSpecifications.priceBetween(5000, 8000),
                ProductSpecifications.inStock());

        Page<Product> page = this.products.findAll(spec, PageRequest.of(0, 10, Sort.by("priceCents")));

        assertThat(page.getContent()).extracting(Product::getName)
            .containsExactly("Hydration Pack", "Trail Backpack");
    }

    @Test
    void nullSpecificationsAreIgnoredWhenComposingOptionalFilters() {
        Specification<Product> spec = Specification.allOf(
                ProductSpecifications.nameContains(""),
                ProductSpecifications.category(null),
                ProductSpecifications.priceBetween(2000, 3500),
                ProductSpecifications.inStock());

        Page<Product> page = this.products.findAll(spec, PageRequest.of(0, 10, Sort.by("priceCents", "name")));

        assertThat(page.getContent()).extracting(Product::getName)
            .containsExactly("Wool Beanie", "Training Stopwatch", "Insulated Water Bottle", "Cycling Gloves");
    }

    @Test
    void paginationAndSortingApplyToSpecificationResults() {
        Specification<Product> spec = Specification.allOf(
                ProductSpecifications.category("gear"),
                ProductSpecifications.inStock());

        Page<Product> page = this.products.findAll(spec,
                PageRequest.of(0, 2, Sort.by("createdAt").descending()));

        assertThat(page.getTotalElements()).isEqualTo(4);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getContent()).extracting(Product::getName)
            .containsExactly("Training Stopwatch", "Resistance Bands");
    }

    private static Product product(String name, String category, int priceCents, boolean inStock, long minutes) {
        return new Product(name, category, priceCents, inStock, BASE_TIME.plusSeconds(minutes * 60));
    }

}
