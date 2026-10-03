package br.ufla.feiralivre.pedido.cliente;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class EntregaCliente {
    private final RestClient http;

    public EntregaCliente(RestClient.Builder builder, @Value("${servicos.entrega.url}") String url) {
        this.http = builder.clone().baseUrl(url).defaultStatusHandler(HttpStatusCode::isError, ErroRemoto::repassar).build();
    }

    public void verificarDisponibilidade(long horarioId, String data, long compradorId, long vendedorId) { http.get().uri(u -> u.path("/interno/atendimentos/disponibilidade").queryParam("horarioId", horarioId).queryParam("data", data).queryParam("compradorId", compradorId).queryParam("vendedorId", vendedorId).build()).retrieve().toBodilessEntity(); }
    public void reservarAtendimento(long pedidoId, long compradorId, long vendedorId, long horarioId, String dataRetirada) { http.post().uri("/interno/atendimentos").contentType(MediaType.APPLICATION_JSON).body(Map.of("pedidoId", pedidoId, "compradorId", compradorId, "vendedorId", vendedorId, "horarioId", horarioId, "dataRetirada", dataRetirada)).retrieve().toBodilessEntity(); }
    public void liberarAtendimento(long pedidoId) { http.delete().uri("/interno/atendimentos/{pedidoId}", pedidoId).retrieve().toBodilessEntity(); }
}
