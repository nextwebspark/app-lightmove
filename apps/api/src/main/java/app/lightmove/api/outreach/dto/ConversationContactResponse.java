package app.lightmove.api.outreach.dto;

import app.lightmove.api.candidate.dto.CandidateEmailDto;
import app.lightmove.api.candidate.dto.CandidatePhoneDto;
import java.util.List;

/** What the conversation's details pane and composer read about reaching one executive: their ledger and where they are. */
public record ConversationContactResponse(List<CandidateEmailDto> emails, List<CandidatePhoneDto> phones,
                                          String locationCity, String locationCountry, boolean doNotContact) {}
