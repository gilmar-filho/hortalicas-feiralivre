package br.ufla.feiralivre.faturamento.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import br.ufla.feiralivre.faturamento.repository.FaturamentoRepository;

@Service
public class FaturamentoService {
    private final FaturamentoRepository repository;

    public FaturamentoService(FaturamentoRepository repository) { this.repository = repository; }
    public long criarFatura(long pedidoId, double valor) { return repository.criar(pedidoId, valor); }
    public List<Map<String, Object>> listar(String visao, long usuarioId) { return repository.listar(visao, usuarioId); }
    public Map<String, Object> atualizar(long id, double valor, String status) { return repository.atualizar(id, valor, status); }
}
