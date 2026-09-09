package br.ufla.feiralivre.pedido.repository;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PedidoRepository {
    private final JdbcTemplate db;
    public PedidoRepository(JdbcTemplate db) { this.db = db; }
    public List<Map<String,Object>> listar(long compradorId) { return db.queryForList("SELECT p.*, u.nome comprador_nome, l.nome local_nome, f.id fatura_id, f.valor fatura_valor, f.status fatura_status, (SELECT group_concat(pr.nome, ', ') FROM item_pedido ip JOIN produto pr ON pr.id=ip.produto_id WHERE ip.pedido_id=p.id) produtos FROM pedido p JOIN usuario u ON u.id=p.comprador_id JOIN local_retirada l ON l.id=p.local_retirada_id LEFT JOIN fatura f ON f.pedido_id=p.id WHERE p.comprador_id=? ORDER BY p.id DESC", compradorId); }
    public List<Map<String,Object>> recebidos(long vendedorId) { return db.queryForList("SELECT DISTINCT p.id, p.status, p.valor_total, p.data_pedido, p.data_retirada, p.hora_retirada, u.nome comprador_nome, f.id fatura_id, f.valor fatura_valor, f.status fatura_status FROM pedido p JOIN usuario u ON u.id=p.comprador_id JOIN item_pedido i ON i.pedido_id=p.id JOIN produto pr ON pr.id=i.produto_id LEFT JOIN fatura f ON f.pedido_id=p.id WHERE pr.usuario_id=? ORDER BY p.id DESC", vendedorId); }
    public Map<String,Object> criar(long compradorId, double total, String data, Object hora, Object localId) { db.update("INSERT INTO pedido (comprador_id,status,valor_total,data_pedido,data_retirada,hora_retirada,local_retirada_id) VALUES (?,'PENDENTE',?,datetime('now'),?,?,?)", compradorId, total, data, hora, localId); long id = db.queryForObject("SELECT last_insert_rowid()", Long.class); return db.queryForMap("SELECT * FROM pedido WHERE id=?", id); }
    public void criarItem(long pedidoId, long produtoId, int quantidade, Object preco, double total) { db.update("INSERT INTO item_pedido (pedido_id,produto_id,quantidade,preco_unitario,subtotal) VALUES (?,?,?,?,?)", pedidoId, produtoId, quantidade, preco, total); }
    public Map<String,Object> pedidoComStatusParaVendedor(long id, long vendedorId) { return db.queryForMap("SELECT status FROM pedido WHERE id=? AND EXISTS (SELECT 1 FROM item_pedido i JOIN produto pr ON pr.id=i.produto_id WHERE i.pedido_id=pedido.id AND pr.usuario_id=?)", id, vendedorId); }
    public Map<String,Object> faturaDoPedido(long id) { return db.queryForMap("SELECT id, status FROM fatura WHERE pedido_id=?", id); }
    public void atualizarStatus(long id, String status) { db.update("UPDATE pedido SET status=? WHERE id=?", status, id); }
    public void atualizarFatura(long id, String status) { db.update("UPDATE fatura SET status=? WHERE id=?", status, id); }
    public Map<String,Object> buscar(long id) { return db.queryForMap("SELECT * FROM pedido WHERE id=?", id); }
}
