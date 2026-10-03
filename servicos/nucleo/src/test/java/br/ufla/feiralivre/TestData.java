package br.ufla.feiralivre;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Cada teste cria os próprios dados em vez de depender dos ids fixos de
 * data.sql, para que a ordem de execução e o número de execuções não
 * mudem o resultado.
 */
public final class TestData {

    private static final AtomicLong SEQ = new AtomicLong();

    private TestData() { }

    /**
     * E-mail é UNIQUE em usuario: um literal fixo faria a suíte passar na
     * primeira execução e quebrar na segunda sobre o mesmo banco.
     */
    public static String email(String prefixo) {
        return prefixo + "-" + SEQ.incrementAndGet() + "-" + System.nanoTime() + "@teste.local";
    }

    public static long usuario(JdbcTemplate db, String nome, String email, String senha) {
        db.update("INSERT INTO usuario (nome, email, senha) VALUES (?, ?, ?)", nome, email, senha);
        return db.queryForObject("SELECT last_insert_rowid()", Long.class);
    }

    public static long id() {
        return System.currentTimeMillis() * 1_000L + SEQ.incrementAndGet() % 1_000L;
    }

    public static LocalDate hoje() {
        return LocalDate.now(ZoneId.of("America/Sao_Paulo"));
    }
}
