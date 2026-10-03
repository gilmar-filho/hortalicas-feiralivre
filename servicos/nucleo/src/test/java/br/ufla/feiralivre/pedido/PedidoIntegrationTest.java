package br.ufla.feiralivre.pedido;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import br.ufla.feiralivre.ServicosSimulados;
import br.ufla.feiralivre.TestData;

public class PedidoIntegrationTest extends ServicosSimulados {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate db;

    @Test
    public void deveCriarPedidoComSnapshotDoProdutoReservasEFatura() {
        long vendedor = pessoa("vendedor.pedido");
        long comprador = pessoa("comprador.pedido");
        long produto = TestData.id();
        LocalDate data = TestData.hoje().plusDays(2);
        servicosAceitamPedido(produto, vendedor, "Alface (teste de pedido)", 4.50);

        ResponseEntity<Map> resposta = postPedido(produto, 2, 77L, data.toString(), comprador);

        assertEquals(HttpStatus.OK, resposta.getStatusCode());
        long pedido = ((Number) resposta.getBody().get("id")).longValue();
        assertEquals("PENDENTE", db.queryForObject("SELECT status FROM pedido WHERE id = ?", String.class, pedido));
        Map<String, Object> item = db.queryForMap("SELECT produto_nome, vendedor_id, quantidade, preco_unitario FROM item_pedido WHERE pedido_id = ?", pedido);
        assertEquals("Alface (teste de pedido)", item.get("produto_nome"));
        assertEquals(vendedor, ((Number) item.get("vendedor_id")).longValue());
        assertEquals(2, ((Number) item.get("quantidade")).intValue());
        Map<String, Object> fatura = db.queryForMap("SELECT valor, status FROM fatura WHERE pedido_id = ?", pedido);
        assertEquals(9.0, ((Number) fatura.get("valor")).doubleValue(), 0.001);
        assertEquals("PENDENTE", fatura.get("status"));
        PRODUCAO.verify(postRequestedFor(urlEqualTo("/interno/reservas-estoque"))
            .withRequestBody(matchingJsonPath("$.pedidoId", equalTo(String.valueOf(pedido))))
            .withRequestBody(matchingJsonPath("$.quantidade", equalTo("2"))));
        ENTREGA.verify(getRequestedFor(urlPathEqualTo("/interno/atendimentos/disponibilidade"))
            .withQueryParam("vendedorId", equalTo(String.valueOf(vendedor)))
            .withQueryParam("compradorId", equalTo(String.valueOf(comprador))));
        ENTREGA.verify(postRequestedFor(urlEqualTo("/interno/atendimentos"))
            .withRequestBody(matchingJsonPath("$.pedidoId", equalTo(String.valueOf(pedido)))));
    }

    @Test
    public void deveRecusarPedidoQuandoEstoqueEhInsuficiente() {
        long comprador = pessoa("comprador.escasso");
        long produto = TestData.id();
        produtoExiste(produto, pessoa("vendedor.escasso"), "Rúcula (estoque curto)", 3.00);
        janelaDisponivel();
        estoqueInsuficiente();

        ResponseEntity<Map> resposta = postPedido(produto, 10, 77L, TestData.hoje().plusDays(2).toString(), comprador);

        assertEquals(HttpStatus.CONFLICT, resposta.getStatusCode());
        assertEquals("Estoque insuficiente", resposta.getBody().get("message"));
        assertEquals(0, pedidosDo(comprador));
        ENTREGA.verify(0, postRequestedFor(urlEqualTo("/interno/atendimentos")));
    }

    @Test
    public void deveRecusarPedidoComJanelaJaEncerradaMantendoO400() {
        long comprador = pessoa("comprador.passado");
        long produto = TestData.id();
        produtoExiste(produto, pessoa("vendedor.passado"), "Cenoura (data passada)", 6.20);
        janelaRecusada(400, "Esta janela de retirada já encerrou");

        ResponseEntity<Map> resposta = postPedido(produto, 1, 77L, TestData.hoje().minusDays(1).toString(), comprador);

        assertEquals(HttpStatus.BAD_REQUEST, resposta.getStatusCode());
        assertEquals("Esta janela de retirada já encerrou", resposta.getBody().get("message"));
        PRODUCAO.verify(0, postRequestedFor(urlEqualTo("/interno/reservas-estoque")));
    }

    @Test
    public void deveRecusarPedidoDeProdutoInativo() {
        long produto = TestData.id();
        produtoIndisponivel(produto);

        ResponseEntity<Map> resposta = postPedido(produto, 1, 77L, TestData.hoje().plusDays(2).toString(), pessoa("comprador.inativo"));

        assertEquals(HttpStatus.NOT_FOUND, resposta.getStatusCode(), "Produto desativado não está à venda e deve responder 404");
        assertEquals("Produto não está disponível", resposta.getBody().get("message"));
    }

    @Test
    public void pedidoSemJanelaOuComDataMalformadaDeveResponder400SemChamarServicos() {
        long comprador = pessoa("comprador.entrada");
        long produto = TestData.id();

        ResponseEntity<Map> semJanela = postPedido(produto, 1, null, TestData.hoje().plusDays(2).toString(), comprador);
        ResponseEntity<Map> dataInvalida = postPedido(produto, 1, 77L, "2026-13-45", comprador);
        ResponseEntity<Map> semData = postPedido(produto, 1, 77L, null, comprador);

        for (ResponseEntity<Map> resposta : List.of(semJanela, dataInvalida, semData)) {
            assertEquals(HttpStatus.BAD_REQUEST, resposta.getStatusCode());
            assertEquals("Informe a janela e a data de retirada", resposta.getBody().get("message"));
        }
        assertEquals(0, PRODUCAO.getAllServeEvents().size());
        assertEquals(0, ENTREGA.getAllServeEvents().size());
    }

    @Test
    public void pedidoComJanelaDeOutroProdutorDeveResponder404SemReservarEstoque() {
        long comprador = pessoa("comprador.dono");
        long produto = TestData.id();
        produtoExiste(produto, pessoa("vendedor.dono"), "Couve (janela alheia)", 4.00);
        janelaRecusada(404, "Janela de retirada não encontrada");

        ResponseEntity<Map> resposta = postPedido(produto, 2, 77L, TestData.hoje().plusDays(2).toString(), comprador);

        assertEquals(HttpStatus.NOT_FOUND, resposta.getStatusCode());
        assertEquals("Janela de retirada não encontrada", resposta.getBody().get("message"));
        PRODUCAO.verify(0, postRequestedFor(urlEqualTo("/interno/reservas-estoque")));
        assertEquals(0, pedidosDo(comprador));
    }

    @Test
    public void recusaNoAtendimentoNaoGravaPedidoENaoReusaOId() {
        long vendedor = pessoa("vendedor.corrida");
        long comprador = pessoa("comprador.corrida");
        long produto = TestData.id();
        String data = TestData.hoje().plusDays(2).toString();
        produtoExiste(produto, vendedor, "Agrião (corrida)", 3.00);
        janelaDisponivel();
        estoqueReservado();
        atendimentoRecusado(409, "Janela de retirada cheia");

        ResponseEntity<Map> recusado = postPedido(produto, 1, 77L, data, comprador);
        long idPerdido = idEnviadoAProducao();
        atendimentoReservado();
        ResponseEntity<Map> seguinte = postPedido(produto, 1, 77L, data, comprador);

        assertEquals(HttpStatus.CONFLICT, recusado.getStatusCode());
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM pedido WHERE id = ?", Integer.class, idPerdido));
        assertEquals(HttpStatus.OK, seguinte.getStatusCode());
        assertNotEquals(idPerdido, ((Number) seguinte.getBody().get("id")).longValue(),
            "Um id que já foi a Produção nunca pode ser reusado: a reserva órfã passaria para o próximo pedido");
    }

    @Test
    public void criacoesConcorrentesRecebemIdsDistintos() throws Exception {
        long vendedor = pessoa("vendedor.concorrente");
        long produto = TestData.id();
        String data = TestData.hoje().plusDays(2).toString();
        servicosAceitamPedido(produto, vendedor, "Tomate (concorrente)", 7.00);
        List<Long> compradores = new ArrayList<>();
        for (int i = 0; i < 8; i++) compradores.add(pessoa("comprador.concorrente"));
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<ResponseEntity<Map>>> futuros = new ArrayList<>();
        for (long comprador : compradores)
            futuros.add(pool.submit(() -> { largada.await(); return postPedido(produto, 1, 77L, data, comprador); }));

        largada.countDown();
        Set<Long> ids = new HashSet<>();
        try {
            for (Future<ResponseEntity<Map>> futuro : futuros) {
                ResponseEntity<Map> resposta = futuro.get(30, TimeUnit.SECONDS);
                assertEquals(HttpStatus.OK, resposta.getStatusCode());
                ids.add(((Number) resposta.getBody().get("id")).longValue());
            }
        } finally {
            pool.shutdownNow();
        }

        assertEquals(8, ids.size(), "Pedidos simultâneos não podem dividir o mesmo id");
    }

    @Test
    public void entregaForaDoArNaCriacaoNaoReservaEstoque() {
        long comprador = pessoa("comprador.semEntrega");
        long produto = TestData.id();
        produtoExiste(produto, pessoa("vendedor.semEntrega"), "Salsa (sem entrega)", 2.00);
        entregaForaDoArNaDisponibilidade();

        ResponseEntity<Map> resposta = postPedido(produto, 1, 77L, TestData.hoje().plusDays(2).toString(), comprador);

        assertTrue(resposta.getStatusCode().isError());
        PRODUCAO.verify(0, postRequestedFor(urlEqualTo("/interno/reservas-estoque")));
        assertEquals(0, pedidosDo(comprador));
    }

    @Test
    public void listagensDevemUsarONomeEOVendedorGravadosNoItem() {
        long vendedor = pessoa("vendedor.listagem");
        long comprador = pessoa("comprador.listagem");
        long produto = TestData.id();
        servicosAceitamPedido(produto, vendedor, "Quiabo (listagem)", 5.00);
        long pedido = ((Number) postPedido(produto, 1, 77L, TestData.hoje().plusDays(2).toString(), comprador).getBody().get("id")).longValue();

        List<Map> meus = restTemplate.getForObject("/api/pedidos?compradorId=" + comprador, List.class);
        List<Map> recebidos = restTemplate.getForObject("/api/pedidos/recebidos?vendedorId=" + vendedor, List.class);

        assertEquals("Quiabo (listagem)", meus.get(0).get("produtos"));
        assertTrue(recebidos.stream().anyMatch(p -> ((Number) p.get("id")).longValue() == pedido));
    }

    private long idEnviadoAProducao() {
        String corpo = PRODUCAO.findAll(postRequestedFor(urlEqualTo("/interno/reservas-estoque"))).get(0).getBodyAsString();
        return com.jayway.jsonpath.JsonPath.<Number>read(corpo, "$.pedidoId").longValue();
    }

    private long pessoa(String prefixo) {
        return TestData.usuario(db, prefixo, TestData.email(prefixo), "123456");
    }

    private int pedidosDo(long comprador) {
        return db.queryForObject("SELECT COUNT(*) FROM pedido WHERE comprador_id = ?", Integer.class, comprador);
    }

    private ResponseEntity<Map> postPedido(long produto, int quantidade, Long horario, String data, long comprador) {
        Map<String, Object> request = new HashMap<>();
        request.put("produtoId", produto);
        request.put("quantidade", quantidade);
        request.put("horarioRetiradaId", horario);
        request.put("dataRetirada", data);
        request.put("compradorId", comprador);
        return restTemplate.postForEntity("/api/pedidos", request, Map.class);
    }
}
