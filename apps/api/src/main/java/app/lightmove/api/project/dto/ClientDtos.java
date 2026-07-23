package app.lightmove.api.project.dto;

import app.lightmove.api.project.constant.ClientRepStatus;
import app.lightmove.api.project.constant.ClientType;
import app.lightmove.api.project.constant.ProjectHealth;
import app.lightmove.api.project.constant.ProjectStage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * The HTTP contract for the client registry and its representatives. Requests validate here; the
 * service resolves a DB-picked company's canonical fields from the universe rather than trusting them.
 */
public final class ClientDtos {

    private ClientDtos() {
    }

    /** One row of the Clients table. {@code type} and every count are derived, never stored. */
    public record ClientListResponse(
            UUID id,
            String name,
            ClientType type,
            String sector,
            String hqCountry,
            long activeMandates,
            long deliveredMandates,
            List<RepAvatar> contacts,
            ViewerSummary viewers
    ) {}

    public record RepAvatar(String fullName, ClientRepStatus status) {}

    public record ViewerSummary(long active, long invited) {}

    public record ClientDetailResponse(
            UUID id,
            String name,
            String sector,
            String hqCountry,
            String domain,
            String offLimitsNote,
            long activeMandates,
            long deliveredMandates,
            List<RepresentativeResponse> representatives,
            List<ClientMandateResponse> mandates
    ) {}

    public record RepresentativeResponse(
            UUID id,
            String fullName,
            String position,
            String email,
            ClientRepStatus status
    ) {}

    /** A mandate as the client drawer lists it — enough to render the row and open the project. */
    public record ClientMandateResponse(
            UUID id,
            String positionTitle,
            ProjectStage stage,
            ProjectHealth health,
            String leadName,
            LocalDate targetDate
    ) {}

    /** A company chosen from the universe — its rebuild-stable key. Null on the request means custom. */
    public record CompanyPickDto(
            @NotBlank(message = "Choose a company from the database")
            String source,

            @NotBlank(message = "Choose a company from the database")
            String sourceId
    ) {}

    /**
     * Create a client. Either {@code company} names a universe row (its canonical name/domain/hq are
     * resolved server-side), or {@code customName} types a new record in. {@code sector}/{@code hqCountry}
     * are editable regardless. An optional {@code primaryContact} gets a portal invite immediately.
     */
    public record CreateClientRequest(
            @Valid CompanyPickDto company,

            @Size(max = 160, message = "That name is too long")
            String customName,

            @Size(max = 160, message = "That domain is too long")
            String customDomain,

            @Size(max = 96, message = "That sector is too long")
            String sector,

            @Size(max = 64, message = "That location is too long")
            String hqCountry,

            @Valid PrimaryContactRequest primaryContact
    ) {}

    /** The first representative, created with the client. All three fields required to send the invite. */
    public record PrimaryContactRequest(
            @NotBlank(message = "Enter the contact's name")
            @Size(max = 160, message = "That name is too long")
            String fullName,

            @Size(max = 160, message = "That position is too long")
            String position,

            @NotBlank(message = "Enter a work email")
            @Email(message = "Enter a valid work email address")
            @Size(max = 320, message = "That email is too long")
            String email
    ) {}

    public record InviteRepresentativeRequest(
            @NotBlank(message = "Enter the representative's name")
            @Size(max = 160, message = "That name is too long")
            String fullName,

            @Size(max = 160, message = "That position is too long")
            String position,

            @NotBlank(message = "Enter a work email")
            @Email(message = "Enter a valid work email address")
            @Size(max = 320, message = "That email is too long")
            String email
    ) {}

    public record UpdateClientRequest(
            @NotBlank(message = "Enter the client's name")
            @Size(max = 160, message = "That name is too long")
            String name,

            @Size(max = 96, message = "That sector is too long")
            String sector,

            @Size(max = 64, message = "That location is too long")
            String hqCountry,

            @Size(max = 160, message = "That domain is too long")
            String domain,

            String offLimitsNote
    ) {}
}
