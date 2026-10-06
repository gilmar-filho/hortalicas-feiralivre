package br.ufla.feiralivre.mensageria;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Topologia da mensageria, declarada de forma idempotente por cada serviço:
 * qualquer um que suba primeiro garante exchange, fila e binding no broker.
 */
@Configuration
@EnableScheduling
public class MensageriaConfig {
    public static final String EXCHANGE = "feira.eventos";
    public static final String ROTEAMENTO_RETIRADA_CONFIRMADA = "retirada.confirmada";
    public static final String FILA_RETIRADA_CONFIRMADA = "producao.retirada-confirmada";

    @Bean
    public Declarables topologia() {
        return new Declarables(
            new TopicExchange(EXCHANGE, true, false),
            new Queue(FILA_RETIRADA_CONFIRMADA, true),
            new Binding(FILA_RETIRADA_CONFIRMADA, Binding.DestinationType.QUEUE, EXCHANGE, ROTEAMENTO_RETIRADA_CONFIRMADA, null)
        );
    }
}
