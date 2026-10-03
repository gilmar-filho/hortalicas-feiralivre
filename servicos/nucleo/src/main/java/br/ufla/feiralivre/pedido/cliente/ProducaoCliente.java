package br.ufla.feiralivre.pedido.cliente;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ProducaoCliente {
    private final RestClient http;

    public ProducaoCliente(RestClient.Builder builder, @Value("${servicos.producao.url}") String url) {
        this.http = builder.clone().baseUrl(url).defaultStatusHandler(HttpStatusCode::isError, ErroRemoto::repassar).build();
    }

    public ProdutoResumo produto(long id) { return http.get().uri("/interno/produtos/{id}", id).retrieve().body(ProdutoResumo.class); }
    public void reservarEstoque(long pedidoId, long produtoId, int quantidade, String dataRetirada) { http.post().uri("/interno/reservas-estoque").contentType(MediaType.APPLICATION_JSON).body(Map.of("pedidoId", pedidoId, "produtoId", produtoId, "quantidade", quantidade, "dataRetirada", dataRetirada)).retrieve().toBodilessEntity(); }
    public void devolverEstoque(long pedidoId) { http.delete().uri("/interno/reservas-estoque/{pedidoId}", pedidoId).retrieve().toBodilessEntity(); }
}
