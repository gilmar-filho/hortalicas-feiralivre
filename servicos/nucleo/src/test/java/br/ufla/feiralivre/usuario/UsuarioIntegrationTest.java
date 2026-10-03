package br.ufla.feiralivre.usuario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import br.ufla.feiralivre.ServicosSimulados;
import br.ufla.feiralivre.TestData;

public class UsuarioIntegrationTest extends ServicosSimulados {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate db;

    @Test
    public void deveAutenticarComCredenciaisCorretas() {
        String email = TestData.email("login.valido");
        TestData.usuario(db, "Login válido", email, "senha-certa");

        ResponseEntity<Map> response = login(email, "senha-certa");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(email, response.getBody().get("email"));
    }

    @Test
    public void deveRecusarLoginComSenhaErrada() {
        String email = TestData.email("senha.errada");
        TestData.usuario(db, "Senha errada", email, "senha-certa");

        ResponseEntity<Map> response = login(email, "senha-torta");

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode(),
            "Senha incorreta deve responder 401");
    }

    @Test
    public void deveRecusarLoginDeEmailInexistente() {
        ResponseEntity<Map> response = login(TestData.email("nao.existe"), "qualquer");

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode(),
            "E-mail inexistente deve responder 401, sem revelar que a conta não existe");
    }

    @Test
    public void naoDeveDevolverASenhaNaAutenticacao() {
        String email = TestData.email("sem.vazar");
        TestData.usuario(db, "Sem vazar", email, "segredo");

        ResponseEntity<Map> response = login(email, "segredo");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNull(response.getBody().get("senha"), "A resposta do login não pode conter a senha");
    }

    private ResponseEntity<Map> login(String email, String senha) {
        return restTemplate.postForEntity("/api/usuario/login", Map.of("email", email, "senha", senha), Map.class);
    }
}
