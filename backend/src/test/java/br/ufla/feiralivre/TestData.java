package br.ufla.feiralivre;

import java.time.LocalDate;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Cada teste cria os próprios dados em vez de depender dos ids fixos de
 * data.sql, para que a ordem de execução e o número de execuções não
 * mudem o resultado.
 */
public final class TestData {

    private static final java.util.concurrent.atomic.AtomicLong SEQ = new java.util.concurrent.atomic.AtomicLong();

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

    public static long produto(JdbcTemplate db, long vendedorId, String nome, double preco) {
        return produto(db, vendedorId, nome, preco, true);
    }

    public static long produto(JdbcTemplate db, long vendedorId, String nome, double preco, boolean ativo) {
        db.update("INSERT INTO produto (usuario_id, nome, descricao, preco, ativo, data_cadastro) "
            + "VALUES (?, ?, 'Produto de teste', ?, ?, date('now'))",
            vendedorId, nome, preco, ativo ? 1 : 0);
        return db.queryForObject("SELECT last_insert_rowid()", Long.class);
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

    /**
     * Entrega referencia o pedido só por id, sem FK: os testes do contexto
     * reservam vagas sem precisar criar um pedido de verdade.
     */
    public static long pedidoFicticio() {
        return System.currentTimeMillis() * 1_000L + SEQ.incrementAndGet() % 1_000L;
    }

    public static long lote(JdbcTemplate db, long produtoId, int diasAteVencer, int quantidade) {
        return lote(db, produtoId, diasAteVencer, quantidade, "Sítio de teste");
    }

    public static long lote(JdbcTemplate db, long produtoId, int diasAteVencer, int quantidade, String origem) {
        db.update("INSERT INTO lote (produto_id, origem, data_producao, data_validade, "
            + "quantidade_total, quantidade_disponivel) "
            + "VALUES (?, ?, date('now'), date('now', ?), ?, ?)",
            produtoId, origem, (diasAteVencer >= 0 ? "+" : "") + diasAteVencer + " day", quantidade, quantidade);
        return db.queryForObject("SELECT last_insert_rowid()", Long.class);
    }
}
