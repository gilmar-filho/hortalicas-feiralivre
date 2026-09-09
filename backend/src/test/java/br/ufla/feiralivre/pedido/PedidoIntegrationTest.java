package br.ufla.feiralivre.pedido;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class PedidoIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate db;

    @Test
    public void deveCriarPedidoReduzirEstoqueEGerarFatura() {
        // 1. Preparar os dados (Garante uma data de retirada no futuro para não dar erro)
        String dataRetirada = LocalDate.now().plusDays(2).toString();
        Map<String, Object> request = Map.of(
            "produtoId", 1,
            "quantidade", 2,
            "dataRetirada", dataRetirada,
            "horaRetirada", "09:00",
            "localRetiradaId", 1,
            "compradorId", 1
        );

        // 2. Verificar o estoque antes da compra
        Integer estoqueAntes = db.queryForObject("SELECT quantidade_disponivel FROM lote WHERE id = 1", Integer.class);

        // 3. Fazer a requisição POST (simulando o frontend)
        ResponseEntity<Map> response = restTemplate.postForEntity("/api/pedidos", request, Map.class);

        // 4. Validar se o servidor respondeu com sucesso (200 OK)
        assertEquals(HttpStatus.OK, response.getStatusCode(), "A requisição deve retornar 200 OK");
        assertNotNull(response.getBody(), "O corpo da resposta não deve ser nulo");
        
        Number pedidoId = (Number) response.getBody().get("id");
        assertNotNull(pedidoId, "O ID do pedido não deve ser nulo");

        // 5. Validar no banco se o Pedido está PENDENTE
        String statusPedido = db.queryForObject("SELECT status FROM pedido WHERE id = ?", String.class, pedidoId);
        assertEquals("PENDENTE", statusPedido, "O status do pedido deve ser PENDENTE");

        // 6. Validar no banco se o estoque diminuiu
        Integer estoqueDepois = db.queryForObject("SELECT quantidade_disponivel FROM lote WHERE id = 1", Integer.class);
        assertEquals(estoqueAntes - 2, estoqueDepois, "O estoque deve ser reduzido em 2 unidades");

        // 7. Validar no banco se a Fatura foi gerada
        String statusFatura = db.queryForObject("SELECT status FROM fatura WHERE pedido_id = ?", String.class, pedidoId);
        assertEquals("PENDENTE", statusFatura, "A fatura deve ser gerada com status PENDENTE");
    }
}