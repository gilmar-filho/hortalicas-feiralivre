package br.ufla.feiralivre;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.jdbc.core.JdbcTemplate;

public final class TestData {

    private static final AtomicLong SEQ = new AtomicLong();

    private TestData() { }

    /**
     * Produção referencia vendedor e pedido só por id: o teste inventa ids
     * que não colidem entre execuções sobre o mesmo banco.
     */
    public static long id() {
        return System.currentTimeMillis() * 1_000L + SEQ.incrementAndGet() % 1_000L;
    }

    public static LocalDate hoje() {
        return LocalDate.now(ZoneId.of("America/Sao_Paulo"));
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
