package br.ufla.feiralivre.pedido;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import br.ufla.feiralivre.faturamento.FaturamentoService;

@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {
    private final JdbcTemplate db;
    private final FaturamentoService faturamento;
    public PedidoController(JdbcTemplate db, FaturamentoService faturamento) { this.db = db; this.faturamento = faturamento; }

    @GetMapping
    public List<Map<String,Object>> listar(@RequestParam(defaultValue = "1") long compradorId) { return db.queryForList("SELECT p.*, u.nome comprador_nome, l.nome local_nome, f.id fatura_id, f.valor fatura_valor, f.status fatura_status, (SELECT group_concat(pr.nome, ', ') FROM item_pedido ip JOIN produto pr ON pr.id=ip.produto_id WHERE ip.pedido_id=p.id) produtos FROM pedido p JOIN usuario u ON u.id=p.comprador_id JOIN local_retirada l ON l.id=p.local_retirada_id LEFT JOIN fatura f ON f.pedido_id=p.id WHERE p.comprador_id=? ORDER BY p.id DESC", compradorId); }

    @GetMapping("/recebidos")
    public List<Map<String,Object>> recebidos(@RequestParam(defaultValue = "1") long vendedorId) { return db.queryForList("SELECT DISTINCT p.id, p.status, p.valor_total, p.data_pedido, p.data_retirada, p.hora_retirada, u.nome comprador_nome, f.id fatura_id, f.valor fatura_valor, f.status fatura_status FROM pedido p JOIN usuario u ON u.id=p.comprador_id JOIN item_pedido i ON i.pedido_id=p.id JOIN produto pr ON pr.id=i.produto_id LEFT JOIN fatura f ON f.pedido_id=p.id WHERE pr.usuario_id=? ORDER BY p.id DESC", vendedorId); }

    @PostMapping
    @Transactional
    public Map<String,Object> criar(@RequestBody Map<String,Object> dados) {
        long produtoId = ((Number) dados.get("produtoId")).longValue();
        int quantidade = ((Number) dados.get("quantidade")).intValue();
        String dataRetirada = String.valueOf(dados.get("dataRetirada"));
        if (LocalDate.parse(dataRetirada).isBefore(LocalDate.now())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A data de retirada deve ser futura");
        Map<String,Object> produto = db.queryForMap("SELECT * FROM produto WHERE id=? AND ativo=1", produtoId);
        List<Map<String,Object>> lotes = db.queryForList("SELECT * FROM lote WHERE produto_id=? AND data_validade >= ? AND quantidade_disponivel > 0 ORDER BY data_validade", produtoId, dataRetirada);
        int restante = quantidade;
        for (Map<String,Object> lote : lotes) restante -= Math.min(restante, ((Number) lote.get("quantidade_disponivel")).intValue());
        if (restante > 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "Estoque insuficiente");
        double total = ((Number) produto.get("preco")).doubleValue() * quantidade;
        long compradorId = ((Number) dados.getOrDefault("compradorId", 1)).longValue();
        db.update("INSERT INTO pedido (comprador_id,status,valor_total,data_pedido,data_retirada,hora_retirada,local_retirada_id) VALUES (?,'PENDENTE',?,datetime('now'),?,?,?)", compradorId, total, dataRetirada, dados.get("horaRetirada"), dados.get("localRetiradaId"));
        long pedidoId = Objects.requireNonNull(db.queryForObject("SELECT last_insert_rowid()", Long.class));
        db.update("INSERT INTO item_pedido (pedido_id,produto_id,quantidade,preco_unitario,subtotal) VALUES (?,?,?,?,?)", pedidoId, produtoId, quantidade, produto.get("preco"), total);
        restante = quantidade;
        for (Map<String,Object> lote : lotes) { if (restante == 0) break; int usar = Math.min(restante, ((Number) lote.get("quantidade_disponivel")).intValue()); db.update("UPDATE lote SET quantidade_disponivel=quantidade_disponivel-?, quantidade_reservada=quantidade_reservada+? WHERE id=?", usar, usar, lote.get("id")); db.update("INSERT INTO reserva_estoque (pedido_id,lote_id,quantidade) VALUES (?,?,?)", pedidoId, lote.get("id"), usar); restante -= usar; }
        faturamento.criarFatura(pedidoId, total);
        return db.queryForMap("SELECT * FROM pedido WHERE id=?", pedidoId);
    }

    @PatchMapping("/{pedidoId}/status")
    @Transactional
    public Map<String, Object> atualizarStatus(@PathVariable long pedidoId, @RequestBody Map<String, Object> dados) {
        String status = String.valueOf(dados.get("status"));
        long vendedorId = ((Number) dados.getOrDefault("vendedorId", 1)).longValue();
        if (!List.of("PENDENTE", "CONFIRMADO", "SEPARADO", "PRONTO", "ENTREGUE", "CANCELADO").contains(status)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status inválido");
        Map<String, Object> pedido = db.queryForMap("SELECT status FROM pedido WHERE id=? AND EXISTS (SELECT 1 FROM item_pedido i JOIN produto pr ON pr.id=i.produto_id WHERE i.pedido_id=pedido.id AND pr.usuario_id=?)", pedidoId, vendedorId);
        String statusAnterior = String.valueOf(pedido.get("status"));
        Map<String, Object> fatura = db.queryForMap("SELECT id, status FROM fatura WHERE pedido_id=?", pedidoId);
        boolean cancelando = "CANCELADO".equals(status) && !"CANCELADO".equals(statusAnterior);
        if (cancelando) {
            boolean estoqueVendido = "APROVADO".equals(String.valueOf(fatura.get("status")));
            List<Map<String, Object>> reservas = db.queryForList("SELECT lote_id, quantidade FROM reserva_estoque WHERE pedido_id=?", pedidoId);
            for (Map<String, Object> reserva : reservas) {
                long loteId = ((Number) reserva.get("lote_id")).longValue();
                int quantidade = ((Number) reserva.get("quantidade")).intValue();
                if (estoqueVendido) {
                    db.update("UPDATE lote SET quantidade_disponivel=quantidade_disponivel+?, quantidade_vendida=quantidade_vendida-? WHERE id=?", quantidade, quantidade, loteId);
                } else {
                    db.update("UPDATE lote SET quantidade_disponivel=quantidade_disponivel+?, quantidade_reservada=quantidade_reservada-? WHERE id=?", quantidade, quantidade, loteId);
                }
            }
        }
        db.update("UPDATE pedido SET status=? WHERE id=?", status, pedidoId);
        db.update("UPDATE fatura SET status=? WHERE id=?", status, fatura.get("id"));
        return db.queryForMap("SELECT * FROM pedido WHERE id=?", pedidoId);
    }

}
