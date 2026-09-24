package br.ufla.feiralivre.usuario.repository;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UsuarioRepository {
    private final JdbcTemplate db;
    public UsuarioRepository(JdbcTemplate db) { this.db = db; }
    public Map<String, Object> buscar(long id) { return db.queryForMap("SELECT * FROM usuario WHERE id=?", id); }
    public Map<String, Object> criar(Map<String, Object> dados) { db.update("INSERT INTO usuario (nome,email,senha) VALUES (?,?,?)", dados.get("nome"), dados.get("email"), dados.get("senha")); return buscar(db.queryForObject("SELECT last_insert_rowid()", Long.class)); }
    public List<Map<String, Object>> autenticar(Map<String, Object> dados) { return db.queryForList("SELECT id,nome,email FROM usuario WHERE email=? AND senha=?", dados.get("email"), dados.get("senha")); }
    public void migrarSenha() { try { db.execute("ALTER TABLE usuario ADD COLUMN senha TEXT NOT NULL DEFAULT '123456'"); } catch (Exception ignored) { } }
}
