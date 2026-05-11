package dev.springboot4docs.ch_49_passkeys_webauthn;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class PasskeyExceptionHandler {

	@ExceptionHandler(PasskeyException.class)
	ProblemDetail passkeyProblem(PasskeyException ex) {
		ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
		problem.setTitle("Passkey ceremony rejected");
		problem.setDetail(ex.getMessage());
		return problem;
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ProblemDetail validationProblem(MethodArgumentNotValidException ex) {
		ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
		problem.setTitle("Invalid passkey request");
		problem.setDetail("Required ceremony fields are missing or invalid");
		return problem;
	}

}
