package app.lightmove.api.outreach.dto;

import jakarta.validation.constraints.Size;

/** What a signature says instead of the profile or the workspace; a null or blank field follows its source. */
public record SignatureFieldsDto(
        @Size(max = 160, message = "That name is too long") String name,
        @Size(max = 120, message = "That title is too long") String title,
        @Size(max = 40, message = "That phone number is too long") String phone,
        @Size(max = 255, message = "That address is too long") String linkedinUrl,
        @Size(max = 160, message = "That company name is too long") String company,
        @Size(max = 255, message = "That address is too long") String website,
        @Size(max = 200, message = "That address is too long") String address) {}
