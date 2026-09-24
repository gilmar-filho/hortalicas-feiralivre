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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class PedidoIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate db;

    @Test
    public void deveCriarPedidoReduzirEstoqueEGerarFatura() {
        long vendedorId = TestData.usuario(db, "Vendedor pedido", TestData.email("vendedor.pedido"), "123456");
        long localId = TestData.localRetirada(db, vendedorId, "Ponto do teste de pedido");
        long produtoId = TestData.produto(db, vendedorId, "Alface (teste de pedido)", 4.50);
        long loteId = TestData.lote(db, produtoId, 7, 40);

        ResponseEntity<Map> response = criarPedido(produtoId, 2, localId, LocalDate.now().plusDays(2));

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

        ResponseEntity<Map> response = criarPedido(produtoId, 10, localId, LocalDate.now().plusDays(2));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode(),
            "Pedir mais do que existe em estoque deve ser recusado com 409");

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

        ResponseEntity<Map> response = criarPedido(produtoId, 1, localId, LocalDate.now().minusDays(1));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(),
            "A data de retirada precisa ser futura");
    }

    @Test
    public void deveRecusarPedidoDeProdutoInativo() {
        long vendedorId = TestData.usuario(db, "Vendedor inativo", TestData.email("vendedor.inativo"), "123456");
        long localId = TestData.localRetirada(db, vendedorId, "Ponto do teste de inativo");
        long produtoId = TestData.produto(db, vendedorId, "Beterraba (desativada)", 5.00, false);
        TestData.lote(db, produtoId, 7, 40);

        ResponseEntity<Map> response = criarPedido(produtoId, 1, localId, LocalDate.now().plusDays(2));

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode(),
            "Produto desativado não está à venda e deve responder 404, não erro interno");
    }

    private ResponseEntity<Map> criarPedido(long produtoId, int quantidade, long localId, LocalDate dataRetirada) {
        Map<String, Object> request = Map.of(
            "produtoId", produtoId,
            "quantidade", quantidade,
            "dataRetirada", dataRetirada.toString(),
            "horaRetirada", "09:00",
            "localRetiradaId", localId,
            "compradorId", 1
        );
        return restTemplate.postForEntity("/api/pedidos", request, Map.class);
    }
}
