package br.ufla.feiralivre.entrega.dto;

public record ReservaAtendimentoRequest(long pedidoId, long compradorId, long vendedorId, long horarioId, String dataRetirada) { }
