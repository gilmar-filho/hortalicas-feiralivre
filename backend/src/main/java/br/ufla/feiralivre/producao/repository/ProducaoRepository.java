package br.ufla.feiralivre.producao.repository;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProducaoRepository {
    private final JdbcTemplate db;

    public ProducaoRepository(JdbcTemplate db) { this.db = db; }

    public void migrarLocalDoProduto() {
        try { db.execute("ALTER TABLE produto ADD COLUMN local_retirada_id INTEGER"); } catch (Exception ignored) { }
        try { db.execute("ALTER TABLE produto ADD COLUMN categoria TEXT NOT NULL DEFAULT 'Hortaliças'"); } catch (Exception ignored) { }
    }

    public List<Map<String, Object>> listar(String busca, Long usuarioId) {
        String filtroDono = usuarioId == null ? "" : " AND p.usuario_id = " + usuarioId;
        String sql = "SELECT p.id, p.usuario_id, p.nome, p.categoria, p.descricao, p.foto, p.preco, p.ativo, p.data_cadastro, p.local_retirada_id, r.nome local_nome, r.endereco local_endereco, COALESCE(SUM(CASE WHEN l.data_validade >= date('now') THEN l.quantidade_disponivel ELSE 0 END), 0) estoque, MIN(CASE WHEN l.data_validade >= date('now') AND l.quantidade_disponivel > 0 THEN l.data_validade END) validade, (SELECT l2.id FROM lote l2 WHERE l2.produto_id=p.id AND l2.data_validade >= date('now') ORDER BY l2.data_validade, l2.id LIMIT 1) lote_id, (SELECT l2.quantidade_disponivel FROM lote l2 WHERE l2.produto_id=p.id AND l2.data_validade >= date('now') ORDER BY l2.data_validade, l2.id LIMIT 1) quantidade_disponivel, (SELECT l2.data_validade FROM lote l2 WHERE l2.produto_id=p.id AND l2.data_validade >= date('now') ORDER BY l2.data_validade, l2.id LIMIT 1) data_validade FROM produto p LEFT JOIN lote l ON l.produto_id = p.id LEFT JOIN local_retirada r ON r.id=p.local_retirada_id WHERE p.ativo = 1" + filtroDono + " AND lower(p.nome) LIKE lower(?) GROUP BY p.id ORDER BY p.nome";
        return db.queryForList(sql, "%" + busca + "%");
    }

    public Map<String, Object> produtoAtivo(long id) { return db.queryForMap("SELECT * FROM produto WHERE id=? AND ativo=1", id); }
    public List<Map<String, Object>> lotesDisponiveis(long produtoId, String data) { return db.queryForList("SELECT * FROM lote WHERE produto_id=? AND data_validade >= ? AND quantidade_disponivel > 0 ORDER BY data_validade", produtoId, data); }
    public Map<String, Object> criarProduto(Map<String, Object> dados, long localId) {
        db.update("INSERT INTO produto (usuario_id,nome,categoria,descricao,foto,preco,ativo,data_cadastro,local_retirada_id) VALUES (?,?,?,?,?,?,?,?,?)", dados.get("usuarioId"), dados.get("nome"), dados.getOrDefault("categoria", "Hortaliças"), dados.get("descricao"), dados.get("foto"), dados.get("preco"), dados.get("ativo"), dados.get("dataCadastro"), localId);
        return db.queryForMap("SELECT * FROM produto WHERE id=last_insert_rowid()");
    }
    public Map<String, Object> produto(long id) { return db.queryForMap("SELECT * FROM produto WHERE id=?", id); }
    public void atualizarProduto(long id, Map<String, Object> dados, long localId) { db.update("UPDATE produto SET usuario_id=?, nome=?, categoria=?, descricao=?, foto=?, preco=?, ativo=?, data_cadastro=?, local_retirada_id=? WHERE id=?", dados.get("usuarioId"), dados.get("nome"), dados.getOrDefault("categoria", "Hortaliças"), dados.get("descricao"), dados.get("foto"), dados.get("preco"), dados.get("ativo"), dados.get("dataCadastro"), localId, id); }
    public void desativar(long id) { db.update("UPDATE produto SET ativo=0 WHERE id=?", id); }
    public Map<String, Object> criarLote(long produtoId, Map<String, Object> dados) { db.update("INSERT INTO lote (produto_id,origem,data_producao,data_validade,quantidade_total,quantidade_disponivel) VALUES (?,?,?,?,?,?)", produtoId, dados.getOrDefault("origem", "Produção própria"), dados.getOrDefault("dataProducao", dados.get("dataCadastro")), dados.get("dataValidade"), dados.getOrDefault("quantidadeEstoque", dados.get("quantidade")), dados.getOrDefault("quantidadeEstoque", dados.get("quantidade"))); return db.queryForMap("SELECT * FROM lote WHERE id = last_insert_rowid()"); }
    public Map<String, Object> lote(long id) { return db.queryForMap("SELECT quantidade_reservada, quantidade_vendida FROM lote WHERE id=?", id); }
    public void atualizarLote(long id, Map<String, Object> dados) { int estoque = ((Number) dados.get("quantidadeEstoque")).intValue(); Map<String, Object> lote = lote(id); int total = estoque + ((Number) lote.get("quantidade_reservada")).intValue() + ((Number) lote.get("quantidade_vendida")).intValue(); db.update("UPDATE lote SET data_validade=?, quantidade_total=?, quantidade_disponivel=? WHERE id=?", dados.get("dataValidade"), total, estoque, id); }
    public void reservar(long loteId, long pedidoId, int quantidade) { db.update("UPDATE lote SET quantidade_disponivel=quantidade_disponivel-?, quantidade_reservada=quantidade_reservada+? WHERE id=?", quantidade, quantidade, loteId); db.update("INSERT INTO reserva_estoque (pedido_id,lote_id,quantidade) VALUES (?,?,?)", pedidoId, loteId, quantidade); }
    public List<Map<String, Object>> reservas(long pedidoId) { return db.queryForList("SELECT lote_id, quantidade FROM reserva_estoque WHERE pedido_id=?", pedidoId); }
    public void devolverReserva(long loteId, int quantidade, boolean vendido) { if (vendido) db.update("UPDATE lote SET quantidade_disponivel=quantidade_disponivel+?, quantidade_vendida=quantidade_vendida-? WHERE id=?", quantidade, quantidade, loteId); else db.update("UPDATE lote SET quantidade_disponivel=quantidade_disponivel+?, quantidade_reservada=quantidade_reservada-? WHERE id=?", quantidade, quantidade, loteId); }
}
