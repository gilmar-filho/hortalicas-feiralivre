package br.ufla.feiralivre;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.jdbc.core.JdbcTemplate;

public final class TestData {

    private static final AtomicLong SEQ = new AtomicLong();

    private TestData() { }

    /**
     * Entrega referencia vendedor, comprador e pedido só por id: o teste
     * inventa ids que não colidem entre execuções sobre o mesmo banco.
     */
    public static long id() {
        return System.currentTimeMillis() * 1_000L + SEQ.incrementAndGet() % 1_000L;
    }

    public static long pedidoFicticio() {
        return id();
    }

    public static long localRetirada(JdbcTemplate db, long vendedorId, String nome) {
        db.update("INSERT INTO local_retirada (usuario_id, nome, endereco) VALUES (?, ?, 'Endereço de teste')",
            vendedorId, nome);
        return db.queryForObject("SELECT last_insert_rowid()", Long.class);
    }

    public static long janela(JdbcTemplate db, long localId, LocalDate data, int capacidade) {
        return janela(db, localId, data, capacidade, "00:00", "23:59");
    }

    /**
     * A janela nasce no dia da semana da data informada, para o teste não
     * depender do dia em que roda.
     */
    public static long janela(JdbcTemplate db, long localId, LocalDate data, int capacidade, String horaInicio, String horaFim) {
        db.update("INSERT INTO horario_retirada (local_retirada_id, dia_semana, hora_inicio, hora_fim, capacidade_atendimento) "
            + "VALUES (?, ?, ?, ?, ?)",
            localId, data.getDayOfWeek().getValue() % 7, horaInicio, horaFim, capacidade);
        return db.queryForObject("SELECT last_insert_rowid()", Long.class);
    }
}
