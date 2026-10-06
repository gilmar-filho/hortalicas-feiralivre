package br.ufla.feiralivre.entrega.service;

import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.ufla.feiralivre.entrega.repository.EntregaRepository;

@Service
public class EntregaService {
    public static final String EVENTO_RETIRADA_CONFIRMADA = "RetiradaConfirmada";
    public static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    public static final int HORIZONTE_DIAS = 28;
    private final EntregaRepository repository;
    private final ObjectMapper json;

    public EntregaService(EntregaRepository repository, ObjectMapper json) { this.repository = repository; this.json = json; }

    public static LocalDate hoje() { return LocalDate.now(FUSO); }
    public static int diaSemana(LocalDate data) { return data.getDayOfWeek().getValue() % 7; }

    public List<Map<String, Object>> listarLocais(long vendedorId) { return repository.listarLocais(vendedorId); }
    public List<Map<String, Object>> listarHorarios(long vendedorId) { return repository.listarHorarios(vendedorId); }

    public Map<String, Object> criarLocal(Map<String, Object> dados) {
        String nome = texto(dados, "nome");
        String endereco = texto(dados, "endereco");
        if (nome.isEmpty() || endereco.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe nome e endereço do local");
        return repository.criarLocal(longo(dados, "vendedorId", "Informe nome e endereço do local"), nome, endereco);
    }

    public Map<String, Object> criarHorario(Map<String, Object> dados) {
        long localId = inteiro(dados, "localId", "Local de retirada não encontrado");
        if (repository.local(localId).isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Local de retirada não encontrado");
        int diaSemana = inteiro(dados, "diaSemana", "Dia da semana inválido");
        if (diaSemana < 0 || diaSemana > 6) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dia da semana inválido");
        LocalTime inicio = hora(dados, "horaInicio");
        LocalTime fim = hora(dados, "horaFim");
        if (!fim.isAfter(inicio)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O horário de fim deve ser depois do início");
        return repository.criarHorario(localId, diaSemana, inicio.toString(), fim.toString(), capacidade(dados));
    }

    public Map<String, Object> atualizarCapacidade(long horarioId, Map<String, Object> dados) {
        int capacidade = capacidade(dados);
        if (repository.horario(horarioId).isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Janela de retirada não encontrada");
        repository.atualizarCapacidade(horarioId, capacidade);
        return repository.horario(horarioId).get(0);
    }

    public void verificar(long compradorId, long vendedorId, long horarioId, String dataRetirada) {
        Map<String, Object> horario = repository.horario(horarioId).stream()
            .filter(h -> ((Number) h.get("usuario_id")).longValue() == vendedorId)
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Janela de retirada não encontrada"));
        LocalDate data = LocalDate.parse(dataRetirada);
        LocalDate hoje = hoje();
        if (diaSemana(data) != numero(horario, "dia_semana")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A data não corresponde ao dia da janela");
        if (encerrada(data, String.valueOf(horario.get("hora_fim")), hoje, LocalTime.now(FUSO))) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Esta janela de retirada já encerrou");
        if (data.isAfter(hoje.plusDays(HORIZONTE_DIAS - 1))) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Data fora do período de agendamento");
        if (!repository.compradorJaReservado(horarioId, dataRetirada, compradorId) && repository.ocupacao(horarioId, dataRetirada) >= numero(horario, "capacidade_atendimento"))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Janela de retirada cheia");
    }

    @Transactional
    public void reservarAtendimento(long pedidoId, long compradorId, long vendedorId, long horarioId, String dataRetirada) {
        verificar(compradorId, vendedorId, horarioId, dataRetirada);
        repository.reservar(pedidoId, compradorId, horarioId, dataRetirada);
    }

    public void liberarAtendimento(long pedidoId) { repository.liberar(pedidoId); }

    /**
     * Confirmar a retirada muda a reserva e grava o evento RetiradaConfirmada
     * no outbox na mesma transação: ou as duas coisas acontecem, ou nenhuma.
     * Repetir a confirmação não publica um segundo evento.
     */
    @Transactional
    public Map<String, Object> confirmar(long pedidoId) {
        List<Map<String, Object>> reservas = repository.ultimaReserva(pedidoId);
        if (reservas.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Reserva de atendimento não encontrada");
        Map<String, Object> reserva = reservas.get(0);
        String status = String.valueOf(reserva.get("status"));
        if ("LIBERADA".equals(status)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Reserva de atendimento foi liberada");
        if ("ATIVA".equals(status)) {
            String eventoId = UUID.randomUUID().toString();
            repository.confirmarReserva(pedidoId);
            repository.inserirOutbox(eventoId, EVENTO_RETIRADA_CONFIRMADA, pedidoId, evento(eventoId, reserva));
        }
        return repository.reservasDosPedidos(List.of(pedidoId)).get(0);
    }

    private String evento(String eventoId, Map<String, Object> reserva) {
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("pedidoId", reserva.get("pedido_id"));
        dados.put("compradorId", reserva.get("comprador_id"));
        dados.put("horarioRetiradaId", reserva.get("horario_retirada_id"));
        dados.put("dataRetirada", reserva.get("data_retirada"));
        Map<String, Object> evento = new LinkedHashMap<>();
        evento.put("eventoId", eventoId);
        evento.put("tipo", EVENTO_RETIRADA_CONFIRMADA);
        evento.put("ocorridoEm", Instant.now().toString());
        evento.put("dados", dados);
        try { return json.writeValueAsString(evento); }
        catch (JsonProcessingException e) { throw new UncheckedIOException(e); }
    }

    public List<Map<String, Object>> ocorrencias(long vendedorId, Long compradorId) {
        LocalDate hoje = hoje();
        LocalTime agora = LocalTime.now(FUSO);
        List<Map<String, Object>> resultado = new ArrayList<>();
        for (Map<String, Object> horario : repository.listarHorarios(vendedorId)) {
            long horarioId = ((Number) horario.get("id")).longValue();
            int capacidade = numero(horario, "capacidade_atendimento");
            for (int dia = 0; dia < HORIZONTE_DIAS; dia++) {
                LocalDate data = hoje.plusDays(dia);
                if (diaSemana(data) != numero(horario, "dia_semana") || encerrada(data, String.valueOf(horario.get("hora_fim")), hoje, agora)) continue;
                int ocupadas = repository.ocupacao(horarioId, data.toString());
                Map<String, Object> ocorrencia = new LinkedHashMap<>();
                ocorrencia.put("horarioId", horarioId);
                ocorrencia.put("localNome", horario.get("local_nome"));
                ocorrencia.put("localEndereco", horario.get("local_endereco"));
                ocorrencia.put("data", data.toString());
                ocorrencia.put("diaSemana", diaSemana(data));
                ocorrencia.put("horaInicio", horario.get("hora_inicio"));
                ocorrencia.put("horaFim", horario.get("hora_fim"));
                ocorrencia.put("capacidade", capacidade);
                ocorrencia.put("ocupadas", ocupadas);
                ocorrencia.put("vagas", Math.max(0, capacidade - ocupadas));
                ocorrencia.put("compradorJaReservado", compradorId != null && repository.compradorJaReservado(horarioId, data.toString(), compradorId));
                resultado.add(ocorrencia);
            }
        }
        resultado.sort(Comparator.comparing((Map<String, Object> o) -> (String) o.get("data")).thenComparing(o -> String.valueOf(o.get("horaInicio"))));
        return resultado;
    }

    public List<Map<String, Object>> reservasDosPedidos(List<Long> pedidoIds) {
        return pedidoIds == null || pedidoIds.isEmpty() ? List.of() : repository.reservasDosPedidos(pedidoIds);
    }

    private static boolean encerrada(LocalDate data, String horaFim, LocalDate hoje, LocalTime agora) {
        return data.isBefore(hoje) || (data.equals(hoje) && !agora.isBefore(LocalTime.parse(horaFim)));
    }

    private static int capacidade(Map<String, Object> dados) {
        int capacidade = inteiro(dados, "capacidadeAtendimento", "A capacidade deve ser de pelo menos 1 atendimento");
        if (capacidade < 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A capacidade deve ser de pelo menos 1 atendimento");
        return capacidade;
    }

    private static int inteiro(Map<String, Object> dados, String chave, String mensagem) {
        try { return Integer.parseInt(String.valueOf(dados.get(chave)).trim()); }
        catch (NumberFormatException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem); }
    }

    private static long longo(Map<String, Object> dados, String chave, String mensagem) {
        try { return Long.parseLong(String.valueOf(dados.get(chave)).trim()); }
        catch (NumberFormatException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, mensagem); }
    }

    private static LocalTime hora(Map<String, Object> dados, String chave) {
        try { return LocalTime.parse(String.valueOf(dados.get(chave))); }
        catch (DateTimeParseException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe horários no formato HH:MM"); }
    }

    private static String texto(Map<String, Object> dados, String chave) {
        Object valor = dados.get(chave);
        return valor == null ? "" : String.valueOf(valor).trim();
    }

    private static int numero(Map<String, Object> linha, String coluna) { return ((Number) linha.get(coluna)).intValue(); }
}
