package dev.springboot4docs.ch_10_api_versioning;

import org.springframework.stereotype.Service;

@Service
public class ProductService {

	public ProductV1 findV1(long id) {
		return new ProductV1(id, "Widget");
	}

	public ProductV2 findV2(long id) {
		return new ProductV2(id, "Widget", 1999);
	}

}
