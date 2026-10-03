package br.ufla.feiralivre.pedido;

import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import br.ufla.feiralivre.ServicosSimulados;
import br.ufla.feiralivre.TestData;
import br.ufla.feiralivre.pedido.dto.CriarPedidoRequest;
import br.ufla.feiralivre.pedido.service.PedidoService;

public class StatusPedidoIntegrationTest extends ServicosSimulados {

    @Autowired
    private PedidoService pedidoService;

    @Autowired
    private JdbcTemplate db;

    @Test
    public void cancelamentoDeveDevolverEstoqueELiberarAVaga() {
        long vendedor = pessoa("vendedor.cancela");
        long pedido = criarPedido(vendedor);

        pedidoService.atualizarStatus(pedido, "CANCELADO", vendedor);

        PRODUCAO.verify(1, deleteRequestedFor(urlEqualTo("/interno/reservas-estoque/" + pedido)));
        ENTREGA.verify(1, deleteRequestedFor(urlEqualTo("/interno/atendimentos/" + pedido)));
        assertEquals("CANCELADO", statusPedido(pedido));
        assertEquals("CANCELADO", statusFatura(pedido));
    }

    @Test
    public void cancelamentoRepetidoNaoDeveChamarOsServicosDeNovo() {
        long vendedor = pessoa("vendedor.duplo");
        long pedido = criarPedido(vendedor);

        pedidoService.atualizarStatus(pedido, "CANCELADO", vendedor);
        pedidoService.atualizarStatus(pedido, "CANCELADO", vendedor);

        PRODUCAO.verify(1, deleteRequestedFor(urlPathMatching("/interno/reservas-estoque/.*")));
        ENTREGA.verify(1, deleteRequestedFor(urlPathMatching("/interno/atendimentos/.*")));
    }

    @Test
    public void mudancaDeStatusDoPedidoDeveRefletirNaFaturaSemChamarServicos() {
        long vendedor = pessoa("vendedor.fatura");
        long pedido = criarPedido(vendedor);
        assertEquals("PENDENTE", statusFatura(pedido), "A fatura nasce pendente junto do pedido");

        pedidoService.atualizarStatus(pedido, "CONFIRMADO", vendedor);

        assertEquals("CONFIRMADO", statusPedido(pedido));
        assertEquals("CONFIRMADO", statusFatura(pedido), "A fatura acompanha o status do pedido");
        PRODUCAO.verify(0, deleteRequestedFor(urlPathMatching("/interno/.*")));
    }

    @Test
    public void deveRecusarStatusInvalido() {
        long vendedor = pessoa("vendedor.status");
        long pedido = criarPedido(vendedor);

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
            () -> pedidoService.atualizarStatus(pedido, "EM_ROTA", vendedor));

        assertEquals(400, e.getStatusCode().value(), "Status desconhecido deve responder 400");
        assertEquals("PENDENTE", statusPedido(pedido), "O pedido não muda quando o status é inválido");
    }

    @Test
    public void pedidoCanceladoNaoPodeVoltarParaOutroStatus() {
        long vendedor = pessoa("vendedor.terminal");
        long pedido = criarPedido(vendedor);
        pedidoService.atualizarStatus(pedido, "CANCELADO", vendedor);

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
            () -> pedidoService.atualizarStatus(pedido, "PENDENTE", vendedor));

        assertEquals(409, e.getStatusCode().value());
        assertEquals("Pedido cancelado não pode mudar de status", e.getReason());
        assertEquals("CANCELADO", statusPedido(pedido), "Reativar deixaria o pedido sem estoque e sem vaga");
    }

    @Test
    public void producaoForaDoArNoCancelamentoMantemOPedidoEPermiteNovaTentativa() {
        long vendedor = pessoa("vendedor.semProducao");
        long pedido = criarPedido(vendedor);
        producaoForaDoArNaDevolucao();

        assertThrows(RuntimeException.class, () -> pedidoService.atualizarStatus(pedido, "CANCELADO", vendedor));
        assertEquals("PENDENTE", statusPedido(pedido), "Sem a devolução confirmada, o pedido não pode ficar cancelado");

        devolucoesAceitas();
        pedidoService.atualizarStatus(pedido, "CANCELADO", vendedor);

        assertEquals("CANCELADO", statusPedido(pedido));
    }

    private long criarPedido(long vendedor) {
        long produto = TestData.id();
        servicosAceitamPedido(produto, vendedor, "Couve (status)", 4.00);
        Map<String, Object> pedido = pedidoService.criar(new CriarPedidoRequest(
            produto, 1, 77L, TestData.hoje().plusDays(2).toString(), 1L));
        return ((Number) pedido.get("id")).longValue();
    }

    private long pessoa(String prefixo) {
        return TestData.usuario(db, prefixo, TestData.email(prefixo), "123456");
    }

    private String statusPedido(long pedido) {
        return db.queryForObject("SELECT status FROM pedido WHERE id = ?", String.class, pedido);
    }

    private String statusFatura(long pedido) {
        return db.queryForObject("SELECT status FROM fatura WHERE pedido_id = ?", String.class, pedido);
    }
}
