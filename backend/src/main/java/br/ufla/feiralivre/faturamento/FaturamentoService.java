package br.ufla.feiralivre.faturamento;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class FaturamentoService {
    private final JdbcTemplate db;
    public FaturamentoService(JdbcTemplate db) { this.db = db; }
    public long criarFatura(long pedidoId, double valor) {
        db.update("INSERT INTO fatura (pedido_id,valor,status) VALUES (?,?, 'PENDENTE')", pedidoId, valor);
        return db.queryForObject("SELECT last_insert_rowid()", Long.class);
    }
}
