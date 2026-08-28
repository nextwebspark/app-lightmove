package app.lightmove.api.position.dto;

import app.lightmove.api.position.constant.BenefitFrequency;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** One allowance in a package — the same shape reads and writes. */
public record BenefitDto(
        @NotBlank(message = "Name the benefit")
        @Size(max = 120, message = "That benefit name is too long")
        String name,

        /** Absent when the package names the allowance without quantifying it, which is common. */
        @Min(value = 0, message = "An allowance cannot be negative")
        Long amount,

        @NotNull(message = "Choose monthly or yearly")
        BenefitFrequency frequency
) {}
