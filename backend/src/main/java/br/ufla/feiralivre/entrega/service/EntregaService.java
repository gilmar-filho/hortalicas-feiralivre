package br.ufla.feiralivre.entrega.service;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import br.ufla.feiralivre.entrega.repository.EntregaRepository;

@Service
public class EntregaService {
    private final EntregaRepository repository;

    public EntregaService(EntregaRepository repository) { this.repository = repository; }
    public List<Map<String, Object>> listarLocais() { return repository.listarLocais(); }
    public List<Map<String, Object>> listarHorarios(long localId) { return repository.listarHorarios(localId); }
    public Map<String, Object> criarLocal(Map<String, Object> dados) { return repository.criarLocal(dados); }
    public Map<String, Object> criarHorario(Map<String, Object> dados) { return repository.criarHorario(dados); }
    public long criarLocalSeNecessario(Map<String, Object> dados) { return repository.criarLocalSeNecessario(dados); }
}
