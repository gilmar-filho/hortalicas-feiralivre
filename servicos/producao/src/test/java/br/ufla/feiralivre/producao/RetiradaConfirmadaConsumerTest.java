package br.ufla.feiralivre.producao;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.ufla.feiralivre.TestData;
import br.ufla.feiralivre.mensageria.RetiradaConfirmadaConsumer;
import br.ufla.feiralivre.producao.service.ProducaoService;

/**
 * O desafio D4 do lado consumidor: o broker entrega at-least-once, então o
 * mesmo evento chega mais de uma vez — e só pode baixar o estoque uma vez.
 */
@SpringBootTest
public class RetiradaConfirmadaConsumerTest {

    @Autowired
    private RetiradaConfirmadaConsumer consumidor;

    @Autowired
    private ProducaoService producao;

    @Autowired
    private JdbcTemplate db;

    @Autowired
    private ObjectMapper json;

    @Test
    public void eventoDeveVenderAReservaEBaixarOReservadoDoLote() throws Exception {
        long lote = loteComReserva(4);
        long pedido = pedidoDaReserva;
        String eventoId = UUID.randomUUID().toString();

        consumidor.consumir(payload(eventoId, pedido));

        assertEquals("VENDIDA", statusDaReserva(pedido));
        assertEquals(0, quantidadeDoLote(lote, "quantidade_reservada"));
        assertEquals(4, quantidadeDoLote(lote, "quantidade_vendida"));
        assertEquals(6, quantidadeDoLote(lote, "quantidade_disponivel"));
        assertEquals(1, processado(eventoId));
    }

    @Test
    public void eventoRepetidoNaoVendeDuasVezes() throws Exception {
        long lote = loteComReserva(4);
        long pedido = pedidoDaReserva;
        String eventoId = UUID.randomUUID().toString();
        String payload = payload(eventoId, pedido);

        consumidor.consumir(payload);
        consumidor.consumir(payload);

        assertEquals("VENDIDA", statusDaReserva(pedido));
        assertEquals(0, quantidadeDoLote(lote, "quantidade_reservada"));
        assertEquals(4, quantidadeDoLote(lote, "quantidade_vendida"));
        assertEquals(1, processado(eventoId), "A duplicata é reconhecida pelo id e não reprocessa");
    }

    @Test
    public void eventoNovoParaReservaJaVendidaNaoVendeDeNovo() throws Exception {
        long lote = loteComReserva(4);
        long pedido = pedidoDaReserva;
        String primeiro = UUID.randomUUID().toString();
        String segundo = UUID.randomUUID().toString();

        consumidor.consumir(payload(primeiro, pedido));
        consumidor.consumir(payload(segundo, pedido));

        assertEquals(4, quantidadeDoLote(lote, "quantidade_vendida"));
        assertEquals(1, processado(segundo), "O evento é registrado mesmo sem ter o que fazer");
    }

    @Test
    public void eventoDeReservaDevolvidaNaoVendeNada() throws Exception {
        long lote = loteComReserva(4);
        long pedido = pedidoDaReserva;
        String eventoId = UUID.randomUUID().toString();
        producao.devolver(pedido);

        consumidor.consumir(payload(eventoId, pedido));

        assertEquals("DEVOLVIDA", statusDaReserva(pedido));
        assertEquals(0, quantidadeDoLote(lote, "quantidade_reservada"));
        assertEquals(0, quantidadeDoLote(lote, "quantidade_vendida"));
        assertEquals(10, quantidadeDoLote(lote, "quantidade_disponivel"));
        assertEquals(1, processado(eventoId));
    }

    @Test
    public void eventoDePedidoDesconhecidoEhRegistradoSemTravarAFila() throws Exception {
        String eventoId = UUID.randomUUID().toString();

        consumidor.consumir(payload(eventoId, TestData.id()));

        assertEquals(1, processado(eventoId));
    }

    @Test
    public void payloadIlegivelEhDescartadoSemEfeitoNemSeRegistra() {
        int antes = db.queryForObject("SELECT COUNT(*) FROM evento_processado", Integer.class);

        consumidor.consumir("{isso nao e json");

        assertEquals(antes, db.queryForObject("SELECT COUNT(*) FROM evento_processado", Integer.class));
    }

    private long pedidoDaReserva;

    private long loteComReserva(int quantidade) {
        long produto = TestData.produto(db, TestData.id(), "Alface (retirada)", 4.00);
        long lote = TestData.lote(db, produto, 10, 10);
        pedidoDaReserva = TestData.id();
        producao.reservar(pedidoDaReserva, produto, quantidade, TestData.hoje().plusDays(1).toString());
        return lote;
    }

    private String payload(String eventoId, long pedido) throws JsonProcessingException {
        return json.writeValueAsString(Map.of(
            "eventoId", eventoId,
            "tipo", "RetiradaConfirmada",
            "ocorridoEm", Instant.now().toString(),
            "dados", Map.of("pedidoId", pedido, "dataRetirada", TestData.hoje().plusDays(1).toString())));
    }

    private String statusDaReserva(long pedido) {
        return db.queryForObject(
            "SELECT status FROM reserva_estoque WHERE pedido_id=? ORDER BY id DESC LIMIT 1", String.class, pedido);
    }

    private int quantidadeDoLote(long lote, String coluna) {
        return db.queryForObject("SELECT " + coluna + " FROM lote WHERE id=?", Integer.class, lote);
    }

    private int processado(String eventoId) {
        return db.queryForObject("SELECT COUNT(*) FROM evento_processado WHERE evento_id=?", Integer.class, eventoId);
    }
}
