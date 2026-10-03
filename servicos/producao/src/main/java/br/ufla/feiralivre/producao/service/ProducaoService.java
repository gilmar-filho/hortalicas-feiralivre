package br.ufla.feiralivre.producao.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import br.ufla.feiralivre.producao.repository.ProducaoRepository;

@Service
public class ProducaoService {
    private final ProducaoRepository repository;

    public ProducaoService(ProducaoRepository repository) { this.repository = repository; }

    public List<Map<String, Object>> listar(String busca, Long usuarioId) { return repository.listar(busca, usuarioId); }
    public Map<String, Object> produtoAtivo(long id) { List<Map<String, Object>> produtos = repository.produtoAtivo(id); if (produtos.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Produto não está disponível"); return produtos.get(0); }
    public List<Map<String, Object>> lotesDisponiveis(long produtoId, String data) { return repository.lotesDisponiveis(produtoId, data); }
    public Map<String, Object> criar(Map<String, Object> dados) { Map<String, Object> produto = repository.criarProduto(dados); criarLote(((Number) produto.get("id")).longValue(), dados); return produto; }
    public Map<String, Object> editar(long id, Map<String, Object> dados) { repository.atualizarProduto(id, dados); String loteId = String.valueOf(dados.getOrDefault("loteId", "")).trim(); if (!loteId.isEmpty() && dados.get("quantidadeEstoque") != null) atualizarLote(Long.parseLong(loteId), dados); else if (dados.get("quantidadeEstoque") != null) criarLote(id, dados); return repository.produto(id); }
    public void desativar(long id) { repository.desativar(id); }
    public Map<String, Object> criarLote(long produtoId, Map<String, Object> dados) { validarLote(dados); return repository.criarLote(produtoId, dados); }
    public Map<String, Object> atualizarLote(long loteId, Map<String, Object> dados) { validarLote(dados); repository.atualizarLote(loteId, dados); return repository.lote(loteId); }
    public Map<String, Object> resumo(long id) {
        Map<String, Object> produto = produtoAtivo(id);
        Map<String, Object> resumo = new LinkedHashMap<>();
        resumo.put("id", id);
        resumo.put("vendedorId", produto.get("usuario_id"));
        resumo.put("nome", produto.get("nome"));
        resumo.put("preco", produto.get("preco"));
        return resumo;
    }

    @Transactional
    public Map<String, Object> reservar(long pedidoId, long produtoId, int quantidade, String dataRetirada) {
        produtoAtivo(produtoId);
        List<Map<String, Object>> lotes = repository.lotesDisponiveis(produtoId, dataRetirada);
        int existente = lotes.stream().mapToInt(lote -> ((Number) lote.get("quantidade_disponivel")).intValue()).sum();
        if (existente < quantidade) throw new ResponseStatusException(HttpStatus.CONFLICT, "Estoque insuficiente");
        List<Map<String, Object>> alocados = new ArrayList<>();
        int restante = quantidade;
        for (Map<String, Object> lote : lotes) {
            if (restante == 0) break;
            int usar = Math.min(restante, ((Number) lote.get("quantidade_disponivel")).intValue());
            long loteId = ((Number) lote.get("id")).longValue();
            repository.reservar(loteId, pedidoId, usar);
            Map<String, Object> alocado = new LinkedHashMap<>();
            alocado.put("loteId", loteId);
            alocado.put("quantidade", usar);
            alocado.put("origem", lote.get("origem"));
            alocado.put("dataValidade", lote.get("data_validade"));
            alocados.add(alocado);
            restante -= usar;
        }
        Map<String, Object> reserva = new LinkedHashMap<>();
        reserva.put("pedidoId", pedidoId);
        reserva.put("lotes", alocados);
        return reserva;
    }

    @Transactional
    public void devolver(long pedidoId) {
        for (Map<String, Object> reserva : repository.reservasAtivas(pedidoId))
            repository.devolver(((Number) reserva.get("id")).longValue(), ((Number) reserva.get("lote_id")).longValue(), ((Number) reserva.get("quantidade")).intValue());
    }

    public List<Map<String, Object>> reservasDosPedidos(List<Long> pedidoIds) {
        return pedidoIds == null || pedidoIds.isEmpty() ? List.of() : repository.reservasDosPedidos(pedidoIds);
    }

    private void validarLote(Map<String, Object> dados) {
        if (dados.get("dataValidade") == null || String.valueOf(dados.get("dataValidade")).isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe a data de vencimento do lote");
        Object quantidade = dados.getOrDefault("quantidadeEstoque", dados.get("quantidade"));
        if (quantidade == null || Integer.parseInt(String.valueOf(quantidade)) < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe uma quantidade de estoque válida");
    }
}
