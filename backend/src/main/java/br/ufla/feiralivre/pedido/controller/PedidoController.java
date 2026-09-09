package br.ufla.feiralivre.pedido.controller;

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

import br.ufla.feiralivre.pedido.dto.CriarPedidoRequest;
import br.ufla.feiralivre.pedido.service.PedidoService;

@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {
    private final PedidoService service;
    public PedidoController(PedidoService service) { this.service = service; }
    @GetMapping public List<Map<String,Object>> listar(@RequestParam(defaultValue = "1") long compradorId) { return service.listar(compradorId); }
    @GetMapping("/recebidos") public List<Map<String,Object>> recebidos(@RequestParam(defaultValue = "1") long vendedorId) { return service.recebidos(vendedorId); }
    @PostMapping public Map<String,Object> criar(@RequestBody CriarPedidoRequest request) { return service.criar(request); }
    @PatchMapping("/{pedidoId}/status") public Map<String,Object> atualizarStatus(@PathVariable long pedidoId, @RequestBody Map<String,Object> dados) { return service.atualizarStatus(pedidoId, String.valueOf(dados.get("status")), ((Number) dados.getOrDefault("vendedorId", 1)).longValue()); }
}
