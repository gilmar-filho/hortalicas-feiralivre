package br.ufla.feiralivre.faturamento.repository;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class FaturamentoRepository {
    private final JdbcTemplate db;

    public FaturamentoRepository(JdbcTemplate db) { this.db = db; }

    public long criar(long pedidoId, double valor) {
        db.update("INSERT INTO fatura (pedido_id,valor,status) VALUES (?,?, 'PENDENTE')", pedidoId, valor);
        return db.queryForObject("SELECT last_insert_rowid()", Long.class);
    }

    public List<Map<String, Object>> listar(String visao, long usuarioId) {
        String filtro = "vendedor".equals(visao)
            ? "EXISTS (SELECT 1 FROM item_pedido i JOIN produto pr ON pr.id=i.produto_id WHERE i.pedido_id=p.id AND pr.usuario_id=" + usuarioId + ")"
            : "p.comprador_id=" + usuarioId;
        String sql = "SELECT f.id, f.pedido_id, f.valor, f.status, u.nome comprador_nome, u.email comprador_email, p.status pedido_status, p.data_pedido, p.data_retirada, p.hora_retirada, r.nome local_nome, r.endereco local_endereco, "
            + "(SELECT p2.data_retirada FROM pedido p2 JOIN fatura f2 ON f2.pedido_id=p2.id WHERE p2.comprador_id=p.comprador_id AND p2.status <> 'CANCELADO' AND f2.status <> 'CANCELADO' AND date(p2.data_retirada) > date('now') ORDER BY date(p2.data_retirada), p2.id LIMIT 1) proxima_retirada, "
            + "(SELECT f2.id FROM pedido p2 JOIN fatura f2 ON f2.pedido_id=p2.id WHERE p2.comprador_id=p.comprador_id AND p2.status <> 'CANCELADO' AND f2.status <> 'CANCELADO' AND date(p2.data_retirada) > date('now') ORDER BY date(p2.data_retirada), p2.id LIMIT 1) proxima_retirada_fatura_id, "
            + "(SELECT group_concat(pr.nome || ' | qtd: ' || i.quantidade || ' | unitário: R$ ' || i.preco_unitario, char(10)) FROM item_pedido i JOIN produto pr ON pr.id=i.produto_id WHERE i.pedido_id=p.id) produtos, "
            + "(SELECT group_concat(l.origem || ' | validade: ' || l.data_validade, char(10)) FROM reserva_estoque re JOIN lote l ON l.id=re.lote_id WHERE re.pedido_id=p.id) lotes "
            + "FROM fatura f JOIN pedido p ON p.id=f.pedido_id JOIN usuario u ON u.id=p.comprador_id JOIN local_retirada r ON r.id=p.local_retirada_id WHERE " + filtro + " ORDER BY f.id DESC";
        return db.queryForList(sql);
    }

    public Map<String, Object> atualizar(long id, double valor, String status) {
        db.update("UPDATE fatura SET valor=?, status=? WHERE id=?", valor, status, id);
        return db.queryForMap("SELECT * FROM fatura WHERE id=?", id);
    }
}
