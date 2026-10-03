package br.ufla.feiralivre.entrega;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import br.ufla.feiralivre.TestData;
import br.ufla.feiralivre.contrato.ValidacaoDeContrato;
import br.ufla.feiralivre.entrega.service.EntregaService;

/**
 * A invariante que justifica Entrega como contexto: nenhuma ocorrência de
 * janela aceita mais compradores do que a capacidade de atendimento.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class EntregaIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private EntregaService entrega;

    @Autowired
    private JdbcTemplate db;

    @BeforeEach
    public void validarContrato() {
        ValidacaoDeContrato.instalar(restTemplate, "../../contracts/entrega.yaml");
    }

    @Test
    public void deveReservarVagaAtivaParaOComprador() {
        long vendedor = pessoa("produtor.reserva");
        long comprador = pessoa("comprador.reserva");
        LocalDate data = futura(3);
        long horario = janela(vendedor, data, 2);
        long pedido = TestData.pedidoFicticio();

        entrega.reservarAtendimento(pedido, comprador, vendedor, horario, data.toString());

        Map<String, Object> reserva = db.queryForMap(
            "SELECT comprador_id, horario_retirada_id, data_retirada, status FROM reserva_atendimento WHERE pedido_id = ?", pedido);
        assertEquals(comprador, ((Number) reserva.get("comprador_id")).longValue());
        assertEquals(horario, ((Number) reserva.get("horario_retirada_id")).longValue());
        assertEquals(data.toString(), reserva.get("data_retirada"));
        assertEquals("ATIVA", reserva.get("status"));
        assertEquals(1, ocupacao(horario, data));
    }

    @Test
    public void deveRecusarJanelaInexistenteOuDeOutroProdutor() {
        long vendedor = pessoa("produtor.dono");
        long outro = pessoa("produtor.outro");
        long comprador = pessoa("comprador.dono");
        LocalDate data = futura(3);
        long horarioDoOutro = janela(outro, data, 5);

        ResponseStatusException inexistente = recusa(() -> reservar(comprador, vendedor, 999_999_999L, data));
        assertEquals(404, inexistente.getStatusCode().value());
        assertEquals("Janela de retirada não encontrada", inexistente.getReason());

        ResponseStatusException deOutro = recusa(() -> reservar(comprador, vendedor, horarioDoOutro, data));
        assertEquals(404, deOutro.getStatusCode().value(), "Janela de outro produtor não existe para este produto");
        assertEquals(0, ocupacao(horarioDoOutro, data));
    }

    @Test
    public void deveRecusarDataForaDoDiaDaJanela() {
        long vendedor = pessoa("produtor.dia");
        long comprador = pessoa("comprador.dia");
        long horario = janela(vendedor, futura(3), 5);

        ResponseStatusException e = recusa(() -> reservar(comprador, vendedor, horario, futura(4)));

        assertEquals(400, e.getStatusCode().value());
        assertEquals("A data não corresponde ao dia da janela", e.getReason());
    }

    @Test
    public void deveRecusarOcorrenciaJaEncerrada() {
        long vendedor = pessoa("produtor.encerrada");
        long comprador = pessoa("comprador.encerrada");
        LocalDate hoje = EntregaService.hoje();
        long encerradaHoje = janela(vendedor, hoje, 5, "00:00", "00:00");
        long deOntem = janela(vendedor, hoje.minusDays(1), 5);

        ResponseStatusException hojeJaFechou = recusa(() -> reservar(comprador, vendedor, encerradaHoje, hoje));
        assertEquals(400, hojeJaFechou.getStatusCode().value());
        assertEquals("Esta janela de retirada já encerrou", hojeJaFechou.getReason());

        ResponseStatusException passada = recusa(() -> reservar(comprador, vendedor, deOntem, hoje.minusDays(1)));
        assertEquals("Esta janela de retirada já encerrou", passada.getReason());
    }

    @Test
    public void deveRespeitarOHorizonteDeAgendamento() {
        long vendedor = pessoa("produtor.horizonte");
        long comprador = pessoa("comprador.horizonte");
        long ultimoDia = janela(vendedor, futura(27), 5);
        long alemDoHorizonte = janela(vendedor, futura(28), 5);

        reservar(comprador, vendedor, ultimoDia, futura(27));

        ResponseStatusException e = recusa(() -> reservar(comprador, vendedor, alemDoHorizonte, futura(28)));
        assertEquals(400, e.getStatusCode().value());
        assertEquals("Data fora do período de agendamento", e.getReason());
    }

    @Test
    public void janelaCheiaDeveRecusarNovoComprador() {
        long vendedor = pessoa("produtor.lotada");
        long primeiro = pessoa("comprador.primeiro");
        long segundo = pessoa("comprador.segundo");
        LocalDate data = futura(2);
        long horario = janela(vendedor, data, 1);
        reservar(primeiro, vendedor, horario, data);

        ResponseStatusException e = recusa(() -> reservar(segundo, vendedor, horario, data));

        assertEquals(409, e.getStatusCode().value());
        assertEquals("Janela de retirada cheia", e.getReason());
        assertEquals(1, ocupacao(horario, data));
    }

    @Test
    public void compradorQueJaOcupaAVagaPodeReservarDeNovoComAJanelaCheia() {
        long vendedor = pessoa("produtor.mesmo");
        long comprador = pessoa("comprador.mesmo");
        LocalDate data = futura(2);
        long horario = janela(vendedor, data, 1);

        reservar(comprador, vendedor, horario, data);
        reservar(comprador, vendedor, horario, data);

        assertEquals(1, ocupacao(horario, data), "A capacidade conta compradores, não pedidos");
        assertEquals(2, db.queryForObject(
            "SELECT COUNT(*) FROM reserva_atendimento WHERE horario_retirada_id = ? AND status = 'ATIVA'", Integer.class, horario));
    }

    @Test
    public void liberarDeveDevolverAVagaESerIdempotente() {
        long vendedor = pessoa("produtor.libera");
        long primeiro = pessoa("comprador.libera1");
        long segundo = pessoa("comprador.libera2");
        LocalDate data = futura(2);
        long horario = janela(vendedor, data, 1);
        long pedido = TestData.pedidoFicticio();
        entrega.reservarAtendimento(pedido, primeiro, vendedor, horario, data.toString());

        entrega.liberarAtendimento(pedido);

        assertEquals("LIBERADA", db.queryForObject(
            "SELECT status FROM reserva_atendimento WHERE pedido_id = ?", String.class, pedido));
        assertEquals(0, ocupacao(horario, data));
        reservar(segundo, vendedor, horario, data);

        entrega.liberarAtendimento(pedido);

        assertEquals(1, ocupacao(horario, data), "Liberar de novo não pode mexer na vaga de outro comprador");
    }

    @Test
    public void compradorComDoisPedidosSoLiberaAVagaQuandoNaoRestaNenhumAtivo() {
        long vendedor = pessoa("produtor.dois");
        long comprador = pessoa("comprador.dois");
        long outro = pessoa("comprador.espera");
        LocalDate data = futura(2);
        long horario = janela(vendedor, data, 1);
        long pedido1 = TestData.pedidoFicticio();
        long pedido2 = TestData.pedidoFicticio();
        entrega.reservarAtendimento(pedido1, comprador, vendedor, horario, data.toString());
        entrega.reservarAtendimento(pedido2, comprador, vendedor, horario, data.toString());

        entrega.liberarAtendimento(pedido1);

        assertEquals(1, ocupacao(horario, data));
        assertEquals(409, recusa(() -> reservar(outro, vendedor, horario, data)).getStatusCode().value());

        entrega.liberarAtendimento(pedido2);

        reservar(outro, vendedor, horario, data);
        assertEquals(1, ocupacao(horario, data));
    }

    @Test
    public void capacidadeReduzidaMantemReservasERecusaNovoComprador() {
        long vendedor = pessoa("produtor.reduz");
        long a = pessoa("comprador.reduzA");
        long b = pessoa("comprador.reduzB");
        long c = pessoa("comprador.reduzC");
        LocalDate data = futura(2);
        long horario = janela(vendedor, data, 3);
        reservar(a, vendedor, horario, data);
        reservar(b, vendedor, horario, data);

        entrega.atualizarCapacidade(horario, Map.of("capacidadeAtendimento", 1));

        assertEquals(2, ocupacao(horario, data), "Reduzir a capacidade não cancela ninguém");
        assertEquals(409, recusa(() -> reservar(c, vendedor, horario, data)).getStatusCode().value());
        reservar(a, vendedor, horario, data);
    }

    @Test
    public void cadastroDeJanelaDeveRecusarValoresInvalidos() {
        long vendedor = pessoa("produtor.cadastro");
        long localId = TestData.localRetirada(db, vendedor, "Ponto do cadastro");

        assertBadRequest(novaJanela(localId, 6, "08:00", "12:00", 0), "A capacidade deve ser de pelo menos 1 atendimento");
        assertBadRequest(novaJanela(localId, 7, "08:00", "12:00", 3), "Dia da semana inválido");
        assertBadRequest(novaJanela(localId, 6, "08:00", "08:00", 3), "O horário de fim deve ser depois do início");
        assertBadRequest(novaJanela(localId, 6, "oito", "12:00", 3), "Informe horários no formato HH:MM");

        ResponseStatusException capacidadeZero = recusa(() -> entrega.atualizarCapacidade(
            TestData.janela(db, localId, futura(2), 3), Map.of("capacidadeAtendimento", 0)));
        assertEquals("A capacidade deve ser de pelo menos 1 atendimento", capacidadeZero.getReason());

        assertEquals(0, db.queryForObject(
            "SELECT COUNT(*) FROM horario_retirada WHERE local_retirada_id = ? AND capacidade_atendimento <> 3", Integer.class, localId));
    }

    @Test
    public void cadastroDeJanelaDeveCriarComCapacidadeEListarPorProdutor() {
        long vendedor = pessoa("produtor.lista");
        long localId = TestData.localRetirada(db, vendedor, "Ponto da lista");

        ResponseEntity<Map> criada = novaJanela(localId, 3, "07:30", "10:00", 4);

        assertEquals(HttpStatus.OK, criada.getStatusCode());
        List<Map> janelas = restTemplate.getForObject("/api/retiradas/horarios?vendedorId=" + vendedor, List.class);
        assertEquals(1, janelas.size());
        assertEquals(4, ((Number) janelas.get(0).get("capacidade_atendimento")).intValue());
        assertEquals("Ponto da lista", janelas.get(0).get("local_nome"));
        assertEquals("07:30", janelas.get(0).get("hora_inicio"));
    }

    @Test
    public void localEJanelaInexistentesDevemResponder404() {
        ResponseEntity<Map> semLocal = novaJanela(999_999_999L, 6, "08:00", "12:00", 3);
        assertEquals(HttpStatus.NOT_FOUND, semLocal.getStatusCode());
        assertEquals("Local de retirada não encontrado", semLocal.getBody().get("message"));

        ResponseStatusException semJanela = recusa(() -> entrega.atualizarCapacidade(999_999_999L, Map.of("capacidadeAtendimento", 2)));
        assertEquals(404, semJanela.getStatusCode().value());
        assertEquals("Janela de retirada não encontrada", semJanela.getReason());
    }

    @Test
    public void cadastroDeLocalDeveGravarOProdutorEExigirNomeEEndereco() {
        long vendedor = pessoa("produtor.local");

        ResponseEntity<Map> criado = restTemplate.postForEntity("/api/retiradas/locais",
            Map.of("vendedorId", vendedor, "nome", "Praça nova", "endereco", "Rua A, 1"), Map.class);
        ResponseEntity<Map> semNome = restTemplate.postForEntity("/api/retiradas/locais",
            Map.of("vendedorId", vendedor, "nome", " ", "endereco", "Rua A, 1"), Map.class);

        assertEquals(HttpStatus.OK, criado.getStatusCode());
        assertEquals(vendedor, ((Number) criado.getBody().get("usuario_id")).longValue());
        assertBadRequest(semNome, "Informe nome e endereço do local");
        List<Map> locais = restTemplate.getForObject("/api/retiradas/locais?vendedorId=" + vendedor, List.class);
        assertEquals(1, locais.size());
    }

    @Test
    public void ocorrenciasDevemListarQuatroSemanasComVagas() {
        long vendedor = pessoa("produtor.semanas");
        long horario = janela(vendedor, futura(1), 2);

        List<Map<String, Object>> ocorrencias = ocorrenciasDa(vendedor, null, horario);

        assertEquals(4, ocorrencias.size(), "28 dias contêm exatamente 4 ocorrências de cada dia da semana");
        for (int semana = 0; semana < 4; semana++) {
            Map<String, Object> o = ocorrencias.get(semana);
            assertEquals(futura(1 + 7 * semana).toString(), o.get("data"));
            assertEquals(EntregaService.diaSemana(futura(1)), ((Number) o.get("diaSemana")).intValue());
            assertEquals(2, ((Number) o.get("capacidade")).intValue());
            assertEquals(0, ((Number) o.get("ocupadas")).intValue());
            assertEquals(2, ((Number) o.get("vagas")).intValue());
            assertEquals(false, o.get("compradorJaReservado"));
        }
    }

    @Test
    public void ocorrenciasDevemMostrarEsgotadaEReservaDoComprador() {
        long vendedor = pessoa("produtor.esgotada");
        long quemReservou = pessoa("comprador.reservou");
        long outro = pessoa("comprador.olhando");
        LocalDate data = futura(1);
        long horario = janela(vendedor, data, 1);
        reservar(quemReservou, vendedor, horario, data);

        Map<String, Object> vistaPorQuemReservou = ocorrenciasDa(vendedor, quemReservou, horario).get(0);
        Map<String, Object> vistaPorOutro = ocorrenciasDa(vendedor, outro, horario).get(0);
        Map<String, Object> vistaAnonima = ocorrenciasDa(vendedor, null, horario).get(0);

        assertEquals(data.toString(), vistaPorOutro.get("data"));
        assertEquals(0, ((Number) vistaPorOutro.get("vagas")).intValue());
        assertEquals(1, ((Number) vistaPorOutro.get("ocupadas")).intValue());
        assertEquals(true, vistaPorQuemReservou.get("compradorJaReservado"));
        assertEquals(false, vistaPorOutro.get("compradorJaReservado"));
        assertEquals(false, vistaAnonima.get("compradorJaReservado"));
    }

    @Test
    public void ocorrenciaEncerradaNaoApareceEReservaLiberadaNaoConta() {
        long vendedor = pessoa("produtor.filtros");
        long comprador = pessoa("comprador.filtros");
        LocalDate hoje = EntregaService.hoje();
        long encerradaHoje = janela(vendedor, hoje, 5, "00:00", "00:00");
        LocalDate data = futura(2);
        long horario = janela(vendedor, data, 1);
        long pedido = TestData.pedidoFicticio();
        entrega.reservarAtendimento(pedido, comprador, vendedor, horario, data.toString());
        entrega.liberarAtendimento(pedido);

        List<Map<String, Object>> deHoje = ocorrenciasDa(vendedor, null, encerradaHoje);
        Map<String, Object> liberada = ocorrenciasDa(vendedor, null, horario).get(0);

        assertEquals(3, deHoje.size());
        assertFalse(deHoje.stream().anyMatch(o -> hoje.toString().equals(o.get("data"))), "A ocorrência encerrada hoje não aparece");
        assertTrue(entrega.ocorrencias(vendedor, null).stream()
            .map(o -> LocalDate.parse((String) o.get("data")))
            .allMatch(d -> !d.isBefore(hoje) && !d.isAfter(hoje.plusDays(27))));
        assertEquals(0, ((Number) liberada.get("ocupadas")).intValue());
        assertEquals(1, ((Number) liberada.get("vagas")).intValue());
    }

    @Test
    public void produtorSemJanelasNaoTemOcorrencias() {
        assertTrue(entrega.ocorrencias(pessoa("produtor.vazio"), null).isEmpty());
    }

    @Test
    public void reservasDevemIncluirLiberadasEDevolverAMaisRecentePorPedido() {
        long vendedor = pessoa("produtor.reservas");
        long comprador = pessoa("comprador.reservas");
        LocalDate data = futura(1);
        long horario = janela(vendedor, data, 5);
        long cancelado = TestData.pedidoFicticio();
        long reagendado = TestData.pedidoFicticio();
        entrega.reservarAtendimento(cancelado, comprador, vendedor, horario, data.toString());
        entrega.liberarAtendimento(cancelado);
        entrega.reservarAtendimento(reagendado, comprador, vendedor, horario, data.toString());
        entrega.liberarAtendimento(reagendado);
        entrega.reservarAtendimento(reagendado, comprador, vendedor, horario, data.plusDays(7).toString());

        List<Map> reservas = restTemplate.getForObject(
            "/api/retiradas/reservas?pedidoIds=" + cancelado + "," + reagendado, List.class);

        assertEquals(2, reservas.size(), "Uma reserva por pedido: a mais recente");
        Map doCancelado = reservaDo(reservas, cancelado);
        Map doReagendado = reservaDo(reservas, reagendado);
        assertEquals("LIBERADA", doCancelado.get("status"));
        assertEquals("Ponto de entrega", doCancelado.get("localNome"));
        assertEquals("00:00", doCancelado.get("horaInicio"));
        assertEquals("23:59", doCancelado.get("horaFim"));
        assertEquals("ATIVA", doReagendado.get("status"));
        assertEquals(data.plusDays(7).toString(), doReagendado.get("data"));
    }

    @Test
    public void reservasSemPedidosDevemResponderListaVazia() {
        ResponseEntity<List> resposta = restTemplate.getForEntity("/api/retiradas/reservas", List.class);

        assertEquals(HttpStatus.OK, resposta.getStatusCode());
        assertTrue(resposta.getBody().isEmpty());
    }

    private long pessoa(String prefixo) {
        return TestData.id();
    }

    private LocalDate futura(int dias) {
        return EntregaService.hoje().plusDays(dias);
    }

    private long janela(long vendedor, LocalDate data, int capacidade) {
        return TestData.janela(db, TestData.localRetirada(db, vendedor, "Ponto de entrega"), data, capacidade);
    }

    private long janela(long vendedor, LocalDate data, int capacidade, String inicio, String fim) {
        return TestData.janela(db, TestData.localRetirada(db, vendedor, "Ponto de entrega"), data, capacidade, inicio, fim);
    }

    private void reservar(long comprador, long vendedor, long horario, LocalDate data) {
        entrega.reservarAtendimento(TestData.pedidoFicticio(), comprador, vendedor, horario, data.toString());
    }

    private int ocupacao(long horario, LocalDate data) {
        return db.queryForObject(
            "SELECT COUNT(DISTINCT comprador_id) FROM reserva_atendimento WHERE horario_retirada_id = ? AND data_retirada = ? AND status = 'ATIVA'",
            Integer.class, horario, data.toString());
    }

    private ResponseStatusException recusa(Executable acao) {
        return assertThrows(ResponseStatusException.class, acao);
    }

    private List<Map<String, Object>> ocorrenciasDa(long vendedor, Long comprador, long horario) {
        return entrega.ocorrencias(vendedor, comprador).stream()
            .filter(o -> ((Number) o.get("horarioId")).longValue() == horario)
            .toList();
    }

    private ResponseEntity<Map> novaJanela(long localId, int diaSemana, String inicio, String fim, int capacidade) {
        return restTemplate.postForEntity("/api/retiradas/horarios", Map.of(
            "localId", localId, "diaSemana", diaSemana, "horaInicio", inicio, "horaFim", fim,
            "capacidadeAtendimento", capacidade), Map.class);
    }

    private Map reservaDo(List<Map> reservas, long pedido) {
        return reservas.stream()
            .filter(r -> ((Number) r.get("pedidoId")).longValue() == pedido)
            .findFirst()
            .orElseThrow(() -> new AssertionError("Reserva do pedido " + pedido + " não veio"));
    }

    private void assertBadRequest(ResponseEntity<Map> resposta, String mensagem) {
        assertEquals(HttpStatus.BAD_REQUEST, resposta.getStatusCode());
        assertEquals(mensagem, resposta.getBody().get("message"));
    }

    @Test
    public void capacidadePorHttpDeveAtualizarAJanela() {
        long vendedor = pessoa("produtor.patch");
        long horario = janela(vendedor, futura(2), 3);

        ResponseEntity<Map> resposta = restTemplate.exchange("/api/retiradas/horarios/" + horario,
            HttpMethod.PATCH, new HttpEntity<>(Map.of("capacidadeAtendimento", 5)), Map.class);

        assertEquals(HttpStatus.OK, resposta.getStatusCode());
        assertEquals(5, ((Number) resposta.getBody().get("capacidade_atendimento")).intValue());
    }
}
