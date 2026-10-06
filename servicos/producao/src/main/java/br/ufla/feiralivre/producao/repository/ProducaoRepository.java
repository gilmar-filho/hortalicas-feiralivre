package br.ufla.feiralivre.producao.repository;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProducaoRepository {
    private final JdbcTemplate db;

    public ProducaoRepository(JdbcTemplate db) { this.db = db; }

    public List<Map<String, Object>> listar(String busca, Long usuarioId) {
        String filtroDono = usuarioId == null ? "" : " AND p.usuario_id = " + usuarioId;
        String sql = "SELECT p.id, p.usuario_id, p.nome, p.categoria, p.descricao, p.foto, p.preco, p.ativo, p.data_cadastro, COALESCE(SUM(CASE WHEN l.data_validade >= date('now') THEN l.quantidade_disponivel ELSE 0 END), 0) estoque, MIN(CASE WHEN l.data_validade >= date('now') AND l.quantidade_disponivel > 0 THEN l.data_validade END) validade, MAX(CASE WHEN l.data_validade >= date('now') AND l.quantidade_disponivel > 0 THEN l.data_validade END) validade_maxima, (SELECT l2.id FROM lote l2 WHERE l2.produto_id=p.id AND l2.data_validade >= date('now') ORDER BY l2.data_validade, l2.id LIMIT 1) lote_id, (SELECT l2.quantidade_disponivel FROM lote l2 WHERE l2.produto_id=p.id AND l2.data_validade >= date('now') ORDER BY l2.data_validade, l2.id LIMIT 1) quantidade_disponivel, (SELECT l2.data_validade FROM lote l2 WHERE l2.produto_id=p.id AND l2.data_validade >= date('now') ORDER BY l2.data_validade, l2.id LIMIT 1) data_validade FROM produto p LEFT JOIN lote l ON l.produto_id = p.id WHERE p.ativo = 1" + filtroDono + " AND lower(p.nome) LIKE lower(?) GROUP BY p.id ORDER BY p.nome";
        return db.queryForList(sql, "%" + busca + "%");
    }

    public List<Map<String, Object>> produtoAtivo(long id) { return db.queryForList("SELECT * FROM produto WHERE id=? AND ativo=1", id); }
    public List<Map<String, Object>> lotesDisponiveis(long produtoId, String data) { return db.queryForList("SELECT * FROM lote WHERE produto_id=? AND data_validade >= ? AND quantidade_disponivel > 0 ORDER BY data_validade, id", produtoId, data); }
    public Map<String, Object> criarProduto(Map<String, Object> dados) {
        db.update("INSERT INTO produto (usuario_id,nome,categoria,descricao,foto,preco,ativo,data_cadastro) VALUES (?,?,?,?,?,?,?,?)", dados.get("usuarioId"), dados.get("nome"), dados.getOrDefault("categoria", "Hortaliças"), dados.get("descricao"), dados.get("foto"), dados.get("preco"), dados.get("ativo"), dados.get("dataCadastro"));
        return db.queryForMap("SELECT * FROM produto WHERE id=last_insert_rowid()");
    }
    public Map<String, Object> produto(long id) { return db.queryForMap("SELECT * FROM produto WHERE id=?", id); }
    public void atualizarProduto(long id, Map<String, Object> dados) { db.update("UPDATE produto SET usuario_id=?, nome=?, categoria=?, descricao=?, foto=?, preco=?, ativo=?, data_cadastro=? WHERE id=?", dados.get("usuarioId"), dados.get("nome"), dados.getOrDefault("categoria", "Hortaliças"), dados.get("descricao"), dados.get("foto"), dados.get("preco"), dados.get("ativo"), dados.get("dataCadastro"), id); }
    public void desativar(long id) { db.update("UPDATE produto SET ativo=0 WHERE id=?", id); }
    public Map<String, Object> criarLote(long produtoId, Map<String, Object> dados) { db.update("INSERT INTO lote (produto_id,origem,data_producao,data_validade,quantidade_total,quantidade_disponivel) VALUES (?,?,COALESCE(?,date('now')),?,?,?)", produtoId, dados.getOrDefault("origem", "Produção própria"), dados.getOrDefault("dataProducao", dados.get("dataCadastro")), dados.get("dataValidade"), dados.getOrDefault("quantidadeEstoque", dados.get("quantidade")), dados.getOrDefault("quantidadeEstoque", dados.get("quantidade"))); return db.queryForMap("SELECT * FROM lote WHERE id = last_insert_rowid()"); }
    public Map<String, Object> lote(long id) { return db.queryForMap("SELECT quantidade_reservada, quantidade_vendida FROM lote WHERE id=?", id); }
    public void atualizarLote(long id, Map<String, Object> dados) { int estoque = ((Number) dados.get("quantidadeEstoque")).intValue(); Map<String, Object> lote = lote(id); int total = estoque + ((Number) lote.get("quantidade_reservada")).intValue() + ((Number) lote.get("quantidade_vendida")).intValue(); db.update("UPDATE lote SET data_validade=?, quantidade_total=?, quantidade_disponivel=? WHERE id=?", dados.get("dataValidade"), total, estoque, id); }
    public void reservar(long loteId, long pedidoId, int quantidade) { db.update("UPDATE lote SET quantidade_disponivel=quantidade_disponivel-?, quantidade_reservada=quantidade_reservada+? WHERE id=?", quantidade, quantidade, loteId); db.update("INSERT INTO reserva_estoque (pedido_id,lote_id,quantidade) VALUES (?,?,?)", pedidoId, loteId, quantidade); }
    public List<Map<String, Object>> reservasAtivas(long pedidoId) { return db.queryForList("SELECT id, lote_id, quantidade FROM reserva_estoque WHERE pedido_id=? AND status='ATIVA'", pedidoId); }
    public void devolver(long reservaId, long loteId, int quantidade) { db.update("UPDATE lote SET quantidade_disponivel=quantidade_disponivel+?, quantidade_reservada=quantidade_reservada-? WHERE id=?", quantidade, quantidade, loteId); db.update("UPDATE reserva_estoque SET status='DEVOLVIDA' WHERE id=?", reservaId); }
    public void vender(long reservaId, long loteId, int quantidade) { db.update("UPDATE lote SET quantidade_reservada=quantidade_reservada-?, quantidade_vendida=quantidade_vendida+? WHERE id=?", quantidade, quantidade, loteId); db.update("UPDATE reserva_estoque SET status='VENDIDA' WHERE id=?", reservaId); }
    public int registrarProcessamento(String eventoId, String tipo) { return db.update("INSERT OR IGNORE INTO evento_processado (evento_id,tipo) VALUES (?,?)", eventoId, tipo); }
    public int venderReservas(long pedidoId) {
        int confirmadas = 0;
        for (Map<String, Object> reserva : reservasAtivas(pedidoId)) {
            vender(((Number) reserva.get("id")).longValue(), ((Number) reserva.get("lote_id")).longValue(), ((Number) reserva.get("quantidade")).intValue());
            confirmadas++;
        }
        return confirmadas;
    }
    public List<Map<String, Object>> reservasDosPedidos(List<Long> pedidoIds) {
        String marcadores = String.join(",", Collections.nCopies(pedidoIds.size(), "?"));
        return db.queryForList("SELECT re.pedido_id pedidoId, re.lote_id loteId, re.quantidade, l.origem, l.data_validade dataValidade, re.status FROM reserva_estoque re JOIN lote l ON l.id=re.lote_id WHERE re.pedido_id IN (" + marcadores + ") ORDER BY re.pedido_id, re.id", pedidoIds.toArray());
    }
}
