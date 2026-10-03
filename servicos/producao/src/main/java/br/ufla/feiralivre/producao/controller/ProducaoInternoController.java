package br.ufla.feiralivre.producao.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.ufla.feiralivre.producao.dto.ReservaEstoqueRequest;
import br.ufla.feiralivre.producao.service.ProducaoService;

@RestController
@RequestMapping("/interno")
public class ProducaoInternoController {
    private final ProducaoService service;
    public ProducaoInternoController(ProducaoService service) { this.service = service; }
    @GetMapping("/produtos/{id}") public Map<String, Object> produto(@PathVariable long id) { return service.resumo(id); }
    @PostMapping("/reservas-estoque") @ResponseStatus(HttpStatus.CREATED) public Map<String, Object> reservar(@RequestBody ReservaEstoqueRequest reserva) { return service.reservar(reserva.pedidoId(), reserva.produtoId(), reserva.quantidade(), reserva.dataRetirada()); }
    @DeleteMapping("/reservas-estoque/{pedidoId}") @ResponseStatus(HttpStatus.NO_CONTENT) public void devolver(@PathVariable long pedidoId) { service.devolver(pedidoId); }
}
