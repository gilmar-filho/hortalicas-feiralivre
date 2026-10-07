package br.ufla.feiralivre.mensageria;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import br.ufla.feiralivre.entrega.repository.EntregaRepository;

/**
 * Relê do outbox: publica no broker o que a transação de negócio gravou como
 * PENDENTE e só então marca como PUBLICADO. Broker fora do ar atrasa a
 * entrega, nunca derruba a operação que gerou o evento — e, se a marcação
 * falhar depois do envio, o evento é publicado de novo e o consumidor
 * descarta a duplicata.
 */
@Component
public class OutboxRelay {
    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int LOTE = 20;

    private final EntregaRepository repository;
    private final RabbitTemplate rabbit;
    private final boolean ativo;

    public OutboxRelay(EntregaRepository repository, RabbitTemplate rabbit,
            @Value("${mensageria.relay.ativo:true}") boolean ativo) {
        this.repository = repository;
        this.rabbit = rabbit;
        this.ativo = ativo;
    }

    @Scheduled(fixedDelayString = "${mensageria.relay.intervalo-ms:1000}")
    public void publicarPendentes() {
        if (!ativo) return;
        List<Map<String, Object>> pendentes = repository.outboxPendente(LOTE);
        for (Map<String, Object> evento : pendentes) {
            String eventoId = String.valueOf(evento.get("evento_id"));
            try {
                rabbit.convertAndSend(MensageriaConfig.EXCHANGE, MensageriaConfig.ROTEAMENTO_RETIRADA_CONFIRMADA, String.valueOf(evento.get("payload")));
                repository.marcarPublicado(eventoId);
            } catch (RuntimeException e) {
                repository.registrarErroPublicacao(eventoId, truncar(e));
                log.warn("Outbox: publicação do evento {} falhou e será repetida: {}", eventoId, e.getMessage());
            }
        }
    }

    private static String truncar(Exception e) {
        String erro = String.valueOf(e.getMessage());
        return erro.length() > 300 ? erro.substring(0, 300) : erro;
    }
}
