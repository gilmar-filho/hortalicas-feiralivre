package br.ufla.feiralivre.producao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

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
public class FefoIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate db;

    @BeforeEach
    public void validarContrato() {
        ValidacaoDeContrato.instalar(restTemplate, "../../contracts/producao.yaml");
    }

    @Test
    public void deveReservarPrimeiroOLoteMaisProximoDoVencimento() {
        long vendedorId = TestData.id();
        long produtoId = TestData.produto(db, vendedorId, "Rúcula (teste FEFO)", 3.00);

        // O lote que vence mais tarde é cadastrado primeiro, e por isso recebe
        // o menor id. Se a alocação seguisse a ordem de cadastro em vez da
        // validade, ela escolheria este — é o que o teste precisa flagrar.
        long loteValidadeLonga = TestData.lote(db, produtoId, 10, 50);
        long loteValidadeCurta = TestData.lote(db, produtoId, 3, 50);

        long pedidoId = criarPedido(produtoId, 5, TestData.hoje().plusDays(1));

        long loteReservado = db.queryForObject(
            "SELECT lote_id FROM reserva_estoque WHERE pedido_id = ?", Long.class, pedidoId);
        assertEquals(loteValidadeCurta, loteReservado,
            "FEFO: a reserva deve sair do lote que vence primeiro, não do cadastrado primeiro");

        assertEquals(45, disponivel(loteValidadeCurta), "O lote que vence antes deve ter sido consumido");
        assertEquals(50, disponivel(loteValidadeLonga), "O lote que vence depois deve permanecer intacto");
    }

    @Test
    public void deveTransbordarParaOProximoLoteRespeitandoAOrdemDeValidade() {
        long vendedorId = TestData.id();
        long produtoId = TestData.produto(db, vendedorId, "Agrião (teste de transbordo)", 3.50);

        long loteLonge = TestData.lote(db, produtoId, 12, 50);
        long lotePerto = TestData.lote(db, produtoId, 2, 5);
        long loteMeio = TestData.lote(db, produtoId, 6, 10);

        // 12 unidades não cabem no lote que vence primeiro: 5 saem dele,
        // 7 do seguinte na ordem de validade, e o mais distante fica intacto.
        long pedidoId = criarPedido(produtoId, 12, TestData.hoje().plusDays(1));

        assertEquals(0, disponivel(lotePerto), "O lote que vence primeiro é esvaziado antes de usar outro");
        assertEquals(3, disponivel(loteMeio), "O restante sai do segundo lote na ordem de validade");
        assertEquals(50, disponivel(loteLonge), "O lote de validade mais distante não é tocado");

        List<Map<String, Object>> reservas = db.queryForList(
            "SELECT lote_id, quantidade FROM reserva_estoque WHERE pedido_id = ? ORDER BY id", pedidoId);
        assertEquals(2, reservas.size(), "A reserva deve registrar os dois lotes usados, não um só");
        assertEquals(lotePerto, ((Number) reservas.get(0).get("lote_id")).longValue(),
            "A primeira reserva é do lote que vence antes");
        assertEquals(5, ((Number) reservas.get(0).get("quantidade")).intValue());
        assertEquals(loteMeio, ((Number) reservas.get(1).get("lote_id")).longValue());
        assertEquals(7, ((Number) reservas.get(1).get("quantidade")).intValue());
    }

    @Test
    public void naoDeveAlocarLoteQueVenceAntesDaDataDeRetirada() {
        long vendedorId = TestData.id();
        long produtoId = TestData.produto(db, vendedorId, "Almeirão (teste de validade)", 4.00);

        long loteQueVenceAntes = TestData.lote(db, produtoId, 2, 40);
        long loteAindaValido = TestData.lote(db, produtoId, 15, 40);

        // A retirada é daqui a 8 dias: o lote que vence em 2 não chega lá.
        long pedidoId = criarPedido(produtoId, 3, TestData.hoje().plusDays(8));

        long loteReservado = db.queryForObject(
            "SELECT lote_id FROM reserva_estoque WHERE pedido_id = ?", Long.class, pedidoId);
        assertEquals(loteAindaValido, loteReservado,
            "Um lote que vence antes da retirada não pode ser alocado, mesmo vencendo primeiro");
        assertEquals(40, disponivel(loteQueVenceAntes), "O lote vencido na data da retirada fica intacto");
    }

    @Test
    public void deveRecusarPedidoQuandoTodoOEstoqueVenceAntesDaRetirada() {
        long vendedorId = TestData.id();
        long produtoId = TestData.produto(db, vendedorId, "Salsa (estoque vencido)", 2.00);
        TestData.lote(db, produtoId, 2, 40);

        ResponseEntity<Map> response = postPedido(produtoId, 3, TestData.hoje().plusDays(10));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode(),
            "Estoque que não chega válido na data da retirada não conta como disponível");
    }

    private long criarPedido(long produtoId, int quantidade, LocalDate dataRetirada) {
        ResponseEntity<Map> response = postPedido(produtoId, quantidade, dataRetirada);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        return ((Number) response.getBody().get("pedidoId")).longValue();
    }

    private ResponseEntity<Map> postPedido(long produtoId, int quantidade, LocalDate dataRetirada) {
        Map<String, Object> request = Map.of(
            "pedidoId", TestData.id(),
            "produtoId", produtoId,
            "quantidade", quantidade,
            "dataRetirada", dataRetirada.toString()
        );
        return restTemplate.postForEntity("/interno/reservas-estoque", request, Map.class);
    }

    private int disponivel(long loteId) {
        return db.queryForObject("SELECT quantidade_disponivel FROM lote WHERE id = ?", Integer.class, loteId);
    }
}
