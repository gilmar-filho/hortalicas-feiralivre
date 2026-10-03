package br.ufla.feiralivre.producao.dto;

public record ReservaEstoqueRequest(long pedidoId, long produtoId, int quantidade, String dataRetirada) { }
