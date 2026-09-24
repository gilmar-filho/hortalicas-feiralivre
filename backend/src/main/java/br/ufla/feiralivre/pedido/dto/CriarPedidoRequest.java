package br.ufla.feiralivre.pedido.dto;

public record CriarPedidoRequest(long produtoId, int quantidade, Long horarioRetiradaId, String dataRetirada, Long compradorId) {
    public long compradorOuPadrao() { return compradorId == null ? 1L : compradorId; }
}
