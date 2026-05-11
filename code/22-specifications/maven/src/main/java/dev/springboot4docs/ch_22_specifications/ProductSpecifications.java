package dev.springboot4docs.ch_22_specifications;

import org.springframework.data.jpa.domain.Specification;

public final class ProductSpecifications {

	private ProductSpecifications() {
	}

	public static Specification<Product> nameContains(String q) {
		return (root, query, builder) -> {
			if (q == null || q.isBlank()) {
				return null;
			}
			return builder.like(builder.lower(root.get("name")), "%" + q.toLowerCase() + "%");
		};
	}

	public static Specification<Product> category(String category) {
		return (root, query, builder) -> {
			if (category == null || category.isBlank()) {
				return null;
			}
			return builder.equal(builder.lower(root.get("category")), category.toLowerCase());
		};
	}

	public static Specification<Product> priceBetween(int minCents, int maxCents) {
		return (root, query, builder) -> builder.between(root.get("priceCents"), minCents, maxCents);
	}

	public static Specification<Product> inStock() {
		return (root, query, builder) -> builder.isTrue(root.get("inStock"));
	}

}
