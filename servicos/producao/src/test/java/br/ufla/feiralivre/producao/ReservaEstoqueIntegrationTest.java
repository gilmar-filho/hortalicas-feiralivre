package br.ufla.feiralivre.producao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import br.ufla.feiralivre.TestData;
import br.ufla.feiralivre.contrato.ValidacaoDeContrato;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ReservaEstoqueIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate db;

    @BeforeEach
    public void validarContrato() {
        ValidacaoDeContrato.instalar(restTemplate, "../../contracts/producao.yaml");
    }

    @Test
    public void reservaDeveDevolverOsLotesAlocadosNaOrdemFefo() {
        long produto = TestData.produto(db, TestData.id(), "Couve (lotes alocados)", 4.00);
        long longe = TestData.lote(db, produto, 12, 10, "Sítio Longe");
        long perto = TestData.lote(db, produto, 3, 4, "Sítio Perto");
        long pedido = TestData.id();

        ResponseEntity<Map> resposta = reservar(pedido, produto, 6, TestData.hoje().plusDays(1));

        assertEquals(HttpStatus.CREATED, resposta.getStatusCode());
        assertEquals(pedido, ((Number) resposta.getBody().get("pedidoId")).longValue());
        List<Map> lotes = (List<Map>) resposta.getBody().get("lotes");
        assertEquals(2, lotes.size());
        assertEquals(perto, ((Number) lotes.get(0).get("loteId")).longValue());
        assertEquals(4, ((Number) lotes.get(0).get("quantidade")).intValue());
        assertEquals("Sítio Perto", lotes.get(0).get("origem"));
        assertEquals(validade(perto), lotes.get(0).get("dataValidade"));
        assertEquals(longe, ((Number) lotes.get(1).get("loteId")).longValue());
        assertEquals(2, ((Number) lotes.get(1).get("quantidade")).intValue());
    }

    @Test
    public void estoqueInsuficienteDeveResponder409SemGravarNada() {
        long produto = TestData.produto(db, TestData.id(), "Rúcula (estoque curto)", 3.00);
        long loteA = TestData.lote(db, produto, 5, 3);
        long loteB = TestData.lote(db, produto, 9, 2);
        long pedido = TestData.id();

        ResponseEntity<Map> resposta = reservar(pedido, produto, 10, TestData.hoje().plusDays(1));

        assertEquals(HttpStatus.CONFLICT, resposta.getStatusCode());
        assertEquals("Estoque insuficiente", resposta.getBody().get("message"));
        assertEquals(3, disponivel(loteA));
        assertEquals(2, disponivel(loteB));
        assertEquals(0, reservasDoPedido(pedido));
    }

    @Test
    public void produtoInativoDeveResponder404NaReservaENoResumo() {
        long produto = TestData.produto(db, TestData.id(), "Beterraba (inativa)", 5.00, false);
        TestData.lote(db, produto, 7, 40);

        ResponseEntity<Map> reserva = reservar(TestData.id(), produto, 1, TestData.hoje().plusDays(1));
        ResponseEntity<Map> resumo = restTemplate.getForEntity("/interno/produtos/" + produto, Map.class);

        assertEquals(HttpStatus.NOT_FOUND, reserva.getStatusCode());
        assertEquals("Produto não está disponível", reserva.getBody().get("message"));
        assertEquals(HttpStatus.NOT_FOUND, resumo.getStatusCode());
    }

    @Test
    public void resumoDoProdutoAtivoDeveTrazerVendedorNomeEPreco() {
        long vendedor = TestData.id();
        long produto = TestData.produto(db, vendedor, "Alface (resumo)", 4.50);

        ResponseEntity<Map> resumo = restTemplate.getForEntity("/interno/produtos/" + produto, Map.class);

        assertEquals(HttpStatus.OK, resumo.getStatusCode());
        assertEquals(produto, ((Number) resumo.getBody().get("id")).longValue());
        assertEquals(vendedor, ((Number) resumo.getBody().get("vendedorId")).longValue());
        assertEquals("Alface (resumo)", resumo.getBody().get("nome"));
        assertEquals(4.50, ((Number) resumo.getBody().get("preco")).doubleValue(), 0.001);
        assertEquals(4, resumo.getBody().size(), "O resumo interno não vaza colunas do banco");
    }

    @Test
    public void devolucaoDeveMoverReservadaParaDisponivelEMarcarDevolvida() {
        long produto = TestData.produto(db, TestData.id(), "Espinafre (devolução)", 4.00);
        long lote = TestData.lote(db, produto, 7, 30);
        long pedido = TestData.id();
        reservar(pedido, produto, 4, TestData.hoje().plusDays(1));

        restTemplate.delete("/interno/reservas-estoque/" + pedido);

        assertEquals(30, disponivel(lote));
        assertEquals(0, reservado(lote));
        assertEquals(0, db.queryForObject("SELECT quantidade_vendida FROM lote WHERE id = ?", Integer.class, lote));
        assertEquals("DEVOLVIDA", db.queryForObject(
            "SELECT status FROM reserva_estoque WHERE pedido_id = ?", String.class, pedido));
    }

    @Test
    public void devolucaoRepetidaNaoDeveAlterarOLote() {
        long produto = TestData.produto(db, TestData.id(), "Chicória (devolução dupla)", 4.00);
        long lote = TestData.lote(db, produto, 7, 30);
        long pedido = TestData.id();
        reservar(pedido, produto, 4, TestData.hoje().plusDays(1));

        restTemplate.delete("/interno/reservas-estoque/" + pedido);
        ResponseEntity<Void> segunda = restTemplate.exchange("/interno/reservas-estoque/" + pedido,
            org.springframework.http.HttpMethod.DELETE, null, Void.class);

        assertEquals(HttpStatus.NO_CONTENT, segunda.getStatusCode());
        assertEquals(30, disponivel(lote), "Devolver de novo não pode inflar o estoque");
        assertEquals(0, reservado(lote));
    }

    @Test
    public void devolucaoSemReservaDeveResponder204() {
        ResponseEntity<Void> resposta = restTemplate.exchange("/interno/reservas-estoque/" + TestData.id(),
            org.springframework.http.HttpMethod.DELETE, null, Void.class);

        assertEquals(HttpStatus.NO_CONTENT, resposta.getStatusCode());
    }

    @Test
    public void reservasDosPedidosIncluemDevolvidasComStatus() {
        long produto = TestData.produto(db, TestData.id(), "Tomate (rastreio da fatura)", 7.90);
        long lote = TestData.lote(db, produto, 7, 30, "Sítio Boa Terra");
        long ativo = TestData.id();
        long cancelado = TestData.id();
        reservar(ativo, produto, 2, TestData.hoje().plusDays(1));
        reservar(cancelado, produto, 3, TestData.hoje().plusDays(1));
        restTemplate.delete("/interno/reservas-estoque/" + cancelado);

        List<Map> reservas = restTemplate.getForObject(
            "/api/produtos/reservas?pedidoIds=" + ativo + "," + cancelado, List.class);

        assertEquals(2, reservas.size());
        Map doCancelado = reservas.stream()
            .filter(r -> ((Number) r.get("pedidoId")).longValue() == cancelado).findFirst().orElseThrow();
        assertEquals("DEVOLVIDA", doCancelado.get("status"), "A fatura do pedido cancelado continua mostrando o lote");
        assertEquals(lote, ((Number) doCancelado.get("loteId")).longValue());
        assertEquals("Sítio Boa Terra", doCancelado.get("origem"));
        assertEquals(validade(lote), doCancelado.get("dataValidade"));
        assertEquals(3, ((Number) doCancelado.get("quantidade")).intValue());
    }

    @Test
    public void reservasSemPedidosDevemResponderListaVazia() {
        ResponseEntity<List> resposta = restTemplate.getForEntity("/api/produtos/reservas", List.class);

        assertEquals(HttpStatus.OK, resposta.getStatusCode());
        assertTrue(resposta.getBody().isEmpty());
    }

    @Test
    public void disputaPeloUltimoEstoqueTerminaEmUm201EUm409() throws Exception {
        long produto = TestData.produto(db, TestData.id(), "Agrião (última unidade)", 3.50);
        long lote = TestData.lote(db, produto, 7, 5);
        LocalDate data = TestData.hoje().plusDays(1);

        List<Integer> status = emParalelo(
            () -> reservar(TestData.id(), produto, 5, data),
            () -> reservar(TestData.id(), produto, 5, data));

        assertEquals(List.of(201, 409), status);
        assertEquals(0, disponivel(lote));
        assertEquals(5, reservado(lote), "Nunca se reserva mais do que o lote tem");
    }

    private ResponseEntity<Map> reservar(long pedido, long produto, int quantidade, LocalDate data) {
        return restTemplate.postForEntity("/interno/reservas-estoque", Map.of(
            "pedidoId", pedido, "produtoId", produto, "quantidade", quantidade, "dataRetirada", data.toString()), Map.class);
    }

    private List<Integer> emParalelo(java.util.concurrent.Callable<ResponseEntity<Map>> a,
                                     java.util.concurrent.Callable<ResponseEntity<Map>> b) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch largada = new CountDownLatch(1);
        try {
            Future<ResponseEntity<Map>> fa = pool.submit(() -> { largada.await(); return a.call(); });
            Future<ResponseEntity<Map>> fb = pool.submit(() -> { largada.await(); return b.call(); });
            largada.countDown();
            return Stream.of(fa.get(15, TimeUnit.SECONDS), fb.get(15, TimeUnit.SECONDS))
                .map(r -> r.getStatusCode().value()).sorted().toList();
        } finally {
            pool.shutdownNow();
        }
    }

    private int disponivel(long lote) {
        return db.queryForObject("SELECT quantidade_disponivel FROM lote WHERE id = ?", Integer.class, lote);
    }

    private int reservado(long lote) {
        return db.queryForObject("SELECT quantidade_reservada FROM lote WHERE id = ?", Integer.class, lote);
    }

    private int reservasDoPedido(long pedido) {
        return db.queryForObject("SELECT COUNT(*) FROM reserva_estoque WHERE pedido_id = ?", Integer.class, pedido);
    }

    private String validade(long lote) {
        return db.queryForObject("SELECT data_validade FROM lote WHERE id = ?", String.class, lote);
    }
}
