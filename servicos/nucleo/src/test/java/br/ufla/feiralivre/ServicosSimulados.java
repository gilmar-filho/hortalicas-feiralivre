package br.ufla.feiralivre;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.noContent;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.model.SimpleRequest;
import com.atlassian.oai.validator.model.SimpleResponse;
import com.atlassian.oai.validator.report.SimpleValidationReportFormat;
import com.atlassian.oai.validator.report.ValidationReport;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.http.LoggedResponse;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;

/**
 * Produção e Entrega simuladas pelo WireMock. Depois de cada teste, toda
 * interação gravada — requisição do núcleo e resposta do stub — é validada
 * contra o contrato do serviço: stub ou cliente fora do contrato falha o teste.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class ServicosSimulados {

    protected static final WireMockServer PRODUCAO = new WireMockServer(options().dynamicPort());
    protected static final WireMockServer ENTREGA = new WireMockServer(options().dynamicPort());
    private static final OpenApiInteractionValidator CONTRATO_PRODUCAO = contrato("../../contracts/producao.yaml");
    private static final OpenApiInteractionValidator CONTRATO_ENTREGA = contrato("../../contracts/entrega.yaml");
    private static final ObjectMapper JSON = new ObjectMapper();

    static {
        PRODUCAO.start();
        ENTREGA.start();
    }

    @DynamicPropertySource
    static void enderecos(DynamicPropertyRegistry registro) {
        registro.add("servicos.producao.url", PRODUCAO::baseUrl);
        registro.add("servicos.entrega.url", ENTREGA::baseUrl);
    }

    @BeforeEach
    public void limparServicos() {
        PRODUCAO.resetAll();
        ENTREGA.resetAll();
    }

    @AfterEach
    public void validarContratos() {
        validar(PRODUCAO, CONTRATO_PRODUCAO);
        validar(ENTREGA, CONTRATO_ENTREGA);
    }

    protected void produtoExiste(long produtoId, long vendedorId, String nome, double preco) {
        PRODUCAO.stubFor(get(urlEqualTo("/interno/produtos/" + produtoId)).willReturn(okJson(json(Map.of(
            "id", produtoId, "vendedorId", vendedorId, "nome", nome, "preco", preco)))));
    }

    protected void produtoIndisponivel(long produtoId) {
        PRODUCAO.stubFor(get(urlEqualTo("/interno/produtos/" + produtoId)).willReturn(erro(404, "Produto não está disponível")));
    }

    protected void janelaDisponivel() {
        ENTREGA.stubFor(get(urlPathEqualTo("/interno/atendimentos/disponibilidade")).willReturn(noContent()));
    }

    protected void janelaRecusada(int status, String mensagem) {
        ENTREGA.stubFor(get(urlPathEqualTo("/interno/atendimentos/disponibilidade")).willReturn(erro(status, mensagem)));
    }

    protected void estoqueReservado() {
        PRODUCAO.stubFor(post(urlEqualTo("/interno/reservas-estoque")).willReturn(aResponse().withStatus(201)
            .withHeader("Content-Type", "application/json")
            .withBody(json(Map.of("pedidoId", 1, "lotes", List.of(Map.of(
                "loteId", 7, "quantidade", 1, "origem", "Sítio simulado", "dataValidade", "2030-01-01")))))));
    }

    protected void estoqueInsuficiente() {
        PRODUCAO.stubFor(post(urlEqualTo("/interno/reservas-estoque")).willReturn(erro(409, "Estoque insuficiente")));
    }

    protected void atendimentoReservado() {
        ENTREGA.stubFor(post(urlEqualTo("/interno/atendimentos")).willReturn(aResponse().withStatus(201)));
    }

    protected void atendimentoRecusado(int status, String mensagem) {
        ENTREGA.stubFor(post(urlEqualTo("/interno/atendimentos")).willReturn(erro(status, mensagem)));
    }

    protected void devolucoesAceitas() {
        PRODUCAO.stubFor(delete(urlPathMatching("/interno/reservas-estoque/\\d+")).willReturn(noContent()));
        ENTREGA.stubFor(delete(urlPathMatching("/interno/atendimentos/\\d+")).willReturn(noContent()));
    }

    protected void producaoForaDoArNaDevolucao() {
        PRODUCAO.stubFor(delete(urlPathMatching("/interno/reservas-estoque/\\d+"))
            .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
    }

    protected void entregaForaDoArNaDisponibilidade() {
        ENTREGA.stubFor(get(urlPathEqualTo("/interno/atendimentos/disponibilidade"))
            .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
    }

    /** O caminho feliz inteiro: produto existe, há vaga, estoque e atendimento reservados. */
    protected void servicosAceitamPedido(long produtoId, long vendedorId, String nome, double preco) {
        produtoExiste(produtoId, vendedorId, nome, preco);
        janelaDisponivel();
        estoqueReservado();
        atendimentoReservado();
        devolucoesAceitas();
    }

    private static ResponseDefinitionBuilder erro(int status, String mensagem) {
        return aResponse().withStatus(status).withHeader("Content-Type", "application/json").withBody(json(Map.of(
            "timestamp", "2026-01-01T00:00:00.000+00:00", "status", status,
            "error", HttpStatus.valueOf(status).getReasonPhrase(), "message", mensagem, "path", "/interno")));
    }

    private static String json(Object valor) {
        try {
            return JSON.writeValueAsString(valor);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private static OpenApiInteractionValidator contrato(String caminho) {
        try {
            return OpenApiInteractionValidator.createForInlineApiSpecification(Files.readString(Path.of(caminho))).build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void validar(WireMockServer servico, OpenApiInteractionValidator contrato) {
        for (ServeEvent evento : servico.getAllServeEvents()) {
            if (evento.getResponseDefinition().getFault() != null) continue;
            LoggedRequest requisicao = evento.getRequest();
            SimpleRequest.Builder pedido = new SimpleRequest.Builder(requisicao.getMethod().getName(), URI.create(requisicao.getUrl()).getPath());
            requisicao.getQueryParams().forEach((nome, parametro) -> pedido.withQueryParam(nome, parametro.values()));
            requisicao.getHeaders().all().forEach(cabecalho -> pedido.withHeader(cabecalho.key(), cabecalho.values()));
            if (!requisicao.getBodyAsString().isEmpty()) pedido.withBody(requisicao.getBodyAsString());
            LoggedResponse resposta = evento.getResponse();
            SimpleResponse.Builder retorno = SimpleResponse.Builder.status(resposta.getStatus());
            if (resposta.getHeaders() != null) resposta.getHeaders().all().forEach(cabecalho -> retorno.withHeader(cabecalho.key(), cabecalho.values()));
            if (resposta.getBodyAsString() != null && !resposta.getBodyAsString().isEmpty()) retorno.withBody(resposta.getBodyAsString());
            ValidationReport relatorio = contrato.validate(pedido.build(), retorno.build());
            if (relatorio.hasErrors())
                throw new AssertionError("Interação fora do contrato: " + requisicao.getMethod() + " " + requisicao.getUrl() + "\n"
                    + SimpleValidationReportFormat.getInstance().apply(relatorio));
        }
    }
}
