package com.appsmith.server.solutions.ce;

import com.appsmith.external.dtos.ExecuteActionDTO;
import com.appsmith.external.models.ActionDTO;
import com.appsmith.external.models.ActionExecutionResult;
import org.springframework.http.HttpHeaders;
import org.springframework.http.codec.multipart.Part;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

public interface ActionExecutionSolutionCE {
    Mono<ActionExecutionResult> executeAction(
            Flux<Part> partFlux,
            String branchName,
            String environmentId,
            Boolean operateWithoutPermission,
            HttpHeaders httpHeaders);

    Mono<ActionExecutionResult> executeAction(
            ExecuteActionDTO executeActionDTO,
            String environmentId,
            Boolean operateWithoutPermission,
            HttpHeaders httpHeaders);

    Mono<ActionDTO> getValidActionForExecution(ExecuteActionDTO executeActionDTO, Boolean operateWithoutPermission);

    <T> T variableSubstitution(T configuration, Map<String, String> replaceParamsMap);
}
