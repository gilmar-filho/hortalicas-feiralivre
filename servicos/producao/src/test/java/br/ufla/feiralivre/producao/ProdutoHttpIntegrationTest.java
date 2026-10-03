package br.ufla.feiralivre.producao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import br.ufla.feiralivre.TestData;
import br.ufla.feiralivre.contrato.ValidacaoDeContrato;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ProdutoHttpIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate db;

    @BeforeEach
    public void validarContrato() {
        ValidacaoDeContrato.instalar(restTemplate, "../../contracts/producao.yaml");
    }

    @Test
    public void criarProdutoDeveCriarOPrimeiroLoteEAparecerNoCatalogo() {
        long vendedor = TestData.id();

        ResponseEntity<Map> criado = restTemplate.postForEntity("/api/produtos", produto(vendedor, "Pepino (http)", 12), Map.class);

        assertEquals(HttpStatus.OK, criado.getStatusCode());
        long produto = ((Number) criado.getBody().get("id")).longValue();
        Map item = catalogo(vendedor).stream().filter(p -> ((Number) p.get("id")).longValue() == produto).findFirst().orElseThrow();
        assertEquals(12, ((Number) item.get("estoque")).intValue());
    }

    @Test
    public void editarProdutoDeveAtualizarOLoteInformado() {
        long vendedor = TestData.id();
        long produto = ((Number) restTemplate.postForEntity("/api/produtos", produto(vendedor, "Abóbora (edição)", 5), Map.class).getBody().get("id")).longValue();
        long lote = db.queryForObject("SELECT id FROM lote WHERE produto_id = ?", Long.class, produto);
        Map<String, Object> edicao = produto(vendedor, "Abóbora cabotiá", 9);
        edicao.put("loteId", lote);

        ResponseEntity<Map> editado = restTemplate.exchange("/api/produtos/" + produto, HttpMethod.PUT, new HttpEntity<>(edicao), Map.class);

        assertEquals(HttpStatus.OK, editado.getStatusCode());
        assertEquals("Abóbora cabotiá", editado.getBody().get("nome"));
        assertEquals(9, db.queryForObject("SELECT quantidade_disponivel FROM lote WHERE id = ?", Integer.class, lote));
    }

    @Test
    public void desativarDeveTirarOProdutoDoCatalogo() {
        long vendedor = TestData.id();
        long produto = ((Number) restTemplate.postForEntity("/api/produtos", produto(vendedor, "Jiló (desativado)", 5), Map.class).getBody().get("id")).longValue();

        ResponseEntity<Void> resposta = restTemplate.exchange("/api/produtos/" + produto + "/desativar", HttpMethod.PATCH, null, Void.class);

        assertEquals(HttpStatus.OK, resposta.getStatusCode());
        assertFalse(catalogo(vendedor).stream().anyMatch(p -> ((Number) p.get("id")).longValue() == produto));
    }

    @Test
    public void criarLoteSemValidadeDeveResponder400() {
        long produto = TestData.produto(db, TestData.id(), "Maxixe (lote inválido)", 3.00);

        ResponseEntity<Map> resposta = restTemplate.postForEntity("/api/produtos/" + produto + "/lotes", Map.of("quantidadeEstoque", 10), Map.class);

        assertEquals(HttpStatus.BAD_REQUEST, resposta.getStatusCode());
        assertEquals("Informe a data de vencimento do lote", resposta.getBody().get("message"));
    }

    @Test
    public void criarEAtualizarLoteDevemResponderOsContadores() {
        long produto = TestData.produto(db, TestData.id(), "Inhame (lotes)", 6.00);
        String validade = TestData.hoje().plusDays(9).toString();

        ResponseEntity<Map> criado = restTemplate.postForEntity("/api/produtos/" + produto + "/lotes",
            Map.of("dataValidade", validade, "quantidadeEstoque", 10, "origem", "Roça do teste"), Map.class);
        long lote = ((Number) criado.getBody().get("id")).longValue();
        ResponseEntity<Map> atualizado = restTemplate.exchange("/api/produtos/" + produto + "/lotes/" + lote, HttpMethod.PUT,
            new HttpEntity<>(Map.of("dataValidade", validade, "quantidadeEstoque", 7)), Map.class);

        assertEquals(HttpStatus.OK, criado.getStatusCode());
        assertEquals(HttpStatus.OK, atualizado.getStatusCode());
        assertEquals(0, ((Number) atualizado.getBody().get("quantidade_reservada")).intValue());
        assertEquals(7, db.queryForObject("SELECT quantidade_disponivel FROM lote WHERE id = ?", Integer.class, lote));
    }

    @Test
    public void catalogoPorHttpDeveTrazerProdutoSemLoteValidoComCamposNulos() {
        long vendedor = TestData.id();
        long produto = TestData.produto(db, vendedor, "Taioba (sem lote)", 3.00);

        Map item = catalogo(vendedor).stream().filter(p -> ((Number) p.get("id")).longValue() == produto).findFirst().orElseThrow();

        assertEquals(0, ((Number) item.get("estoque")).intValue());
        assertTrue(item.get("validade") == null && item.get("lote_id") == null);
    }

    private Map<String, Object> produto(long vendedor, String nome, int estoque) {
        Map<String, Object> dados = new HashMap<>();
        dados.put("usuarioId", vendedor);
        dados.put("nome", nome);
        dados.put("preco", 5.0);
        dados.put("ativo", 1);
        dados.put("dataCadastro", TestData.hoje().toString());
        dados.put("dataValidade", TestData.hoje().plusDays(10).toString());
        dados.put("quantidadeEstoque", estoque);
        return dados;
    }

    private List<Map> catalogo(long vendedor) {
        return restTemplate.getForObject("/api/produtos?usuarioId=" + vendedor, List.class);
    }
}
