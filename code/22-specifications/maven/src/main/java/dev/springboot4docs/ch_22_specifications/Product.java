package dev.springboot4docs.ch_22_specifications;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;

@Entity
public class Product {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String name;

	private String category;

	private int priceCents;

	private boolean inStock;

	private Instant createdAt;

	protected Product() {
	}

	public Product(String name, String category, int priceCents, boolean inStock, Instant createdAt) {
		this.name = name;
		this.category = category;
		this.priceCents = priceCents;
		this.inStock = inStock;
		this.createdAt = createdAt;
	}

	public Long getId() {
		return this.id;
	}

	public String getName() {
		return this.name;
	}

	public String getCategory() {
		return this.category;
	}

	public int getPriceCents() {
		return this.priceCents;
	}

	public boolean isInStock() {
		return this.inStock;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

}
