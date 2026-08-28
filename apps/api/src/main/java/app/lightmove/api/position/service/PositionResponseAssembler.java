package app.lightmove.api.position.service;

import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.position.constant.CompetencyPanel;
import app.lightmove.api.position.dto.AssessmentDto;
import app.lightmove.api.position.dto.BenefitDto;
import app.lightmove.api.position.dto.CompensationDto;
import app.lightmove.api.position.dto.CompetencyDto;
import app.lightmove.api.position.dto.CriterionResponse;
import app.lightmove.api.position.dto.OrgNodeDto;
import app.lightmove.api.position.dto.MandateContextDto;
import app.lightmove.api.position.dto.PositionDetailsDto;
import app.lightmove.api.position.dto.PositionDocumentDto;
import app.lightmove.api.position.dto.PositionResponse;
import app.lightmove.api.position.dto.PublicationDto;
import app.lightmove.api.position.dto.ReportingStructureDto;
import app.lightmove.api.position.model.Position;
import app.lightmove.api.position.model.PositionDocumentSummary;
import app.lightmove.api.position.repository.PositionDocumentRepository;
import app.lightmove.api.core.security.model.User;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Builds the whole brief the screen reads, out of the mandate, the position row, the attached
 * document's metadata and the name of whoever published.
 *
 * <p>Its own class rather than a method on the service: assembling six step groups from four sources
 * is a job that changes whenever the screen does, which is a different reason to change from the
 * writes. The {@code AuthResponseAssembler} beside {@code AuthController} is the same split.
 */
@Component
@RequiredArgsConstructor
class PositionResponseAssembler {

    private final PositionDocumentRepository documents;
    private final UserRepository users;

    PositionResponse assemble(PositionBrief brief) {
        Position position = brief.position();
        return new PositionResponse(
                detailsOf(brief),
                contextOf(position),
                reportingOf(brief),
                compensationOf(position),
                assessmentOf(position),
                publicationOf(position),
                documentOf(position));
    }

    private PositionDetailsDto detailsOf(PositionBrief brief) {
        Position position = brief.position();
        return new PositionDetailsDto(
                brief.project().getPositionTitle(),
                position.getDepartment(),
                position.getLocation(),
                position.getEmploymentType(),
                position.getSeniority(),
                List.copyOf(position.getResponsibilities()),
                position.getNarrative());
    }

    private MandateContextDto contextOf(Position position) {
        return new MandateContextDto(
                position.getMandateReason(),
                position.getBusinessDriver(),
                Set.copyOf(position.getStrategicPriorities()),
                position.getHiringUrgency(),
                position.isConfidential(),
                position.getInternalContext());
    }

    private ReportingStructureDto reportingOf(PositionBrief brief) {
        Position position = brief.position();
        return new ReportingStructureDto(
                position.getOrgChart().stream()
                        .map(node -> new OrgNodeDto(node.getNodeId(), node.getParentNodeId(),
                                node.getTitle(), node.getName(), node.isMandateSeat(),
                                node.getCanvasX(), node.getCanvasY()))
                        .toList(),
                position.getTeamSize(),
                brief.project().getTargetDate(),
                position.getNoticeValue(),
                position.getNoticeUnit());
    }

    private CompensationDto compensationOf(Position position) {
        return new CompensationDto(
                position.getCurrency(),
                position.getSalaryMin(),
                position.getSalaryMax(),
                position.getBaseSalaryMode(),
                position.getBonusValue(),
                position.getBonusBasis(),
                position.getIncentiveType(),
                position.getIncentiveAmount(),
                position.getIncentiveVesting(),
                position.getBenefits().stream()
                        .map(benefit -> new BenefitDto(
                                benefit.getName(), benefit.getAmount(), benefit.getFrequency()))
                        .toList());
    }

    private AssessmentDto assessmentOf(Position position) {
        return new AssessmentDto(
                position.getCriteria().stream()
                        .map(criterion -> new CriterionResponse(
                                criterion.getText(), criterion.getMode(), criterion.isFromBrief()))
                        .toList(),
                panel(position, CompetencyPanel.TECHNICAL),
                panel(position, CompetencyPanel.BEHAVIOURAL));
    }

    private PublicationDto publicationOf(Position position) {
        return new PublicationDto(position.getPublishedAt(), publisherNameOf(position.getPublishedBy()));
    }

    private PositionDocumentDto documentOf(Position position) {
        return documents.findSummaryByPositionId(position.getId())
                .map(PositionResponseAssembler::toDocumentDto)
                .orElse(null);
    }

    /**
     * The publisher's name, resolved for display. Read from the user table rather than the audit
     * ledger: the ledger records the same act, but it is append-only with no read path, so nothing
     * could render "published by" from it.
     */
    private String publisherNameOf(UUID publishedBy) {
        return Optional.ofNullable(publishedBy)
                .flatMap(users::findById)
                .map(User::getFullName)
                .orElse(null);
    }

    private static PositionDocumentDto toDocumentDto(PositionDocumentSummary summary) {
        return new PositionDocumentDto(summary.getFileName(), summary.getContentType(),
                summary.getFileSize(), summary.getCreatedAt());
    }

    private static List<CompetencyDto> panel(Position position, CompetencyPanel panel) {
        return position.getCompetencies().stream()
                .filter(competency -> competency.getPanel() == panel)
                .map(competency -> new CompetencyDto(
                        competency.getName(), competency.getDescription(), competency.getWeight()))
                .toList();
    }
}
