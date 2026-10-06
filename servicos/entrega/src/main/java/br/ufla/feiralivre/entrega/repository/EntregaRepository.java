package br.ufla.feiralivre.entrega.repository;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EntregaRepository {
    private final JdbcTemplate db;

    public EntregaRepository(JdbcTemplate db) { this.db = db; }

    public List<Map<String, Object>> listarLocais(long vendedorId) {
        return db.queryForList("SELECT * FROM local_retirada WHERE usuario_id=? ORDER BY nome", vendedorId);
    }

    public List<Map<String, Object>> local(long id) {
        return db.queryForList("SELECT * FROM local_retirada WHERE id=?", id);
    }

    public Map<String, Object> criarLocal(long vendedorId, String nome, String endereco) {
        db.update("INSERT INTO local_retirada (usuario_id,nome,endereco) VALUES (?,?,?)", vendedorId, nome, endereco);
        return db.queryForMap("SELECT * FROM local_retirada WHERE id=last_insert_rowid()");
    }

    public List<Map<String, Object>> listarHorarios(long vendedorId) {
        return db.queryForList("SELECT h.*, l.nome local_nome, l.endereco local_endereco FROM horario_retirada h JOIN local_retirada l ON l.id=h.local_retirada_id WHERE l.usuario_id=? ORDER BY h.dia_semana, h.hora_inicio", vendedorId);
    }

    public List<Map<String, Object>> horario(long id) {
        return db.queryForList("SELECT h.*, l.usuario_id, l.nome local_nome, l.endereco local_endereco FROM horario_retirada h JOIN local_retirada l ON l.id=h.local_retirada_id WHERE h.id=?", id);
    }

    public Map<String, Object> criarHorario(long localId, int diaSemana, String horaInicio, String horaFim, int capacidade) {
        db.update("INSERT INTO horario_retirada (local_retirada_id,dia_semana,hora_inicio,hora_fim,capacidade_atendimento) VALUES (?,?,?,?,?)", localId, diaSemana, horaInicio, horaFim, capacidade);
        return db.queryForMap("SELECT * FROM horario_retirada WHERE id=last_insert_rowid()");
    }

    public void atualizarCapacidade(long id, int capacidade) {
        db.update("UPDATE horario_retirada SET capacidade_atendimento=? WHERE id=?", capacidade, id);
    }

    public int ocupacao(long horarioId, String data) {
        return db.queryForObject("SELECT COUNT(DISTINCT comprador_id) FROM reserva_atendimento WHERE horario_retirada_id=? AND data_retirada=? AND status='ATIVA'", Integer.class, horarioId, data);
    }

    public boolean compradorJaReservado(long horarioId, String data, long compradorId) {
        return db.queryForObject("SELECT COUNT(*) FROM reserva_atendimento WHERE horario_retirada_id=? AND data_retirada=? AND comprador_id=? AND status='ATIVA'", Integer.class, horarioId, data, compradorId) > 0;
    }

    public void reservar(long pedidoId, long compradorId, long horarioId, String data) {
        db.update("INSERT INTO reserva_atendimento (pedido_id,comprador_id,horario_retirada_id,data_retirada,status) VALUES (?,?,?,?,'ATIVA')", pedidoId, compradorId, horarioId, data);
    }

    public void liberar(long pedidoId) {
        db.update("UPDATE reserva_atendimento SET status='LIBERADA' WHERE pedido_id=? AND status='ATIVA'", pedidoId);
    }

    public List<Map<String, Object>> ultimaReserva(long pedidoId) {
        return db.queryForList("SELECT * FROM reserva_atendimento WHERE pedido_id=? ORDER BY id DESC LIMIT 1", pedidoId);
    }

    public int confirmarReserva(long pedidoId) {
        return db.update("UPDATE reserva_atendimento SET status='RETIRADA' WHERE pedido_id=? AND status='ATIVA'", pedidoId);
    }

    public void inserirOutbox(String eventoId, String tipo, long agregadoId, String payload) {
        db.update("INSERT INTO outbox_evento (evento_id,tipo,agregado_id,payload) VALUES (?,?,?,?)", eventoId, tipo, agregadoId, payload);
    }

    public List<Map<String, Object>> outboxPendente(int limite) {
        return db.queryForList("SELECT evento_id, payload FROM outbox_evento WHERE status='PENDENTE' ORDER BY id LIMIT ?", limite);
    }

    public void marcarPublicado(String eventoId) {
        db.update("UPDATE outbox_evento SET status='PUBLICADO', publicado_em=datetime('now'), ultimo_erro=NULL WHERE evento_id=?", eventoId);
    }

    public void registrarErroPublicacao(String eventoId, String erro) {
        db.update("UPDATE outbox_evento SET tentativas=tentativas+1, ultimo_erro=? WHERE evento_id=?", erro, eventoId);
    }

    public List<Map<String, Object>> reservasDosPedidos(List<Long> pedidoIds) {
        String marcadores = String.join(",", Collections.nCopies(pedidoIds.size(), "?"));
        return db.queryForList("SELECT r.pedido_id pedidoId, r.status, r.data_retirada data, h.hora_inicio horaInicio, h.hora_fim horaFim, l.nome localNome, l.endereco localEndereco FROM reserva_atendimento r JOIN horario_retirada h ON h.id=r.horario_retirada_id JOIN local_retirada l ON l.id=h.local_retirada_id WHERE r.id IN (SELECT MAX(id) FROM reserva_atendimento WHERE pedido_id IN (" + marcadores + ") GROUP BY pedido_id) ORDER BY r.pedido_id", pedidoIds.toArray());
    }
}
