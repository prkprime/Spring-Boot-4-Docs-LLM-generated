package dev.springboot4docs.ch_22_specifications;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String name;

	private String category;

	private int priceCents;

	private boolean inStock;

	private Instant createdAt;

	public Product(String name, String category, int priceCents, boolean inStock, Instant createdAt) {
		this.name = name;
		this.category = category;
		this.priceCents = priceCents;
		this.inStock = inStock;
		this.createdAt = createdAt;
	}

}
