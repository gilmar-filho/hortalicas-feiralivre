package br.ufla.feiralivre.entrega;

import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/retiradas")
public class EntregaController {
    private final JdbcTemplate db;
    public EntregaController(JdbcTemplate db) { this.db = db; }
    @GetMapping("/locais")
    public List<Map<String, Object>> locais() { return db.queryForList("SELECT * FROM local_retirada ORDER BY nome"); }
    @GetMapping("/horarios")
    public List<Map<String, Object>> horarios(@RequestParam long localId) { return db.queryForList("SELECT * FROM horario_retirada WHERE local_retirada_id = ?", localId); }

    @PostMapping("/locais")
    public Map<String, Object> criarLocal(@RequestBody Map<String, Object> dados) {
        db.update("INSERT INTO local_retirada (usuario_id,nome,endereco) VALUES (1,?,?)", dados.get("nome"), dados.get("endereco"));
        return db.queryForMap("SELECT * FROM local_retirada WHERE id=last_insert_rowid()");
    }

    @PostMapping("/horarios")
    public Map<String, Object> criarHorario(@RequestBody Map<String, Object> dados) {
        db.update("INSERT INTO horario_retirada (local_retirada_id,dia_semana,hora_inicio,hora_fim) VALUES (?,?,?,?)", dados.get("localId"), dados.get("diaSemana"), dados.get("horaInicio"), dados.get("horaFim"));
        return db.queryForMap("SELECT * FROM horario_retirada WHERE id=last_insert_rowid()");
    }
}
