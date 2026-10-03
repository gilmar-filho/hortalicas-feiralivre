package br.ufla.feiralivre.contrato;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.model.Response;
import com.atlassian.oai.validator.model.SimpleRequest;
import com.atlassian.oai.validator.model.SimpleResponse;
import com.atlassian.oai.validator.report.SimpleValidationReportFormat;
import com.atlassian.oai.validator.report.ValidationReport;

/**
 * Toda chamada HTTP de teste passa pelo contrato: requisição e resposta que
 * fogem do YAML falham o teste, e o contrato não se afasta do código.
 */
public class ValidacaoDeContrato implements ClientHttpRequestInterceptor {

    private final OpenApiInteractionValidator validador;

    private ValidacaoDeContrato(String caminhoDoContrato) {
        try {
            validador = OpenApiInteractionValidator
                .createForInlineApiSpecification(Files.readString(Path.of(caminhoDoContrato)))
                .build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void instalar(TestRestTemplate restTemplate, String caminhoDoContrato) {
        RestTemplate template = restTemplate.getRestTemplate();
        if (template.getInterceptors().stream().anyMatch(ValidacaoDeContrato.class::isInstance)) return;
        template.setRequestFactory(new BufferingClientHttpRequestFactory(template.getRequestFactory()));
        template.getInterceptors().add(new ValidacaoDeContrato(caminhoDoContrato));
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        ClientHttpResponse response = execution.execute(request, body);
        byte[] corpo = response.getBody().readAllBytes();
        ValidationReport relatorio = validador.validate(requisicao(request, body), resposta(response, corpo));
        if (relatorio.hasErrors())
            throw new AssertionError("Violação do contrato em " + request.getMethod() + " " + request.getURI() + "\n"
                + SimpleValidationReportFormat.getInstance().apply(relatorio));
        return response;
    }

    private static Request requisicao(HttpRequest request, byte[] body) {
        URI uri = request.getURI();
        SimpleRequest.Builder builder = new SimpleRequest.Builder(request.getMethod().name(), uri.getPath());
        UriComponentsBuilder.fromUri(uri).build(true).getQueryParams()
            .forEach((nome, valores) -> builder.withQueryParam(nome, valores.stream()
                .map(valor -> java.net.URLDecoder.decode(valor, UTF_8)).toList()));
        request.getHeaders().forEach(builder::withHeader);
        if (body.length > 0) builder.withBody(new String(body, UTF_8));
        return builder.build();
    }

    private static Response resposta(ClientHttpResponse response, byte[] corpo) throws IOException {
        SimpleResponse.Builder builder = SimpleResponse.Builder.status(response.getStatusCode().value());
        response.getHeaders().forEach(builder::withHeader);
        if (corpo.length > 0) builder.withBody(new String(corpo, UTF_8));
        return builder.build();
    }
}
