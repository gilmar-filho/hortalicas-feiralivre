package br.ufla.feiralivre.entrega.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
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
    public List<Map<String, Object>> locais() { return service.listarLocais(); }

    @GetMapping("/horarios")
    public List<Map<String, Object>> horarios(@RequestParam long localId) { return service.listarHorarios(localId); }

    @PostMapping("/locais")
    public Map<String, Object> criarLocal(@RequestBody Map<String, Object> dados) { return service.criarLocal(dados); }

    @PostMapping("/horarios")
    public Map<String, Object> criarHorario(@RequestBody Map<String, Object> dados) { return service.criarHorario(dados); }
}
