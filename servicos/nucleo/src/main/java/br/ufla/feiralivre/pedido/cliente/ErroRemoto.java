package br.ufla.feiralivre.pedido.cliente;

import java.io.IOException;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Repassa ao front o status e a mensagem que o serviço devolveu. */
final class ErroRemoto {
    private static final ObjectMapper JSON = new ObjectMapper();

    private ErroRemoto() { }

    static void repassar(HttpRequest requisicao, ClientHttpResponse resposta) throws IOException {
        JsonNode corpo = JSON.readTree(resposta.getBody());
        String mensagem = corpo == null ? null : corpo.path("message").asText(null);
        throw new ResponseStatusException(resposta.getStatusCode(), mensagem);
    }
}
