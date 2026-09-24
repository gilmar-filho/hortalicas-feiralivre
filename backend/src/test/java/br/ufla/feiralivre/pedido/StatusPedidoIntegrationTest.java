package br.ufla.feiralivre.pedido;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import br.ufla.feiralivre.TestData;
import br.ufla.feiralivre.pedido.dto.CriarPedidoRequest;
import br.ufla.feiralivre.pedido.service.PedidoService;

/**
 * O cancelamento é a compensação que o monolito resolve hoje dentro de uma
 * transação. Quando Produção e Faturamento virarem serviços com bancos
 * próprios, o comportamento verificado aqui passa a ser responsabilidade da
 * SAGA — estes testes continuam valendo, muda só o que está por baixo.
 */
@SpringBootTest
public class StatusPedidoIntegrationTest {

    @Autowired
    private PedidoService pedidoService;

    @Autowired
    private JdbcTemplate db;

    @Test
    public void cancelamentoDeveDevolverAQuantidadeReservadaAoLote() {
        long vendedorId = TestData.usuario(db, "Vendedor cancela", TestData.email("vendedor.cancela"), "123456");
        long localId = TestData.localRetirada(db, vendedorId, "Ponto do teste de cancelamento");
        long produtoId = TestData.produto(db, vendedorId, "Couve (teste de cancelamento)", 4.00);
        long loteId = TestData.lote(db, produtoId, 7, 30);

        long pedidoId = criarPedido(produtoId, 4, localId);

        assertEquals(26, disponivel(loteId), "Antes do cancelamento o estoque está reservado");
        assertEquals(4, reservado(loteId), "Antes do cancelamento a reserva existe");

        pedidoService.atualizarStatus(pedidoId, "CANCELADO", vendedorId);

        assertEquals(30, disponivel(loteId), "O cancelamento devolve a quantidade ao estoque disponível");
        assertEquals(0, reservado(loteId), "O cancelamento zera a reserva do lote");
        assertEquals("CANCELADO", statusPedido(pedidoId), "O pedido fica cancelado");
    }

    @Test
    public void cancelamentoRepetidoNaoDeveDevolverEstoqueDuasVezes() {
        long vendedorId = TestData.usuario(db, "Vendedor duplo", TestData.email("vendedor.duplo"), "123456");
        long localId = TestData.localRetirada(db, vendedorId, "Ponto do teste de cancelamento duplo");
        long produtoId = TestData.produto(db, vendedorId, "Espinafre (cancelamento duplo)", 4.00);
        long loteId = TestData.lote(db, produtoId, 7, 30);

        long pedidoId = criarPedido(produtoId, 4, localId);
        pedidoService.atualizarStatus(pedidoId, "CANCELADO", vendedorId);
        pedidoService.atualizarStatus(pedidoId, "CANCELADO", vendedorId);

        assertEquals(30, disponivel(loteId), "Cancelar duas vezes não pode inflar o estoque");
        assertEquals(0, reservado(loteId), "A reserva continua zerada");
    }

    @Test
    public void mudancaDeStatusDoPedidoDeveRefletirNaFatura() {
        long vendedorId = TestData.usuario(db, "Vendedor fatura", TestData.email("vendedor.fatura"), "123456");
        long localId = TestData.localRetirada(db, vendedorId, "Ponto do teste de fatura");
        long produtoId = TestData.produto(db, vendedorId, "Brócolis (teste de fatura)", 8.00);
        TestData.lote(db, produtoId, 7, 30);

        long pedidoId = criarPedido(produtoId, 1, localId);
        assertEquals("PENDENTE", statusFatura(pedidoId), "A fatura nasce pendente junto do pedido");

        pedidoService.atualizarStatus(pedidoId, "CONFIRMADO", vendedorId);

        assertEquals("CONFIRMADO", statusPedido(pedidoId), "O pedido muda de status");
        assertEquals("CONFIRMADO", statusFatura(pedidoId), "A fatura acompanha o status do pedido");
    }

    @Test
    public void deveRecusarStatusInvalido() {
        long vendedorId = TestData.usuario(db, "Vendedor status", TestData.email("vendedor.status"), "123456");
        long localId = TestData.localRetirada(db, vendedorId, "Ponto do teste de status");
        long produtoId = TestData.produto(db, vendedorId, "Alho-poró (status inválido)", 9.00);
        TestData.lote(db, produtoId, 7, 30);

        long pedidoId = criarPedido(produtoId, 1, localId);

        try {
            pedidoService.atualizarStatus(pedidoId, "EM_ROTA", vendedorId);
            throw new AssertionError("Um status fora da lista conhecida deveria ser recusado");
        } catch (org.springframework.web.server.ResponseStatusException e) {
            assertEquals(400, e.getStatusCode().value(), "Status desconhecido deve responder 400");
        }

        assertEquals("PENDENTE", statusPedido(pedidoId), "O pedido não muda quando o status é inválido");
    }

    private long criarPedido(long produtoId, int quantidade, long localId) {
        Map<String, Object> pedido = pedidoService.criar(new CriarPedidoRequest(
            produtoId, quantidade, LocalDate.now().plusDays(2).toString(), "09:00", localId, 1L));
        return ((Number) pedido.get("id")).longValue();
    }

    private int disponivel(long loteId) {
        return db.queryForObject("SELECT quantidade_disponivel FROM lote WHERE id = ?", Integer.class, loteId);
    }

    private int reservado(long loteId) {
        return db.queryForObject("SELECT quantidade_reservada FROM lote WHERE id = ?", Integer.class, loteId);
    }

    private String statusPedido(long pedidoId) {
        return db.queryForObject("SELECT status FROM pedido WHERE id = ?", String.class, pedidoId);
    }

    private String statusFatura(long pedidoId) {
        return db.queryForObject("SELECT status FROM fatura WHERE pedido_id = ?", String.class, pedidoId);
    }
}
