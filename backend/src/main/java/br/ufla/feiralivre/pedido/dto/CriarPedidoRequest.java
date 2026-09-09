package br.ufla.feiralivre.pedido.dto;

public record CriarPedidoRequest(long produtoId, int quantidade, String dataRetirada, String horaRetirada, long localRetiradaId, Long compradorId) {
    public long compradorOuPadrao() { return compradorId == null ? 1L : compradorId; }
}
