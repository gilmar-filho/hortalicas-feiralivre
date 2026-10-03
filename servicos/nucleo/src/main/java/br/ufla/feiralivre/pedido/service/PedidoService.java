package br.ufla.feiralivre.pedido.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import br.ufla.feiralivre.faturamento.service.FaturamentoService;
import br.ufla.feiralivre.pedido.cliente.EntregaCliente;
import br.ufla.feiralivre.pedido.cliente.ProducaoCliente;
import br.ufla.feiralivre.pedido.cliente.ProdutoResumo;
import br.ufla.feiralivre.pedido.dto.CriarPedidoRequest;
import br.ufla.feiralivre.pedido.repository.PedidoRepository;

/**
 * Nenhuma transação local fica aberta durante uma chamada remota: no SQLite
 * ela seguraria o lock do banco do núcleo enquanto o outro serviço responde.
 */
@Service
public class PedidoService {
    private static final List<String> STATUS = List.of("PENDENTE", "CONFIRMADO", "SEPARADO", "PRONTO", "ENTREGUE", "CANCELADO");
    private final PedidoRepository repository;
    private final ProducaoCliente producao;
    private final EntregaCliente entrega;
    private final FaturamentoService faturamento;
    private final TransactionTemplate transacao;

    public PedidoService(PedidoRepository repository, ProducaoCliente producao, EntregaCliente entrega, FaturamentoService faturamento, TransactionTemplate transacao) { this.repository = repository; this.producao = producao; this.entrega = entrega; this.faturamento = faturamento; this.transacao = transacao; }
    public List<Map<String,Object>> listar(long compradorId) { return repository.listar(compradorId); }
    public List<Map<String,Object>> recebidos(long vendedorId) { return repository.recebidos(vendedorId); }

    public Map<String,Object> criar(CriarPedidoRequest request) {
        if (request.horarioRetiradaId() == null || !dataValida(request.dataRetirada())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe a janela e a data de retirada");
        long compradorId = request.compradorOuPadrao();
        ProdutoResumo produto = producao.produto(request.produtoId());
        entrega.verificarDisponibilidade(request.horarioRetiradaId(), request.dataRetirada(), compradorId, produto.vendedorId());
        // Id reservado e confirmado antes de sair para a rede: com rollback o
        // SQLite reusaria o id, e uma reserva órfã passaria ao próximo pedido.
        long pedidoId = transacao.execute(tx -> repository.proximoId());
        producao.reservarEstoque(pedidoId, request.produtoId(), request.quantidade(), request.dataRetirada());
        entrega.reservarAtendimento(pedidoId, compradorId, produto.vendedorId(), request.horarioRetiradaId(), request.dataRetirada());
        double total = produto.preco() * request.quantidade();
        transacao.executeWithoutResult(tx -> {
            repository.criar(pedidoId, compradorId, total);
            repository.criarItem(pedidoId, request.produtoId(), produto.nome(), produto.vendedorId(), request.quantidade(), produto.preco(), total);
            faturamento.criarFatura(pedidoId, total);
        });
        return repository.buscar(pedidoId);
    }

    public Map<String,Object> atualizarStatus(long pedidoId, String status, long vendedorId) {
        if (!STATUS.contains(status)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status inválido");
        Map<String,Object> pedido = repository.pedidoComStatusParaVendedor(pedidoId, vendedorId);
        String anterior = String.valueOf(pedido.get("status"));
        if ("CANCELADO".equals(anterior)) {
            if ("CANCELADO".equals(status)) return repository.buscar(pedidoId);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Pedido cancelado não pode mudar de status");
        }
        long faturaId = ((Number) repository.faturaDoPedido(pedidoId).get("id")).longValue();
        if ("CANCELADO".equals(status)) {
            producao.devolverEstoque(pedidoId);
            entrega.liberarAtendimento(pedidoId);
        }
        transacao.executeWithoutResult(tx -> {
            repository.atualizarStatus(pedidoId, status);
            repository.atualizarFatura(faturaId, status);
        });
        return repository.buscar(pedidoId);
    }

    private static boolean dataValida(String data) {
        try { LocalDate.parse(data); return true; } catch (RuntimeException e) { return false; }
    }
}
