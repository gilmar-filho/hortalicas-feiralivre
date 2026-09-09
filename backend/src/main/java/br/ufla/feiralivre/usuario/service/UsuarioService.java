package br.ufla.feiralivre.usuario.service;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import br.ufla.feiralivre.usuario.repository.UsuarioRepository;
import jakarta.annotation.PostConstruct;

@Service
public class UsuarioService {
    private final UsuarioRepository repository;
    public UsuarioService(UsuarioRepository repository) { this.repository = repository; }
    @PostConstruct public void migrarSenha() { repository.migrarSenha(); }
    public Map<String, Object> atual(long id) { return repository.buscar(id); }
    public Map<String, Object> criar(Map<String, Object> dados) { return repository.criar(dados); }
    public Map<String, Object> login(Map<String, Object> dados) { List<Map<String, Object>> usuarios = repository.autenticar(dados); if (usuarios.isEmpty()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "E-mail ou senha inválidos"); return usuarios.get(0); }
}
