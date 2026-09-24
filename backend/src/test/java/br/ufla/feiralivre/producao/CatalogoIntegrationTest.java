package br.ufla.feiralivre.producao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import br.ufla.feiralivre.TestData;
import br.ufla.feiralivre.producao.service.ProducaoService;

@SpringBootTest
public class CatalogoIntegrationTest {

    @Autowired
    private ProducaoService producaoService;

    @Autowired
    private JdbcTemplate db;

    @Test
    public void estoqueDoCatalogoDeveSomarApenasOsLotesAindaValidos() {
        long vendedorId = TestData.usuario(db, "Vendedor catálogo", TestData.email("vendedor.catalogo"), "123456");
        long produtoId = TestData.produto(db, vendedorId, "Abobrinha do catálogo", 5.00);
        TestData.lote(db, produtoId, 5, 10);
        TestData.lote(db, produtoId, 20, 15);
        TestData.lote(db, produtoId, -1, 100);

        Map<String, Object> produto = buscarNoCatalogo(produtoId, vendedorId);

        assertEquals(25, ((Number) produto.get("estoque")).intValue(),
            "O estoque do catálogo soma só os lotes válidos — o vencido não entra");
    }

    @Test
    public void validadeExibidaDeveSerADoLoteQueVencePrimeiro() {
        long vendedorId = TestData.usuario(db, "Vendedor validade cat", TestData.email("vendedor.validadecat"), "123456");
        long produtoId = TestData.produto(db, vendedorId, "Pimentão do catálogo", 6.00);
        TestData.lote(db, produtoId, 18, 10);
        long loteQueVencePrimeiro = TestData.lote(db, produtoId, 4, 10);

        Map<String, Object> produto = buscarNoCatalogo(produtoId, vendedorId);

        String validadeEsperada = db.queryForObject(
            "SELECT data_validade FROM lote WHERE id = ?", String.class, loteQueVencePrimeiro);
        assertEquals(validadeEsperada, produto.get("validade"),
            "O catálogo mostra a validade mais próxima, que é a que o comprador precisa ver");
    }

    @Test
    public void produtoDesativadoNaoDeveAparecerNoCatalogo() {
        long vendedorId = TestData.usuario(db, "Vendedor inativo cat", TestData.email("vendedor.inativocat"), "123456");
        long ativo = TestData.produto(db, vendedorId, "Quiabo visível", 5.00);
        long inativo = TestData.produto(db, vendedorId, "Quiabo escondido", 5.00, false);
        TestData.lote(db, ativo, 7, 10);
        TestData.lote(db, inativo, 7, 10);

        List<Map<String, Object>> catalogo = producaoService.listar("Quiabo", vendedorId);

        assertTrue(contemProduto(catalogo, ativo), "O produto ativo aparece no catálogo");
        assertFalse(contemProduto(catalogo, inativo), "O produto desativado não aparece no catálogo");
    }

    @Test
    public void buscaDeveFiltrarPeloNomeIgnorandoMaiusculas() {
        long vendedorId = TestData.usuario(db, "Vendedor busca", TestData.email("vendedor.busca"), "123456");
        long encontrado = TestData.produto(db, vendedorId, "Mandioquinha salsa", 9.00);
        long ignorado = TestData.produto(db, vendedorId, "Repolho roxo", 4.00);
        TestData.lote(db, encontrado, 7, 10);
        TestData.lote(db, ignorado, 7, 10);

        List<Map<String, Object>> catalogo = producaoService.listar("MANDIOQUINHA", vendedorId);

        assertTrue(contemProduto(catalogo, encontrado), "A busca ignora maiúsculas e minúsculas");
        assertFalse(contemProduto(catalogo, ignorado), "A busca não traz quem não casa com o termo");
    }

    private Map<String, Object> buscarNoCatalogo(long produtoId, long vendedorId) {
        return producaoService.listar("", vendedorId).stream()
            .filter(p -> ((Number) p.get("id")).longValue() == produtoId)
            .findFirst()
            .orElseThrow(() -> new AssertionError("Produto não encontrado no catálogo"));
    }

    private boolean contemProduto(List<Map<String, Object>> catalogo, long produtoId) {
        return catalogo.stream().anyMatch(p -> ((Number) p.get("id")).longValue() == produtoId);
    }
}
