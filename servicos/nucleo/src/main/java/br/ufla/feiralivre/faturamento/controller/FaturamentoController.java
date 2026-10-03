package br.ufla.feiralivre.faturamento.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.ufla.feiralivre.faturamento.service.FaturamentoService;

@RestController
@RequestMapping("/api/faturamento")
public class FaturamentoController {
    private final FaturamentoService service;

    public FaturamentoController(FaturamentoService service) { this.service = service; }

    @GetMapping
    public List<Map<String, Object>> listar(@RequestParam(defaultValue = "comprador") String visao, @RequestParam(defaultValue = "1") long usuarioId) {
        return service.listar(visao, usuarioId);
    }

    @PutMapping("/{id}")
    public Map<String, Object> atualizar(@PathVariable long id, @RequestBody Map<String, Object> dados) {
        return service.atualizar(id, ((Number) dados.get("valor")).doubleValue(), String.valueOf(dados.get("status")));
    }
}
