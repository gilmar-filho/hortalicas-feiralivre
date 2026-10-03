package br.ufla.feiralivre.pedido.repository;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PedidoRepository {
    private final JdbcTemplate db;
    public PedidoRepository(JdbcTemplate db) { this.db = db; }
    public List<Map<String,Object>> listar(long compradorId) { return db.queryForList("SELECT p.*, u.nome comprador_nome, f.id fatura_id, f.valor fatura_valor, f.status fatura_status, (SELECT group_concat(ip.produto_nome, ', ') FROM item_pedido ip WHERE ip.pedido_id=p.id) produtos FROM pedido p JOIN usuario u ON u.id=p.comprador_id LEFT JOIN fatura f ON f.pedido_id=p.id WHERE p.comprador_id=? ORDER BY p.id DESC", compradorId); }
    public List<Map<String,Object>> recebidos(long vendedorId) { return db.queryForList("SELECT DISTINCT p.id, p.status, p.valor_total, p.data_pedido, u.nome comprador_nome, f.id fatura_id, f.valor fatura_valor, f.status fatura_status FROM pedido p JOIN usuario u ON u.id=p.comprador_id JOIN item_pedido i ON i.pedido_id=p.id LEFT JOIN fatura f ON f.pedido_id=p.id WHERE i.vendedor_id=? ORDER BY p.id DESC", vendedorId); }
    public long proximoId() { db.update("UPDATE pedido_sequencia SET valor = valor + 1 WHERE id = 1"); return db.queryForObject("SELECT valor FROM pedido_sequencia WHERE id = 1", Long.class); }
    public void criar(long id, long compradorId, double total) { db.update("INSERT INTO pedido (id,comprador_id,status,valor_total,data_pedido) VALUES (?,?,'PENDENTE',?,datetime('now'))", id, compradorId, total); }
    public void criarItem(long pedidoId, long produtoId, String produtoNome, long vendedorId, int quantidade, double preco, double total) { db.update("INSERT INTO item_pedido (pedido_id,produto_id,produto_nome,vendedor_id,quantidade,preco_unitario,subtotal) VALUES (?,?,?,?,?,?,?)", pedidoId, produtoId, produtoNome, vendedorId, quantidade, preco, total); }
    public Map<String,Object> pedidoComStatusParaVendedor(long id, long vendedorId) { return db.queryForMap("SELECT status FROM pedido WHERE id=? AND EXISTS (SELECT 1 FROM item_pedido i WHERE i.pedido_id=pedido.id AND i.vendedor_id=?)", id, vendedorId); }
    public Map<String,Object> faturaDoPedido(long id) { return db.queryForMap("SELECT id, status FROM fatura WHERE pedido_id=?", id); }
    public void atualizarStatus(long id, String status) { db.update("UPDATE pedido SET status=? WHERE id=?", status, id); }
    public void atualizarFatura(long id, String status) { db.update("UPDATE fatura SET status=? WHERE id=?", status, id); }
    public Map<String,Object> buscar(long id) { return db.queryForMap("SELECT * FROM pedido WHERE id=?", id); }
}
