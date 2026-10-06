package br.ufla.feiralivre.entrega;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.ufla.feiralivre.TestData;
import br.ufla.feiralivre.contrato.ValidacaoDeContrato;
import br.ufla.feiralivre.entrega.service.EntregaService;

/**
 * O desafio D4 do lado publicador: confirmar a retirada muda a reserva e grava
 * o evento RetiradaConfirmada no outbox na mesma transação, e repetir a
 * confirmação não publica um segundo evento.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class RetiradaConfirmadaIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private EntregaService entrega;

    @Autowired
    private JdbcTemplate db;

    @Autowired
    private ObjectMapper json;

    @BeforeEach
    public void validarContrato() {
        ValidacaoDeContrato.instalar(restTemplate, "../../contracts/entrega.yaml");
    }

    @Test
    public void confirmarDeveMarcarAReservaEGerarEventoNoOutbox() throws Exception {
        long pedido = reservaAtiva();

        ResponseEntity<Map> resposta = confirmar(pedido);

        assertEquals(HttpStatus.OK, resposta.getStatusCode());
        assertEquals("RETIRADA", resposta.getBody().get("status"));
        Map<String, Object> evento = unicoEventoDo(pedido);
        assertEquals("RetiradaConfirmada", evento.get("tipo"));
        assertEquals("PENDENTE", evento.get("status"));
        JsonNode payload = json.readTree(String.valueOf(evento.get("payload")));
        assertEquals("RetiradaConfirmada", payload.get("tipo").asText());
        assertEquals(pedido, payload.get("dados").get("pedidoId").asLong());
        assertEquals(evento.get("evento_id"), payload.get("eventoId").asText());
        assertFalse(payload.get("ocorridoEm").asText().isBlank());
    }

    @Test
    public void repetirConfirmacaoNaoGeraUmSegundoEvento() {
        long pedido = reservaAtiva();

        ResponseEntity<Map> primeira = confirmar(pedido);
        ResponseEntity<Map> segunda = confirmar(pedido);

        assertEquals(HttpStatus.OK, primeira.getStatusCode());
        assertEquals(HttpStatus.OK, segunda.getStatusCode());
        assertEquals("RETIRADA", segunda.getBody().get("status"));
        assertEquals(1, eventosDo(pedido).size(), "O produtor perdeu a resposta e reenviou: um evento só");
    }

    @Test
    public void reservaLiberadaDeveRecusarSemGerarEvento() {
        long pedido = reservaAtiva();
        entrega.liberarAtendimento(pedido);

        ResponseEntity<Map> resposta = confirmar(pedido);

        assertEquals(HttpStatus.CONFLICT, resposta.getStatusCode());
        assertEquals("Reserva de atendimento foi liberada", resposta.getBody().get("message"));
        assertEquals(0, eventosDo(pedido).size());
    }

    @Test
    public void pedidoSemReservaDeveResponder404SemGerarEvento() {
        long pedido = TestData.pedidoFicticio();

        ResponseEntity<Map> resposta = confirmar(pedido);

        assertEquals(HttpStatus.NOT_FOUND, resposta.getStatusCode());
        assertEquals(0, eventosDo(pedido).size());
    }

    @Test
    public void falhaAoGravarOOutboxDesfazAMudancaDeReserva() {
        long pedido = reservaAtiva();
        db.execute("ALTER TABLE outbox_evento RENAME TO outbox_evento_ocioso");
        try {
            assertThrows(RuntimeException.class, () -> entrega.confirmar(pedido));
            assertEquals("ATIVA", statusDaReserva(pedido),
                "Sem o outbox não pode existir confirmação pela metade: a transação desfaz tudo");
        } finally {
            db.execute("ALTER TABLE outbox_evento_ocioso RENAME TO outbox_evento");
        }
        assertEquals(0, eventosDo(pedido).size());
    }

    private long reservaAtiva() {
        long vendedor = TestData.id();
        long comprador = TestData.id();
        LocalDate data = EntregaService.hoje().plusDays(3);
        long horario = TestData.janela(db, TestData.localRetirada(db, vendedor, "Ponto do D4"), data, 2);
        long pedido = TestData.pedidoFicticio();
        entrega.reservarAtendimento(pedido, comprador, vendedor, horario, data.toString());
        return pedido;
    }

    private ResponseEntity<Map> confirmar(long pedido) {
        return restTemplate.postForEntity("/api/retiradas/reservas/" + pedido + "/confirmacao", null, Map.class);
    }

    private String statusDaReserva(long pedido) {
        return db.queryForObject("SELECT status FROM reserva_atendimento WHERE pedido_id=?", String.class, pedido);
    }

    private List<Map<String, Object>> eventosDo(long pedido) {
        return db.queryForList(
            "SELECT evento_id, tipo, status, payload FROM outbox_evento WHERE agregado_id=? ORDER BY id", pedido);
    }

    private Map<String, Object> unicoEventoDo(long pedido) {
        List<Map<String, Object>> eventos = eventosDo(pedido);
        assertEquals(1, eventos.size());
        return eventos.get(0);
    }
}
