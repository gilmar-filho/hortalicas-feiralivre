package br.ufla.feiralivre.entrega.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.ufla.feiralivre.entrega.service.EntregaService;

@RestController
@RequestMapping("/api/retiradas")
public class EntregaController {
    private final EntregaService service;

    public EntregaController(EntregaService service) { this.service = service; }

    @GetMapping("/locais")
    public List<Map<String, Object>> locais(@RequestParam long vendedorId) { return service.listarLocais(vendedorId); }

    @PostMapping("/locais")
    public Map<String, Object> criarLocal(@RequestBody Map<String, Object> dados) { return service.criarLocal(dados); }

    @GetMapping("/horarios")
    public List<Map<String, Object>> horarios(@RequestParam long vendedorId) { return service.listarHorarios(vendedorId); }

    @PostMapping("/horarios")
    public Map<String, Object> criarHorario(@RequestBody Map<String, Object> dados) { return service.criarHorario(dados); }

    @PatchMapping("/horarios/{horarioId}")
    public Map<String, Object> atualizarCapacidade(@PathVariable long horarioId, @RequestBody Map<String, Object> dados) { return service.atualizarCapacidade(horarioId, dados); }

    @GetMapping("/ocorrencias")
    public List<Map<String, Object>> ocorrencias(@RequestParam long vendedorId, @RequestParam(required = false) Long compradorId) { return service.ocorrencias(vendedorId, compradorId); }

    @GetMapping("/reservas")
    public List<Map<String, Object>> reservas(@RequestParam(required = false) List<Long> pedidoIds) { return service.reservasDosPedidos(pedidoIds); }

    @PostMapping("/reservas/{pedidoId}/confirmacao")
    public Map<String, Object> confirmarRetirada(@PathVariable long pedidoId) { return service.confirmar(pedidoId); }
}
