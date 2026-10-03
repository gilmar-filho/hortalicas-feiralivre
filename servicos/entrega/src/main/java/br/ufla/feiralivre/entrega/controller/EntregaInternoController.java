package br.ufla.feiralivre.entrega.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.ufla.feiralivre.entrega.dto.ReservaAtendimentoRequest;
import br.ufla.feiralivre.entrega.service.EntregaService;

@RestController
@RequestMapping("/interno/atendimentos")
public class EntregaInternoController {
    private final EntregaService service;
    public EntregaInternoController(EntregaService service) { this.service = service; }
    @GetMapping("/disponibilidade") @ResponseStatus(HttpStatus.NO_CONTENT) public void disponibilidade(@RequestParam long horarioId, @RequestParam String data, @RequestParam long compradorId, @RequestParam long vendedorId) { service.verificar(compradorId, vendedorId, horarioId, data); }
    @PostMapping @ResponseStatus(HttpStatus.CREATED) public void reservar(@RequestBody ReservaAtendimentoRequest reserva) { service.reservarAtendimento(reserva.pedidoId(), reserva.compradorId(), reserva.vendedorId(), reserva.horarioId(), reserva.dataRetirada()); }
    @DeleteMapping("/{pedidoId}") @ResponseStatus(HttpStatus.NO_CONTENT) public void liberar(@PathVariable long pedidoId) { service.liberarAtendimento(pedidoId); }
}
