package dev.springboot4docs.ch_08_bean_validation;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank @Size(min = 2, max = 50) String name,
        @Email @NotNull String email,
        @Min(13) @Max(120) int age,
        @Pattern(regexp = "\\+?[0-9 -]{7,15}") String phone) {
}
