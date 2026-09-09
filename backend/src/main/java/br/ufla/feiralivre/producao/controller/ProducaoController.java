package br.ufla.feiralivre.producao.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.ufla.feiralivre.producao.service.ProducaoService;

@RestController
@RequestMapping("/api/produtos")
public class ProducaoController {
    private final ProducaoService service;
    public ProducaoController(ProducaoService service) { this.service = service; }
    @GetMapping public List<Map<String, Object>> listar(@RequestParam(defaultValue = "") String busca, @RequestParam(required = false) Long usuarioId) { return service.listar(busca, usuarioId); }
    @PostMapping public Map<String, Object> criar(@RequestBody Map<String, Object> dados) { return service.criar(dados); }
    @PutMapping("/{produtoId}") public Map<String, Object> editar(@PathVariable long produtoId, @RequestBody Map<String, Object> dados) { return service.editar(produtoId, dados); }
    @PatchMapping("/{produtoId}/desativar") public void desativar(@PathVariable long produtoId) { service.desativar(produtoId); }
    @PostMapping("/{produtoId}/lotes") public Map<String, Object> criarLote(@PathVariable long produtoId, @RequestBody Map<String, Object> dados) { return service.criarLote(produtoId, dados); }
    @PutMapping("/{produtoId}/lotes/{loteId}") public Map<String, Object> atualizarLote(@PathVariable long loteId, @RequestBody Map<String, Object> dados) { return service.atualizarLote(loteId, dados); }
}
