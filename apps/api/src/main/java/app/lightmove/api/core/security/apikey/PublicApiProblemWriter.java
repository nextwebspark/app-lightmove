package app.lightmove.api.core.security.apikey;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.service.Problems;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** The public chain refuses before the DispatcherServlet, where no advice runs; this keeps those refusals RFC 9457. */
@Component
@RequiredArgsConstructor
public class PublicApiProblemWriter {

    private final ObjectMapper json;

    public void write(HttpServletRequest request, HttpServletResponse response, ErrorCode code) throws IOException {
        ProblemDetail problem = Problems.of(code);
        problem.setInstance(URI.create(request.getRequestURI()));
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write(json.writeValueAsString(problem));
    }
}
