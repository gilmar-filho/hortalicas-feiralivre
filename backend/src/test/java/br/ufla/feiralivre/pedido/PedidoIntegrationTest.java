package br.ufla.feiralivre.pedido;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import br.ufla.feiralivre.TestData;
import br.ufla.feiralivre.entrega.service.EntregaService;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class PedidoIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate db;

    @Test
    public void deveCriarPedidoReduzirEstoqueReservarAtendimentoEGerarFatura() {
        long vendedorId = TestData.usuario(db, "Vendedor pedido", TestData.email("vendedor.pedido"), "123456");
        long localId = TestData.localRetirada(db, vendedorId, "Ponto do teste de pedido");
        long produtoId = TestData.produto(db, vendedorId, "Alface (teste de pedido)", 4.50);
        long loteId = TestData.lote(db, produtoId, 7, 40);
        LocalDate dataRetirada = EntregaService.hoje().plusDays(2);
        long horarioId = TestData.janela(db, localId, dataRetirada, 3);

        ResponseEntity<Map> response = postPedido(produtoId, 2, horarioId, dataRetirada);

        assertEquals(HttpStatus.OK, response.getStatusCode(), "A requisição deve retornar 200 OK");
        assertNotNull(response.getBody(), "O corpo da resposta não deve ser nulo");

        Number pedidoId = (Number) response.getBody().get("id");
        assertNotNull(pedidoId, "O ID do pedido não deve ser nulo");

        String statusPedido = db.queryForObject("SELECT status FROM pedido WHERE id = ?", String.class, pedidoId);
        assertEquals("PENDENTE", statusPedido, "O status do pedido deve ser PENDENTE");

        Integer estoqueDepois = db.queryForObject(
            "SELECT quantidade_disponivel FROM lote WHERE id = ?", Integer.class, loteId);
        assertEquals(38, estoqueDepois, "O estoque deve ser reduzido em 2 unidades");

        Integer reservado = db.queryForObject(
            "SELECT quantidade_reservada FROM lote WHERE id = ?", Integer.class, loteId);
        assertEquals(2, reservado, "As 2 unidades devem ficar reservadas, não sumir do lote");

        Map<String, Object> reserva = db.queryForMap(
            "SELECT horario_retirada_id, data_retirada, status FROM reserva_atendimento WHERE pedido_id = ?", pedidoId);
        assertEquals(horarioId, ((Number) reserva.get("horario_retirada_id")).longValue(),
            "Entrega participa do fluxo: o pedido ocupa um atendimento na janela escolhida");
        assertEquals(dataRetirada.toString(), reserva.get("data_retirada"));
        assertEquals("ATIVA", reserva.get("status"));

        String statusFatura = db.queryForObject(
            "SELECT status FROM fatura WHERE pedido_id = ?", String.class, pedidoId);
        assertEquals("PENDENTE", statusFatura, "A fatura deve ser gerada com status PENDENTE");
    }

    @Test
    public void deveRecusarPedidoQuandoEstoqueEhInsuficiente() {
        long vendedorId = TestData.usuario(db, "Vendedor escasso", TestData.email("vendedor.escasso"), "123456");
        long localId = TestData.localRetirada(db, vendedorId, "Ponto do teste de escassez");
        long produtoId = TestData.produto(db, vendedorId, "Rúcula (estoque curto)", 3.00);
        long loteId = TestData.lote(db, produtoId, 7, 5);
        LocalDate dataRetirada = EntregaService.hoje().plusDays(2);

        ResponseEntity<Map> response = postPedido(produtoId, 10, TestData.janela(db, localId, dataRetirada, 3), dataRetirada);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode(),
            "Pedir mais do que existe em estoque deve ser recusado com 409");
        assertEquals("Estoque insuficiente", response.getBody().get("message"),
            "A mensagem do backend precisa chegar ao frontend");

        Integer estoque = db.queryForObject(
            "SELECT quantidade_disponivel FROM lote WHERE id = ?", Integer.class, loteId);
        assertEquals(5, estoque, "Um pedido recusado não pode consumir estoque");

        Integer pedidos = db.queryForObject(
            "SELECT COUNT(*) FROM item_pedido WHERE produto_id = ?", Integer.class, produtoId);
        assertEquals(0, pedidos, "Um pedido recusado não pode deixar item gravado");
    }

    @Test
    public void deveRecusarPedidoComDataDeRetiradaNoPassado() {
        long vendedorId = TestData.usuario(db, "Vendedor passado", TestData.email("vendedor.passado"), "123456");
        long localId = TestData.localRetirada(db, vendedorId, "Ponto do teste de data");
        long produtoId = TestData.produto(db, vendedorId, "Cenoura (data passada)", 6.20);
        TestData.lote(db, produtoId, 7, 40);
        LocalDate ontem = EntregaService.hoje().minusDays(1);

        ResponseEntity<Map> response = postPedido(produtoId, 1, TestData.janela(db, localId, ontem, 3), ontem);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(),
            "A data de retirada precisa ser futura");
        assertEquals("Esta janela de retirada já encerrou", response.getBody().get("message"));
    }

    @Test
    public void deveRecusarPedidoDeProdutoInativo() {
        long vendedorId = TestData.usuario(db, "Vendedor inativo", TestData.email("vendedor.inativo"), "123456");
        long localId = TestData.localRetirada(db, vendedorId, "Ponto do teste de inativo");
        long produtoId = TestData.produto(db, vendedorId, "Beterraba (desativada)", 5.00, false);
        TestData.lote(db, produtoId, 7, 40);
        LocalDate dataRetirada = EntregaService.hoje().plusDays(2);

        ResponseEntity<Map> response = postPedido(produtoId, 1, TestData.janela(db, localId, dataRetirada, 3), dataRetirada);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode(),
            "Produto desativado não está à venda e deve responder 404, não erro interno");
    }

    private ResponseEntity<Map> postPedido(long produtoId, int quantidade, long horarioId, LocalDate dataRetirada) {
        Map<String, Object> request = Map.of(
            "produtoId", produtoId,
            "quantidade", quantidade,
            "horarioRetiradaId", horarioId,
            "dataRetirada", dataRetirada.toString(),
            "compradorId", 1
        );
        return restTemplate.postForEntity("/api/pedidos", request, Map.class);
    }
}
