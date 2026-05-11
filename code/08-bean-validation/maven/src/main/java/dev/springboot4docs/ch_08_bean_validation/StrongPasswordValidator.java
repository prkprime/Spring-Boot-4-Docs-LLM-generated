package dev.springboot4docs.ch_08_bean_validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {

	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		if (value == null || value.length() < 10) {
			return false;
		}
		boolean hasDigit = false;
		boolean hasUpper = false;
		boolean hasSymbol = false;
		for (int i = 0; i < value.length(); i++) {
			char ch = value.charAt(i);
			if (Character.isDigit(ch)) {
				hasDigit = true;
			}
			else if (Character.isUpperCase(ch)) {
				hasUpper = true;
			}
			else if (!Character.isLetterOrDigit(ch)) {
				hasSymbol = true;
			}
		}
		return hasDigit && hasUpper && hasSymbol;
	}

}
