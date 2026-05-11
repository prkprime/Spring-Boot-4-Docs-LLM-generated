package dev.springboot4docs.ch_09_error_handling;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		logger.warn("Validation failed: {}", ex.getMessage());

		ResponseEntity<Object> response = super.handleMethodArgumentNotValid(ex, headers, status, request);
		ProblemDetail problemDetail = asProblemDetail(response.getBody(), status);
		problemDetail.setProperty("errors", fieldErrors(ex));
		return ResponseEntity.status(response.getStatusCode()).headers(response.getHeaders()).body(problemDetail);
	}

	@ExceptionHandler(OrderNotFoundException.class)
	public ResponseEntity<ProblemDetail> handleOrderNotFound(OrderNotFoundException ex, HttpServletRequest request) {
		logger.warn("Order lookup failed", ex);

		ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
		problemDetail.setType(URI.create("https://api.example/errors/order-not-found"));
		problemDetail.setInstance(URI.create(request.getRequestURI()));
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problemDetail);
	}

	@ExceptionHandler(OutOfStockException.class)
	public ResponseEntity<ProblemDetail> handleOutOfStock(OutOfStockException ex, HttpServletRequest request) {
		logger.warn("Order placement failed", ex);

		ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
		problemDetail.setType(URI.create("https://api.example/errors/out-of-stock"));
		problemDetail.setInstance(URI.create(request.getRequestURI()));
		problemDetail.setProperty("sku", ex.getSku());
		return ResponseEntity.status(HttpStatus.CONFLICT).body(problemDetail);
	}

	private static ProblemDetail asProblemDetail(Object body, HttpStatusCode status) {
		if (body instanceof ProblemDetail problemDetail) {
			return problemDetail;
		}
		return ProblemDetail.forStatus(status);
	}

	private static Map<String, String> fieldErrors(MethodArgumentNotValidException ex) {
		Map<String, String> errors = new LinkedHashMap<>();
		for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
			errors.put(fieldError.getField(), fieldError.getDefaultMessage());
		}
		return errors;
	}

}
