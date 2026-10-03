package br.ufla.feiralivre.entrega;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import br.ufla.feiralivre.TestData;
import br.ufla.feiralivre.contrato.ValidacaoDeContrato;
import br.ufla.feiralivre.entrega.service.EntregaService;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class AtendimentoInternoIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate db;

    @BeforeEach
    public void validarContrato() {
        ValidacaoDeContrato.instalar(restTemplate, "../../contracts/entrega.yaml");
    }

    @Test
    public void disponibilidadeDeveResponder204QuandoHaVaga() {
        long vendedor = TestData.id();
        LocalDate data = futura(2);
        long horario = janela(vendedor, data, 1, "00:00", "23:59");

        ResponseEntity<Map> resposta = disponibilidade(horario, data, TestData.id(), vendedor);

        assertEquals(HttpStatus.NO_CONTENT, resposta.getStatusCode());
    }

    @Test
    public void disponibilidadeEReservaDevemRecusarComOMesmoStatusEMensagem() {
        long vendedor = TestData.id();
        long outroVendedor = TestData.id();
        LocalDate hoje = EntregaService.hoje();
        LocalDate data = futura(3);
        long valida = janela(vendedor, data, 5, "00:00", "23:59");
        long doOutro = janela(outroVendedor, data, 5, "00:00", "23:59");
        long encerrada = janela(vendedor, hoje, 5, "00:00", "00:00");
        long alemDoHorizonte = janela(vendedor, futura(28), 5, "00:00", "23:59");
        long lotada = janela(vendedor, data, 1, "00:00", "23:59");
        reservar(TestData.id(), TestData.id(), vendedor, lotada, data);

        mesmaRecusa(999_999_999L, data, vendedor, HttpStatus.NOT_FOUND, "Janela de retirada não encontrada");
        mesmaRecusa(doOutro, data, vendedor, HttpStatus.NOT_FOUND, "Janela de retirada não encontrada");
        mesmaRecusa(valida, futura(4), vendedor, HttpStatus.BAD_REQUEST, "A data não corresponde ao dia da janela");
        mesmaRecusa(encerrada, hoje, vendedor, HttpStatus.BAD_REQUEST, "Esta janela de retirada já encerrou");
        mesmaRecusa(alemDoHorizonte, futura(28), vendedor, HttpStatus.BAD_REQUEST, "Data fora do período de agendamento");
        mesmaRecusa(lotada, data, vendedor, HttpStatus.CONFLICT, "Janela de retirada cheia");
    }

    @Test
    public void compradorQueJaOcupaAVagaPassaNaDisponibilidadeComAJanelaCheia() {
        long vendedor = TestData.id();
        long comprador = TestData.id();
        LocalDate data = futura(2);
        long horario = janela(vendedor, data, 1, "00:00", "23:59");
        reservar(TestData.id(), comprador, vendedor, horario, data);

        assertEquals(HttpStatus.NO_CONTENT, disponibilidade(horario, data, comprador, vendedor).getStatusCode());
    }

    @Test
    public void reservaPorHttpDeveCriarReservaAtiva() {
        long vendedor = TestData.id();
        long comprador = TestData.id();
        long pedido = TestData.id();
        LocalDate data = futura(2);
        long horario = janela(vendedor, data, 2, "00:00", "23:59");

        ResponseEntity<Map> resposta = reservar(pedido, comprador, vendedor, horario, data);

        assertEquals(HttpStatus.CREATED, resposta.getStatusCode());
        Map<String, Object> reserva = db.queryForMap(
            "SELECT comprador_id, horario_retirada_id, data_retirada, status FROM reserva_atendimento WHERE pedido_id = ?", pedido);
        assertEquals(comprador, ((Number) reserva.get("comprador_id")).longValue());
        assertEquals(horario, ((Number) reserva.get("horario_retirada_id")).longValue());
        assertEquals(data.toString(), reserva.get("data_retirada"));
        assertEquals("ATIVA", reserva.get("status"));
    }

    @Test
    public void liberacaoPorHttpDeveLiberarEPoderSerRepetida() {
        long vendedor = TestData.id();
        long pedido = TestData.id();
        LocalDate data = futura(2);
        long horario = janela(vendedor, data, 1, "00:00", "23:59");
        reservar(pedido, TestData.id(), vendedor, horario, data);

        ResponseEntity<Void> primeira = liberar(pedido);
        ResponseEntity<Void> segunda = liberar(pedido);

        assertEquals(HttpStatus.NO_CONTENT, primeira.getStatusCode());
        assertEquals(HttpStatus.NO_CONTENT, segunda.getStatusCode());
        assertEquals("LIBERADA", db.queryForObject(
            "SELECT status FROM reserva_atendimento WHERE pedido_id = ?", String.class, pedido));
    }

    @Test
    public void disputaPelaUltimaVagaTerminaEmUm201EUm409() throws Exception {
        long vendedor = TestData.id();
        LocalDate data = futura(2);
        long horario = janela(vendedor, data, 1, "00:00", "23:59");

        List<Integer> status = emParalelo(
            () -> reservar(TestData.id(), TestData.id(), vendedor, horario, data),
            () -> reservar(TestData.id(), TestData.id(), vendedor, horario, data));

        assertEquals(List.of(201, 409), status);
        assertEquals(1, db.queryForObject(
            "SELECT COUNT(DISTINCT comprador_id) FROM reserva_atendimento WHERE horario_retirada_id = ? AND status = 'ATIVA'",
            Integer.class, horario));
    }

    private void mesmaRecusa(long horario, LocalDate data, long vendedor, HttpStatus status, String mensagem) {
        long comprador = TestData.id();
        ResponseEntity<Map> consulta = disponibilidade(horario, data, comprador, vendedor);
        ResponseEntity<Map> reserva = reservar(TestData.id(), comprador, vendedor, horario, data);
        assertEquals(status, consulta.getStatusCode(), "Consulta de " + mensagem);
        assertEquals(mensagem, consulta.getBody().get("message"));
        assertEquals(status, reserva.getStatusCode(), "Reserva de " + mensagem);
        assertEquals(mensagem, reserva.getBody().get("message"));
    }

    private ResponseEntity<Map> disponibilidade(long horario, LocalDate data, long comprador, long vendedor) {
        return restTemplate.getForEntity("/interno/atendimentos/disponibilidade?horarioId=" + horario + "&data=" + data
            + "&compradorId=" + comprador + "&vendedorId=" + vendedor, Map.class);
    }

    private ResponseEntity<Map> reservar(long pedido, long comprador, long vendedor, long horario, LocalDate data) {
        return restTemplate.postForEntity("/interno/atendimentos", Map.of(
            "pedidoId", pedido, "compradorId", comprador, "vendedorId", vendedor,
            "horarioId", horario, "dataRetirada", data.toString()), Map.class);
    }

    private ResponseEntity<Void> liberar(long pedido) {
        return restTemplate.exchange("/interno/atendimentos/" + pedido, HttpMethod.DELETE, null, Void.class);
    }

    private long janela(long vendedor, LocalDate data, int capacidade, String inicio, String fim) {
        return TestData.janela(db, TestData.localRetirada(db, vendedor, "Ponto interno"), data, capacidade, inicio, fim);
    }

    private LocalDate futura(int dias) {
        return EntregaService.hoje().plusDays(dias);
    }

    private List<Integer> emParalelo(Callable<ResponseEntity<Map>> a, Callable<ResponseEntity<Map>> b) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch largada = new CountDownLatch(1);
        try {
            Future<ResponseEntity<Map>> fa = pool.submit(() -> { largada.await(); return a.call(); });
            Future<ResponseEntity<Map>> fb = pool.submit(() -> { largada.await(); return b.call(); });
            largada.countDown();
            return Stream.of(fa.get(15, TimeUnit.SECONDS), fb.get(15, TimeUnit.SECONDS))
                .map(r -> r.getStatusCode().value()).sorted().toList();
        } finally {
            pool.shutdownNow();
        }
    }
}
