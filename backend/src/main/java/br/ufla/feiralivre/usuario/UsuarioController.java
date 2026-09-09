package br.ufla.feiralivre.usuario;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.annotation.PostConstruct;

@RestController
@RequestMapping("/api/usuario")
public class UsuarioController {
    private final JdbcTemplate db;
    public UsuarioController(JdbcTemplate db) { this.db = db; }
    @PostConstruct
    public void migrarSenha() { try { db.execute("ALTER TABLE usuario ADD COLUMN senha TEXT NOT NULL DEFAULT '123456'"); } catch (Exception ignored) { } }
    @GetMapping("/atual")
    public Map<String,Object> atual(@RequestParam(defaultValue = "1") long usuarioId) { return db.queryForMap("SELECT * FROM usuario WHERE id=?", usuarioId); }

    @PostMapping
    public Map<String, Object> criar(@RequestBody Map<String, Object> dados) {
        db.update("INSERT INTO usuario (nome,email,senha) VALUES (?,?,?)", dados.get("nome"), dados.get("email"), dados.get("senha"));
        return db.queryForMap("SELECT * FROM usuario WHERE id=last_insert_rowid()");
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, Object> dados) {
        List<Map<String, Object>> usuarios = db.queryForList("SELECT id,nome,email FROM usuario WHERE email=? AND senha=?", dados.get("email"), dados.get("senha"));
        if (usuarios.isEmpty()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "E-mail ou senha inválidos");
        return usuarios.get(0);
    }
}
