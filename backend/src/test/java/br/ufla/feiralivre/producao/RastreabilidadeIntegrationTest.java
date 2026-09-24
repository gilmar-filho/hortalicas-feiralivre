package br.ufla.feiralivre.producao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import br.ufla.feiralivre.TestData;

/**
 * A pergunta que o produto existe para responder: dado um pedido, de qual
 * lote saiu o produto, de onde ele veio e qual era a validade.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class RastreabilidadeIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate db;

    @Test
    public void devePermitirChegarDoPedidoAteAOrigemEValidadeDoLote() {
        long vendedorId = TestData.usuario(db, "Vendedor rastreio", TestData.email("vendedor.rastreio"), "123456");
        long localId = TestData.localRetirada(db, vendedorId, "Ponto do teste de rastreio");
        long produtoId = TestData.produto(db, vendedorId, "Tomate (teste de rastreio)", 7.90);
        long loteId = TestData.lote(db, produtoId, 5, 25, "Sítio Boa Terra — talhão 3");

        long pedidoId = criarPedido(produtoId, 3, localId);

        Map<String, Object> rastreio = db.queryForMap(
            "SELECT l.id lote_id, l.origem, l.data_validade, re.quantidade "
            + "FROM reserva_estoque re JOIN lote l ON l.id = re.lote_id WHERE re.pedido_id = ?", pedidoId);

        assertEquals(loteId, ((Number) rastreio.get("lote_id")).longValue(),
            "O pedido precisa apontar para o lote específico de onde saiu o produto");
        assertEquals("Sítio Boa Terra — talhão 3", rastreio.get("origem"),
            "A origem do lote precisa ser recuperável a partir do pedido");
        String validadeDoLote = db.queryForObject(
            "SELECT data_validade FROM lote WHERE id = ?", String.class, loteId);
        assertEquals(validadeDoLote, rastreio.get("data_validade"),
            "A validade do lote precisa ser recuperável a partir do pedido");
        assertEquals(3, ((Number) rastreio.get("quantidade")).intValue(),
            "A quantidade rastreada é a que foi efetivamente reservada");
    }

    @Test
    public void pedidoAtendidoPorDoisLotesDeveRastrearOsDois() {
        long vendedorId = TestData.usuario(db, "Vendedor rastreio 2", TestData.email("vendedor.rastreio2"), "123456");
        long localId = TestData.localRetirada(db, vendedorId, "Ponto do teste de rastreio duplo");
        long produtoId = TestData.produto(db, vendedorId, "Cebolinha (rastreio duplo)", 2.50);
        TestData.lote(db, produtoId, 3, 4, "Horta da Serra");
        TestData.lote(db, produtoId, 9, 20, "Sítio Boa Terra");

        long pedidoId = criarPedido(produtoId, 7, localId);

        List<Map<String, Object>> origens = db.queryForList(
            "SELECT l.origem, re.quantidade FROM reserva_estoque re "
            + "JOIN lote l ON l.id = re.lote_id WHERE re.pedido_id = ? ORDER BY re.id", pedidoId);

        assertEquals(2, origens.size(), "Um pedido servido por dois lotes rastreia os dois");
        assertEquals("Horta da Serra", origens.get(0).get("origem"));
        assertEquals(4, ((Number) origens.get(0).get("quantidade")).intValue());
        assertEquals("Sítio Boa Terra", origens.get(1).get("origem"));
        assertEquals(3, ((Number) origens.get(1).get("quantidade")).intValue());
    }

    private long criarPedido(long produtoId, int quantidade, long localId) {
        Map<String, Object> request = Map.of(
            "produtoId", produtoId,
            "quantidade", quantidade,
            "dataRetirada", LocalDate.now().plusDays(1).toString(),
            "horaRetirada", "09:00",
            "localRetiradaId", localId,
            "compradorId", 1
        );
        ResponseEntity<Map> response = restTemplate.postForEntity("/api/pedidos", request, Map.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        return ((Number) response.getBody().get("id")).longValue();
    }
}
