package app.lightmove.api.project.dto;

import app.lightmove.api.common.persona.model.HiringPersona;
import jakarta.validation.constraints.Size;
import java.util.List;

/** An agency client's persona. Lists arrive whole and replace what was there. */
public record UpdateClientPersonaRequest(
        @Size(max = 2000, message = "Keep the summary under 2,000 characters")
        String summary,

        @Size(max = HiringPersona.MAX_LIST_ITEMS, message = "List at most 20 sectors")
        List<@Size(max = 96, message = "That sector is too long") String> sectors,

        @Size(max = HiringPersona.MAX_LIST_ITEMS, message = "List at most 20 competitors")
        List<@Size(max = 160, message = "That competitor name is too long") String> competitors,

        @Size(max = HiringPersona.MAX_LIST_ITEMS, message = "List at most 20 geographies")
        List<@Size(max = 64, message = "That geography is too long") String> geographies,

        @Size(max = 2000, message = "Keep the notes under 2,000 characters")
        String notes
) {

    public HiringPersona toPersona() {
        return new HiringPersona(summary, sectors, competitors, geographies, notes);
    }
}
