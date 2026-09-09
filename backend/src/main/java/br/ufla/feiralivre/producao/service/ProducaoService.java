package br.ufla.feiralivre.producao.service;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import br.ufla.feiralivre.entrega.service.EntregaService;
import br.ufla.feiralivre.producao.repository.ProducaoRepository;
import jakarta.annotation.PostConstruct;

@Service
public class ProducaoService {
    private final ProducaoRepository repository;
    private final EntregaService entrega;

    public ProducaoService(ProducaoRepository repository, EntregaService entrega) { this.repository = repository; this.entrega = entrega; }

    @PostConstruct
    public void migrarEstruturaLegada() { repository.migrarLocalDoProduto(); }

    public List<Map<String, Object>> listar(String busca, Long usuarioId) { return repository.listar(busca, usuarioId); }
    public Map<String, Object> produtoAtivo(long id) { return repository.produtoAtivo(id); }
    public List<Map<String, Object>> lotesDisponiveis(long produtoId, String data) { return repository.lotesDisponiveis(produtoId, data); }
    public long salvarLocal(Map<String, Object> dados) { return entrega.criarLocalSeNecessario(dados); }
    public Map<String, Object> criar(Map<String, Object> dados) { long localId = salvarLocal(dados); Map<String, Object> produto = repository.criarProduto(dados, localId); criarLote(((Number) produto.get("id")).longValue(), dados); return produto; }
    public Map<String, Object> editar(long id, Map<String, Object> dados) { long localId = salvarLocal(dados); repository.atualizarProduto(id, dados, localId); String loteId = String.valueOf(dados.getOrDefault("loteId", "")).trim(); if (!loteId.isEmpty() && dados.get("quantidadeEstoque") != null) atualizarLote(Long.parseLong(loteId), dados); else if (dados.get("quantidadeEstoque") != null) criarLote(id, dados); return repository.produto(id); }
    public void desativar(long id) { repository.desativar(id); }
    public Map<String, Object> criarLote(long produtoId, Map<String, Object> dados) { validarLote(dados); return repository.criarLote(produtoId, dados); }
    public Map<String, Object> atualizarLote(long loteId, Map<String, Object> dados) { validarLote(dados); repository.atualizarLote(loteId, dados); return repository.lote(loteId); }
    public void reservar(long loteId, long pedidoId, int quantidade) { repository.reservar(loteId, pedidoId, quantidade); }
    public List<Map<String, Object>> reservas(long pedidoId) { return repository.reservas(pedidoId); }
    public void devolverReserva(long loteId, int quantidade, boolean vendido) { repository.devolverReserva(loteId, quantidade, vendido); }

    private void validarLote(Map<String, Object> dados) {
        if (dados.get("dataValidade") == null || String.valueOf(dados.get("dataValidade")).isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe a data de vencimento do lote");
        Object quantidade = dados.getOrDefault("quantidadeEstoque", dados.get("quantidade"));
        if (quantidade == null || Integer.parseInt(String.valueOf(quantidade)) < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe uma quantidade de estoque válida");
    }
}
