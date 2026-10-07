package br.ufla.feiralivre.mensageria;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.ufla.feiralivre.producao.repository.ProducaoRepository;

/**
 * Consumidor idempotente do evento RetiradaConfirmada: o broker entrega
 * at-least-once, então a mesma mensagem pode chegar mais de uma vez. O id do
 * evento é gravado na mesma transação do efeito de negócio — duplicata entra
 * como processada e não baixa estoque duas vezes; falha no efeito desfaz o
 * registro e a mensagem volta.
 */
@Component
public class RetiradaConfirmadaConsumer {
    private static final Logger log = LoggerFactory.getLogger(RetiradaConfirmadaConsumer.class);

    private final ProducaoRepository repository;
    private final ObjectMapper json;

    public RetiradaConfirmadaConsumer(ProducaoRepository repository, ObjectMapper json) { this.repository = repository; this.json = json; }

    @RabbitListener(queues = MensageriaConfig.FILA_RETIRADA_CONFIRMADA)
    @Transactional
    public void consumir(String payload) {
        JsonNode evento;
        try {
            evento = json.readTree(payload);
        } catch (JsonProcessingException e) {
            log.error("Evento ilegível descartado (não há como reprocessá-lo): {}", payload);
            return;
        }
        String eventoId = evento.path("eventoId").asText();
        String tipo = evento.path("tipo").asText();
        long pedidoId = evento.path("dados").path("pedidoId").asLong();
        if (repository.registrarProcessamento(eventoId, tipo) == 0) {
            log.info("Evento {} já processado; duplicata descartada", eventoId);
            return;
        }
        int confirmadas = repository.venderReservas(pedidoId);
        if (confirmadas == 0) log.warn("Evento {} para o pedido {} sem reserva ativa", eventoId, pedidoId);
        else log.info("Evento {} processado: retirada do pedido {} baixou {} reserva(s) de estoque", eventoId, pedidoId, confirmadas);
    }
}
