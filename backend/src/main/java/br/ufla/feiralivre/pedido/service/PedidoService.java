package br.ufla.feiralivre.pedido.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.ufla.feiralivre.entrega.service.EntregaService;
import br.ufla.feiralivre.faturamento.service.FaturamentoService;
import br.ufla.feiralivre.pedido.dto.CriarPedidoRequest;
import br.ufla.feiralivre.pedido.repository.PedidoRepository;
import br.ufla.feiralivre.producao.service.ProducaoService;

@Service
public class PedidoService {
    private static final List<String> STATUS = List.of("PENDENTE", "CONFIRMADO", "SEPARADO", "PRONTO", "ENTREGUE", "CANCELADO");
    private final PedidoRepository repository;
    private final ProducaoService producao;
    private final EntregaService entrega;
    private final FaturamentoService faturamento;

    public PedidoService(PedidoRepository repository, ProducaoService producao, EntregaService entrega, FaturamentoService faturamento) { this.repository = repository; this.producao = producao; this.entrega = entrega; this.faturamento = faturamento; }
    public List<Map<String,Object>> listar(long compradorId) { return repository.listar(compradorId); }
    public List<Map<String,Object>> recebidos(long vendedorId) { return repository.recebidos(vendedorId); }

    @Transactional
    public Map<String,Object> criar(CriarPedidoRequest request) {
        if (request.horarioRetiradaId() == null || !dataValida(request.dataRetirada())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe a janela e a data de retirada");
        Map<String,Object> produto = producao.produtoAtivo(request.produtoId());
        List<Map<String,Object>> lotes = producao.lotesDisponiveis(request.produtoId(), request.dataRetirada());
        int restante = request.quantidade();
        for (Map<String,Object> lote : lotes) restante -= Math.min(restante, ((Number) lote.get("quantidade_disponivel")).intValue());
        if (restante > 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "Estoque insuficiente");
        double total = ((Number) produto.get("preco")).doubleValue() * request.quantidade();
        Map<String,Object> pedido = repository.criar(request.compradorOuPadrao(), total);
        long pedidoId = ((Number) pedido.get("id")).longValue();
        repository.criarItem(pedidoId, request.produtoId(), request.quantidade(), produto.get("preco"), total);
        restante = request.quantidade();
        for (Map<String,Object> lote : lotes) { if (restante == 0) break; int usar = Math.min(restante, ((Number) lote.get("quantidade_disponivel")).intValue()); producao.reservar(((Number) lote.get("id")).longValue(), pedidoId, usar); restante -= usar; }
        entrega.reservarAtendimento(pedidoId, request.compradorOuPadrao(), ((Number) produto.get("usuario_id")).longValue(), request.horarioRetiradaId(), request.dataRetirada());
        faturamento.criarFatura(pedidoId, total);
        return pedido;
    }

    @Transactional
    public Map<String,Object> atualizarStatus(long pedidoId, String status, long vendedorId) {
        if (!STATUS.contains(status)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status inválido");
        Map<String,Object> pedido = repository.pedidoComStatusParaVendedor(pedidoId, vendedorId);
        String anterior = String.valueOf(pedido.get("status"));
        if ("CANCELADO".equals(anterior)) {
            if ("CANCELADO".equals(status)) return repository.buscar(pedidoId);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Pedido cancelado não pode mudar de status");
        }
        Map<String,Object> fatura = repository.faturaDoPedido(pedidoId);
        if ("CANCELADO".equals(status)) {
            boolean vendido = "APROVADO".equals(String.valueOf(fatura.get("status")));
            for (Map<String,Object> reserva : producao.reservas(pedidoId)) producao.devolverReserva(((Number) reserva.get("lote_id")).longValue(), ((Number) reserva.get("quantidade")).intValue(), vendido);
            entrega.liberarAtendimento(pedidoId);
        }
        repository.atualizarStatus(pedidoId, status);
        repository.atualizarFatura(((Number) fatura.get("id")).longValue(), status);
        return repository.buscar(pedidoId);
    }

    private static boolean dataValida(String data) {
        try { LocalDate.parse(data); return true; } catch (RuntimeException e) { return false; }
    }
}
