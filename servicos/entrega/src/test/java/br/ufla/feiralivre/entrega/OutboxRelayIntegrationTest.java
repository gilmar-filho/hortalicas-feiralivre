package br.ufla.feiralivre.entrega;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import br.ufla.feiralivre.TestData;
import br.ufla.feiralivre.entrega.repository.EntregaRepository;
import br.ufla.feiralivre.entrega.service.EntregaService;
import br.ufla.feiralivre.mensageria.MensageriaConfig;
import br.ufla.feiralivre.mensageria.OutboxRelay;

/**
 * O outbox sobrevive ao broker fora do ar: a transação de negócio grava, o
 * relê tenta publicar depois e a falha fica registrada para nova tentativa.
 * O broker é substituído por um dublê em vez de um mock: o relê só enxerga o
 * RabbitTemplate, e o dublê evita instrumentação de classe em tempo de execução.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class OutboxRelayIntegrationTest {

    @Autowired
    private EntregaService entrega;

    @Autowired
    private JdbcTemplate db;

    @Test
    public void brokerForaDeveManterOEventoPendenteSemAfastarONegocio() {
        long pedido = reservaAtiva();
        entrega.confirmar(pedido);

        relay(new RabbitDuble(true)).publicarPendentes();

        Map<String, Object> evento = unicoEventoDo(pedido);
        assertEquals("PENDENTE", evento.get("status"));
        assertEquals(1, ((Number) evento.get("tentativas")).intValue());
        assertNotNull(evento.get("ultimo_erro"), "A falha fica registrada no próprio outbox");
        assertEquals("RETIRADA", db.queryForObject(
            "SELECT status FROM reserva_atendimento WHERE pedido_id=?", String.class, pedido),
            "A confirmação da retirada não depende do broker estar no ar");
    }

    @Test
    public void brokerDeVoltaDevePublicarEMarcarComoPublicado() {
        long pedido = reservaAtiva();
        entrega.confirmar(pedido);
        String eventoId = String.valueOf(unicoEventoDo(pedido).get("evento_id"));
        RabbitDuble rabbit = new RabbitDuble(false);

        relay(rabbit).publicarPendentes();

        assertTrue(rabbit.publicados.stream()
                .anyMatch(p -> p.startsWith(MensageriaConfig.EXCHANGE + "|" + MensageriaConfig.ROTEAMENTO_RETIRADA_CONFIRMADA + "|")
                    && p.contains(eventoId)),
            "O que vai para o broker é exatamente o payload gravado na transação");
        Map<String, Object> evento = unicoEventoDo(pedido);
        assertEquals("PUBLICADO", evento.get("status"));
        assertNotNull(evento.get("publicado_em"));
    }

    private OutboxRelay relay(RabbitTemplate rabbit) {
        return new OutboxRelay(new EntregaRepository(db), rabbit, true);
    }

    private long reservaAtiva() {
        long vendedor = TestData.id();
        long comprador = TestData.id();
        LocalDate data = EntregaService.hoje().plusDays(3);
        long horario = TestData.janela(db, TestData.localRetirada(db, vendedor, "Ponto do relê"), data, 2);
        long pedido = TestData.pedidoFicticio();
        entrega.reservarAtendimento(pedido, comprador, vendedor, horario, data.toString());
        return pedido;
    }

    private Map<String, Object> unicoEventoDo(long pedido) {
        List<Map<String, Object>> eventos = db.queryForList(
            "SELECT evento_id, tipo, status, tentativas, ultimo_erro, publicado_em FROM outbox_evento WHERE agregado_id=? ORDER BY id", pedido);
        assertEquals(1, eventos.size());
        return eventos.get(0);
    }

    private static class RabbitDuble extends RabbitTemplate {
        final List<String> publicados = new ArrayList<>();
        private final boolean foraDoAr;

        RabbitDuble(boolean foraDoAr) { this.foraDoAr = foraDoAr; }

        @Override
        public void convertAndSend(String exchange, String routingKey, Object message) {
            if (foraDoAr) throw new AmqpConnectException(new IOException("Connection refused"));
            publicados.add(exchange + "|" + routingKey + "|" + message);
        }
    }
}
