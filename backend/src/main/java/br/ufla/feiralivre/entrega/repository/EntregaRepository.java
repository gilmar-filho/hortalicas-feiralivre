package br.ufla.feiralivre.entrega.repository;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EntregaRepository {
    private final JdbcTemplate db;

    public EntregaRepository(JdbcTemplate db) { this.db = db; }

    public List<Map<String, Object>> listarLocais() {
        return db.queryForList("SELECT * FROM local_retirada ORDER BY nome");
    }

    public List<Map<String, Object>> listarHorarios(long localId) {
        return db.queryForList("SELECT * FROM horario_retirada WHERE local_retirada_id = ?", localId);
    }

    public Map<String, Object> criarLocal(Map<String, Object> dados) {
        db.update("INSERT INTO local_retirada (usuario_id,nome,endereco) VALUES (1,?,?)", dados.get("nome"), dados.get("endereco"));
        return db.queryForMap("SELECT * FROM local_retirada WHERE id=last_insert_rowid()");
    }

    public Map<String, Object> criarHorario(Map<String, Object> dados) {
        db.update("INSERT INTO horario_retirada (local_retirada_id,dia_semana,hora_inicio,hora_fim) VALUES (?,?,?,?)", dados.get("localId"), dados.get("diaSemana"), dados.get("horaInicio"), dados.get("horaFim"));
        return db.queryForMap("SELECT * FROM horario_retirada WHERE id=last_insert_rowid()");
    }

    public long criarLocalSeNecessario(Map<String, Object> dados) {
        Object localId = dados.get("localId");
        if (localId != null && !String.valueOf(localId).isBlank()) return Long.parseLong(String.valueOf(localId));
        if (String.valueOf(dados.getOrDefault("localNome", "")).isBlank() || String.valueOf(dados.getOrDefault("localEndereco", "")).isBlank()) return 1L;
        db.update("INSERT INTO local_retirada (usuario_id,nome,endereco) VALUES (1,?,?)", dados.get("localNome"), dados.get("localEndereco"));
        return db.queryForObject("SELECT last_insert_rowid()", Long.class);
    }
}
