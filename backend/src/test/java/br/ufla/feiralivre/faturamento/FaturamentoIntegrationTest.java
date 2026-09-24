package br.ufla.feiralivre.faturamento;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import br.ufla.feiralivre.TestData;
import br.ufla.feiralivre.faturamento.service.FaturamentoService;
import br.ufla.feiralivre.pedido.dto.CriarPedidoRequest;
import br.ufla.feiralivre.pedido.service.PedidoService;

/**
 * Verifica a regra de visibilidade, não o formato da consulta: quando estas
 * listagens virarem um read model no D6, a regra continua a mesma.
 */
@SpringBootTest
public class FaturamentoIntegrationTest {

    @Autowired
    private FaturamentoService faturamentoService;

    @Autowired
    private PedidoService pedidoService;

    @Autowired
    private JdbcTemplate db;

    @Test
    public void vendedorDeveVerApenasAsFaturasDosSeusProdutos() {
        long vendedorA = TestData.usuario(db, "Vendedor A", TestData.email("vendedor.a"), "123456");
        long vendedorB = TestData.usuario(db, "Vendedor B", TestData.email("vendedor.b"), "123456");
        long comprador = TestData.usuario(db, "Comprador faturas", TestData.email("comprador.faturas"), "123456");

        long faturaDoA = criarPedidoCom(vendedorA, comprador, "Berinjela do A", 6.00);
        long faturaDoB = criarPedidoCom(vendedorB, comprador, "Berinjela do B", 6.00);

        List<Map<String, Object>> visaoDoA = faturamentoService.listar("vendedor", vendedorA);

        assertTrue(contemFatura(visaoDoA, faturaDoA), "O vendedor vê a fatura do pedido do seu produto");
        assertFalse(contemFatura(visaoDoA, faturaDoB), "O vendedor não pode ver a fatura de outro vendedor");
    }

    @Test
    public void compradorDeveVerApenasAsProprias() {
        long vendedor = TestData.usuario(db, "Vendedor comum", TestData.email("vendedor.comum"), "123456");
        long compradorA = TestData.usuario(db, "Comprador A", TestData.email("comprador.a"), "123456");
        long compradorB = TestData.usuario(db, "Comprador B", TestData.email("comprador.b"), "123456");

        long faturaDoA = criarPedidoCom(vendedor, compradorA, "Chuchu do comprador A", 3.00);
        long faturaDoB = criarPedidoCom(vendedor, compradorB, "Chuchu do comprador B", 3.00);

        List<Map<String, Object>> visaoDoA = faturamentoService.listar("comprador", compradorA);

        assertTrue(contemFatura(visaoDoA, faturaDoA), "O comprador vê a própria fatura");
        assertFalse(contemFatura(visaoDoA, faturaDoB), "O comprador não pode ver a fatura de outro comprador");
    }

    @Test
    public void faturaDeveNascerPendenteComOValorTotalDoPedido() {
        long vendedor = TestData.usuario(db, "Vendedor valor", TestData.email("vendedor.valor"), "123456");
        long comprador = TestData.usuario(db, "Comprador valor", TestData.email("comprador.valor"), "123456");
        long localId = TestData.localRetirada(db, vendedor, "Ponto do teste de valor");
        long produtoId = TestData.produto(db, vendedor, "Quiabo com valor", 7.50);
        TestData.lote(db, produtoId, 7, 30);

        long pedidoId = criarPedido(produtoId, 4, localId, comprador);

        Map<String, Object> fatura = db.queryForMap(
            "SELECT valor, status FROM fatura WHERE pedido_id = ?", pedidoId);

        assertEquals(30.0, ((Number) fatura.get("valor")).doubleValue(), 0.001,
            "A fatura cobra 4 unidades a 7,50");
        assertEquals("PENDENTE", fatura.get("status"), "A fatura nasce pendente");
    }

    private long criarPedidoCom(long vendedorId, long compradorId, String nomeProduto, double preco) {
        long localId = TestData.localRetirada(db, vendedorId, "Ponto de " + nomeProduto);
        long produtoId = TestData.produto(db, vendedorId, nomeProduto, preco);
        TestData.lote(db, produtoId, 7, 30);
        long pedidoId = criarPedido(produtoId, 1, localId, compradorId);
        return db.queryForObject("SELECT id FROM fatura WHERE pedido_id = ?", Long.class, pedidoId);
    }

    private long criarPedido(long produtoId, int quantidade, long localId, long compradorId) {
        Map<String, Object> pedido = pedidoService.criar(new CriarPedidoRequest(
            produtoId, quantidade, LocalDate.now().plusDays(2).toString(), "09:00", localId, compradorId));
        return ((Number) pedido.get("id")).longValue();
    }

    private boolean contemFatura(List<Map<String, Object>> faturas, long faturaId) {
        return faturas.stream().anyMatch(f -> ((Number) f.get("id")).longValue() == faturaId);
    }
}
