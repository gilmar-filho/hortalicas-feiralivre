package br.ufla.feiralivre.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.containers.wait.strategy.WaitAllStrategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Sobe o Compose inteiro em um projeto próprio, com volumes descartáveis, e
 * fala só com o gateway — como o navegador.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class FluxoPontaAPontaTest {

    private static final String PROJETO = "feirae2e" + System.nanoTime();
    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Duration SUBIDA = Duration.ofMinutes(15);

    private static final ComposeContainer AMBIENTE = new ComposeContainer(PROJETO, new File("../compose.yaml"))
        .withLocalCompose(true)
        .withBuild(true)
        .withRemoveVolumes(true)
        .withExposedService("gateway", 80, new WaitAllStrategy()
            .withStrategy(Wait.forHttp("/api/produtos").forStatusCode(200))
            .withStrategy(Wait.forHttp("/api/retiradas/horarios?vendedorId=1").forStatusCode(200))
            .withStrategy(Wait.forHttp("/api/usuario/atual?usuarioId=1").forStatusCode(200))
            .withStartupTimeout(SUBIDA));

    private static String base;

    @BeforeAll
    static void subir() {
        AMBIENTE.start();
        base = "http://" + AMBIENTE.getServiceHost("gateway", 80) + ":" + AMBIENTE.getServicePort("gateway", 80);
    }

    @AfterAll
    static void derrubar() {
        AMBIENTE.stop();
    }

    @Test
    @Order(1)
    public void pedidoFelizDeveReservarEstoqueOcuparVagaGerarFaturaERastrearLote() throws Exception {
        Cenario c = cenario(10);

        Resposta pedido = pedir(c, c.produto(), 3, c.comprador());

        assertEquals(200, pedido.status());
        long id = pedido.corpo().get("id").asLong();
        assertEquals(7, estoque(c, c.produto()));
        assertEquals(0, vagas(c));
        assertTrue(fatura(c, id) != null, "A fatura do pedido aparece para o vendedor");
        JsonNode reservas = enviar("GET", "/api/produtos/reservas?pedidoIds=" + id, null).corpo();
        assertEquals(1, reservas.size());
        assertEquals("ATIVA", reservas.get(0).get("status").asText());
    }

    @Test
    @Order(2)
    public void cancelamentoDeveDevolverEstoqueEVagaEManterOLoteNaFatura() throws Exception {
        Cenario c = cenario(10);
        long id = pedir(c, c.produto(), 4, c.comprador()).corpo().get("id").asLong();

        Resposta cancelado = cancelar(c, id);

        assertEquals(200, cancelado.status());
        assertEquals(10, estoque(c, c.produto()));
        assertEquals(1, vagas(c));
        JsonNode reservas = enviar("GET", "/api/produtos/reservas?pedidoIds=" + id, null).corpo();
        assertEquals("DEVOLVIDA", reservas.get(0).get("status").asText(), "A fatura cancelada continua rastreando o lote");
    }

    @Test
    @Order(3)
    public void janelaCheiaDeveRecusarOutroCompradorSemTocarNoEstoque() throws Exception {
        Cenario c = cenario(50);
        assertEquals(200, pedir(c, c.produto(), 3, c.comprador()).status());

        Resposta recusado = pedir(c, c.produto(), 4, c.outroComprador());

        assertEquals(409, recusado.status());
        assertEquals("Janela de retirada cheia", recusado.corpo().get("message").asText());
        assertEquals(47, estoque(c, c.produto()), "A pré-checagem recusa antes de reservar estoque");
    }

    @Test
    @Order(4)
    public void estoqueInsuficienteDeveRecusarSemOcuparVaga() throws Exception {
        Cenario c = cenario(2);

        Resposta recusado = pedir(c, c.produto(), 5, c.comprador());

        assertEquals(409, recusado.status());
        assertEquals("Estoque insuficiente", recusado.corpo().get("message").asText());
        assertEquals(1, vagas(c));
    }

    @Test
    @Order(5)
    public void rotasInternasNaoPassamPeloGateway() throws Exception {
        assertEquals(404, enviar("GET", "/interno/produtos/1", null).status());
        assertEquals(404, enviar("GET", "/interno/atendimentos/disponibilidade?horarioId=1&data=2030-01-01&compradorId=1&vendedorId=1", null).status());
    }

    @Test
    @Order(6)
    public void mesmoCompradorEntraNaJanelaCheiaEAVagaSoVoltaComOsDoisCancelados() throws Exception {
        Cenario c = cenario(20);
        long tomate = novoProduto(c.vendedor(), c.data(), 20);
        long primeiro = pedir(c, c.produto(), 1, c.comprador()).corpo().get("id").asLong();

        Resposta segundo = pedir(c, tomate, 1, c.comprador());

        assertEquals(200, segundo.status(), "Quem já ocupa a vaga pode pedir outro produto para a mesma ocorrência");
        cancelar(c, primeiro);
        assertEquals(409, pedir(c, c.produto(), 1, c.outroComprador()).status());
        cancelar(c, segundo.corpo().get("id").asLong());
        assertEquals(200, pedir(c, c.produto(), 1, c.outroComprador()).status());
    }

    @Test
    @Order(7)
    public void confirmacaoDaRetiradaDeveBaixarOEstoquePeloEvento() throws Exception {
        Cenario c = cenario(30);
        long pedido = pedir(c, c.produto(), 3, c.comprador()).corpo().get("id").asLong();
        assertEquals("ATIVA", statusDaReservaDeEstoque(pedido));
        assertEquals("ATIVA", statusDaRetirada(pedido));

        Resposta confirmada = enviar("POST", "/api/retiradas/reservas/" + pedido + "/confirmacao", null);

        assertEquals(200, confirmada.status());
        assertEquals("RETIRADA", confirmada.corpo().get("status").asText());
        aguardar(30, () -> "VENDIDA".equals(statusDaReservaDeEstoque(pedido)),
            "a Produção consumir o evento RetiradaConfirmada no broker");

        Resposta repetida = enviar("POST", "/api/retiradas/reservas/" + pedido + "/confirmacao", null);
        assertEquals(200, repetida.status(), "O produtor reenviou após perda de sinal");
        Thread.sleep(2000);
        assertEquals("VENDIDA", statusDaReservaDeEstoque(pedido), "A confirmação repetida não gera efeito novo");
        assertEquals(27, estoque(c, c.produto()), "O produto retirado não volta para o disponível");
    }

    @Test
    @Order(99)
    public void servicoParadoDerrubaSoAsPropriasRotas() throws Exception {
        pararServico("entrega");

        assertEquals(200, enviar("GET", "/api/produtos", null).status(), "Produção segue no ar");
        assertEquals(200, enviar("GET", "/api/usuario/atual?usuarioId=1", null).status(), "O núcleo segue no ar");
        assertNotEquals(200, enviar("GET", "/api/retiradas/horarios?vendedorId=1", null).status(), "Só a rota de Entrega cai");
    }

    private record Resposta(int status, JsonNode corpo) { }

    private record Cenario(long vendedor, long comprador, long outroComprador, long produto, long horario, LocalDate data) { }

    private Cenario cenario(int estoque) throws Exception {
        long vendedor = usuario("vendedor");
        LocalDate data = LocalDate.now(FUSO).plusDays(2);
        long produto = novoProduto(vendedor, data, estoque);
        long local = enviar("POST", "/api/retiradas/locais", Map.of(
            "vendedorId", vendedor, "nome", "Ponto e2e", "endereco", "Rua E2E, 1")).corpo().get("id").asLong();
        long horario = enviar("POST", "/api/retiradas/horarios", Map.of(
            "localId", local, "diaSemana", data.getDayOfWeek().getValue() % 7,
            "horaInicio", "00:00", "horaFim", "23:59", "capacidadeAtendimento", 1)).corpo().get("id").asLong();
        return new Cenario(vendedor, usuario("comprador"), usuario("outro"), produto, horario, data);
    }

    private long novoProduto(long vendedor, LocalDate data, int estoque) throws Exception {
        return enviar("POST", "/api/produtos", Map.of(
            "usuarioId", vendedor, "nome", "Produto e2e " + System.nanoTime(), "preco", 4.5, "ativo", 1,
            "dataCadastro", LocalDate.now(FUSO).toString(), "dataValidade", data.plusDays(10).toString(),
            "quantidadeEstoque", estoque)).corpo().get("id").asLong();
    }

    private long usuario(String prefixo) throws Exception {
        return enviar("POST", "/api/usuario", Map.of(
            "nome", prefixo, "email", prefixo + "-" + System.nanoTime() + "@e2e.local", "senha", "123456")).corpo().get("id").asLong();
    }

    private Resposta pedir(Cenario c, long produto, int quantidade, long comprador) throws Exception {
        return enviar("POST", "/api/pedidos", Map.of(
            "produtoId", produto, "quantidade", quantidade, "horarioRetiradaId", c.horario(),
            "dataRetirada", c.data().toString(), "compradorId", comprador));
    }

    private Resposta cancelar(Cenario c, long pedido) throws Exception {
        return enviar("PATCH", "/api/pedidos/" + pedido + "/status", Map.of("status", "CANCELADO", "vendedorId", c.vendedor()));
    }

    private int estoque(Cenario c, long produto) throws Exception {
        for (JsonNode item : enviar("GET", "/api/produtos?usuarioId=" + c.vendedor(), null).corpo())
            if (item.get("id").asLong() == produto) return item.get("estoque").asInt();
        throw new AssertionError("Produto " + produto + " fora do catálogo");
    }

    private int vagas(Cenario c) throws Exception {
        for (JsonNode o : enviar("GET", "/api/retiradas/ocorrencias?vendedorId=" + c.vendedor(), null).corpo())
            if (o.get("horarioId").asLong() == c.horario() && o.get("data").asText().equals(c.data().toString())) return o.get("vagas").asInt();
        throw new AssertionError("Ocorrência da janela " + c.horario() + " não listada");
    }

    private JsonNode fatura(Cenario c, long pedido) throws Exception {
        for (JsonNode f : enviar("GET", "/api/faturamento?visao=vendedor&usuarioId=" + c.vendedor(), null).corpo())
            if (f.get("pedido_id").asLong() == pedido) return f;
        return null;
    }

    private static String statusDaReservaDeEstoque(long pedido) throws Exception {
        JsonNode reservas = enviar("GET", "/api/produtos/reservas?pedidoIds=" + pedido, null).corpo();
        return reservas.isEmpty() ? "" : reservas.get(0).get("status").asText();
    }

    private static String statusDaRetirada(long pedido) throws Exception {
        JsonNode reservas = enviar("GET", "/api/retiradas/reservas?pedidoIds=" + pedido, null).corpo();
        return reservas.isEmpty() ? "" : reservas.get(0).get("status").asText();
    }

    @FunctionalInterface
    private interface Condicao {
        boolean ok() throws Exception;
    }

    private static void aguardar(int segundos, Condicao condicao, String descricao) throws Exception {
        long limite = System.nanoTime() + segundos * 1_000_000_000L;
        while (System.nanoTime() < limite) {
            if (condicao.ok()) return;
            Thread.sleep(250);
        }
        throw new AssertionError("Esperava: " + descricao);
    }

    private static Resposta enviar(String metodo, String caminho, Object corpo) throws Exception {
        HttpRequest requisicao = HttpRequest.newBuilder(URI.create(base + caminho))
            .header("Content-Type", "application/json")
            .method(metodo, corpo == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(corpo)))
            .build();
        HttpResponse<String> resposta = HTTP.send(requisicao, HttpResponse.BodyHandlers.ofString());
        boolean ehJson = resposta.headers().firstValue("Content-Type").orElse("").contains("json");
        return new Resposta(resposta.statusCode(), ehJson ? JSON.readTree(resposta.body()) : JSON.missingNode());
    }

    /** Filtra pelo projeto deste teste: nunca toca o Compose de desenvolvimento. */
    private static void pararServico(String servico) {
        var docker = DockerClientFactory.instance().client();
        docker.listContainersCmd().withLabelFilter(Map.of("com.docker.compose.service", servico)).exec().stream()
            .filter(container -> container.getLabels().getOrDefault("com.docker.compose.project", "").startsWith(PROJETO))
            .forEach(container -> docker.stopContainerCmd(container.getId()).exec());
    }
}
