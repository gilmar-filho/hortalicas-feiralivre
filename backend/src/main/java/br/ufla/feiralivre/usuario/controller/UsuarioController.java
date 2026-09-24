package br.ufla.feiralivre.usuario.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.ufla.feiralivre.usuario.service.UsuarioService;

@RestController
@RequestMapping("/api/usuario")
public class UsuarioController {
    private final UsuarioService service;
    public UsuarioController(UsuarioService service) { this.service = service; }
    @GetMapping("/atual") public Map<String, Object> atual(@RequestParam(defaultValue = "1") long usuarioId) { return service.atual(usuarioId); }
    @PostMapping public Map<String, Object> criar(@RequestBody Map<String, Object> dados) { return service.criar(dados); }
    @PostMapping("/login") public Map<String, Object> login(@RequestBody Map<String, Object> dados) { return service.login(dados); }
}
