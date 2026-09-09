package br.ufla.feiralivre.producao;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.annotation.PostConstruct;

@RestController
@RequestMapping("/api/produtos")
public class ProducaoController {
    private final JdbcTemplate db;
    public ProducaoController(JdbcTemplate db) { this.db = db; }

    @PostConstruct
    public void migrarLocalDoProduto() {
        try { db.execute("ALTER TABLE produto ADD COLUMN local_retirada_id INTEGER"); } catch (DataAccessException ignored) { }
        try { db.execute("ALTER TABLE produto ADD COLUMN categoria TEXT NOT NULL DEFAULT 'Hortaliças'"); } catch (DataAccessException ignored) { }
    }

    @GetMapping
    public List<Map<String, Object>> listar(@RequestParam(defaultValue = "") String busca, @RequestParam(required = false) Long usuarioId) {
        String filtroDono = usuarioId == null ? "" : " AND p.usuario_id = " + usuarioId;
        return db.queryForList("SELECT p.id, p.usuario_id, p.nome, p.categoria, p.descricao, p.foto, p.preco, p.ativo, p.data_cadastro, p.local_retirada_id, r.nome local_nome, r.endereco local_endereco, COALESCE(SUM(CASE WHEN l.data_validade >= date('now') THEN l.quantidade_disponivel ELSE 0 END), 0) estoque, MIN(CASE WHEN l.data_validade >= date('now') AND l.quantidade_disponivel > 0 THEN l.data_validade END) validade, (SELECT l2.id FROM lote l2 WHERE l2.produto_id=p.id AND l2.data_validade >= date('now') ORDER BY l2.data_validade, l2.id LIMIT 1) lote_id, (SELECT l2.quantidade_disponivel FROM lote l2 WHERE l2.produto_id=p.id AND l2.data_validade >= date('now') ORDER BY l2.data_validade, l2.id LIMIT 1) quantidade_disponivel, (SELECT l2.data_validade FROM lote l2 WHERE l2.produto_id=p.id AND l2.data_validade >= date('now') ORDER BY l2.data_validade, l2.id LIMIT 1) data_validade FROM produto p LEFT JOIN lote l ON l.produto_id = p.id LEFT JOIN local_retirada r ON r.id=p.local_retirada_id WHERE p.ativo = 1" + filtroDono + " AND lower(p.nome) LIKE lower(?) GROUP BY p.id ORDER BY p.nome", "%" + busca + "%");
    }

    @PostMapping
    @Transactional
    public Map<String, Object> criar(@RequestBody Map<String, Object> dados) {
        long localId = salvarLocal(dados);
        db.update("INSERT INTO produto (usuario_id,nome,categoria,descricao,foto,preco,ativo,data_cadastro,local_retirada_id) VALUES (?,?,?,?,?,?,?,?,?)", dados.get("usuarioId"), dados.get("nome"), dados.getOrDefault("categoria", "Hortaliças"), dados.get("descricao"), dados.get("foto"), dados.get("preco"), dados.get("ativo"), dados.get("dataCadastro"), localId);
        long produtoId = Objects.requireNonNull(db.queryForObject("SELECT last_insert_rowid()", Long.class));
        criarLote(produtoId, dados);
        return db.queryForMap("SELECT * FROM produto WHERE id=?", produtoId);
    }

    @PutMapping("/{produtoId}")
    @Transactional
    public Map<String, Object> editar(@PathVariable long produtoId, @RequestBody Map<String, Object> dados) {
        long localId = salvarLocal(dados);
        db.update("UPDATE produto SET usuario_id=?, nome=?, categoria=?, descricao=?, foto=?, preco=?, ativo=?, data_cadastro=?, local_retirada_id=? WHERE id=?", dados.get("usuarioId"), dados.get("nome"), dados.getOrDefault("categoria", "Hortaliças"), dados.get("descricao"), dados.get("foto"), dados.get("preco"), dados.get("ativo"), dados.get("dataCadastro"), localId, produtoId);
        String loteId = String.valueOf(dados.getOrDefault("loteId", "")).trim();
        if (!loteId.isEmpty() && dados.get("quantidadeEstoque") != null) {
            atualizarLote(Long.parseLong(loteId), dados);
        } else if (dados.get("quantidadeEstoque") != null) {
            criarLote(produtoId, dados);
        }
        return db.queryForMap("SELECT * FROM produto WHERE id=?", produtoId);
    }

    @PatchMapping("/{produtoId}/desativar")
    public void desativar(@PathVariable long produtoId) {
        db.update("UPDATE produto SET ativo=0 WHERE id=?", produtoId);
    }

    @PostMapping("/{produtoId}/lotes")
    public Map<String, Object> criarLote(@PathVariable long produtoId, @RequestBody Map<String, Object> dados) {
        validarDadosDoLote(dados);
        db.update("INSERT INTO lote (produto_id,origem,data_producao,data_validade,quantidade_total,quantidade_disponivel) VALUES (?,?,?,?,?,?)", produtoId, dados.getOrDefault("origem", "Produção própria"), dados.getOrDefault("dataProducao", dados.get("dataCadastro")), dados.get("dataValidade"), dados.getOrDefault("quantidadeEstoque", dados.get("quantidade")), dados.getOrDefault("quantidadeEstoque", dados.get("quantidade")));
        return db.queryForMap("SELECT * FROM lote WHERE id = last_insert_rowid()");
    }

    @PutMapping("/{produtoId}/lotes/{loteId}")
    public Map<String, Object> atualizarLote(@PathVariable long loteId, @RequestBody Map<String, Object> dados) {
        validarDadosDoLote(dados);
        int estoque = ((Number) dados.get("quantidadeEstoque")).intValue();
        Map<String, Object> lote = db.queryForMap("SELECT quantidade_reservada, quantidade_vendida FROM lote WHERE id=?", loteId);
        int total = estoque + ((Number) lote.get("quantidade_reservada")).intValue() + ((Number) lote.get("quantidade_vendida")).intValue();
        db.update("UPDATE lote SET data_validade=?, quantidade_total=?, quantidade_disponivel=? WHERE id=?", dados.get("dataValidade"), total, estoque, loteId);
        return db.queryForMap("SELECT * FROM lote WHERE id=?", loteId);
    }

    private void validarDadosDoLote(Map<String, Object> dados) {
        if (dados.get("dataValidade") == null || String.valueOf(dados.get("dataValidade")).isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe a data de vencimento do lote");
        }
        Object quantidade = dados.getOrDefault("quantidadeEstoque", dados.get("quantidade"));
        if (quantidade == null || Integer.parseInt(String.valueOf(quantidade)) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe uma quantidade de estoque válida");
        }
    }

    private long salvarLocal(Map<String, Object> dados) {
        Object localId = dados.get("localId");
        if (localId != null && !String.valueOf(localId).isBlank()) return Long.parseLong(String.valueOf(localId));
        if (String.valueOf(dados.getOrDefault("localNome", "")).isBlank() || String.valueOf(dados.getOrDefault("localEndereco", "")).isBlank()) {
            return 1L;
        }
        db.update("INSERT INTO local_retirada (usuario_id,nome,endereco) VALUES (1,?,?)", dados.get("localNome"), dados.get("localEndereco"));
        return Objects.requireNonNull(db.queryForObject("SELECT last_insert_rowid()", Long.class));
    }
}
