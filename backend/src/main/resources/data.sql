INSERT OR IGNORE INTO usuario (id, nome, email) VALUES (1, 'Ana Feira', 'ana@feira.local');
INSERT OR IGNORE INTO produto (id, usuario_id, nome, descricao, foto, preco, data_cadastro) VALUES (1, 1, 'Alface crespa', 'Colhida hoje, fresca e crocante.', 'https://images.unsplash.com/photo-1622205313162-be1d5712a43c?auto=format&fit=crop&w=700&q=80', 4.50, date('now'));
INSERT OR IGNORE INTO produto (id, usuario_id, nome, descricao, foto, preco, data_cadastro) VALUES (2, 1, 'Tomate italiano', 'Tomates maduros, direto da horta.', 'https://images.unsplash.com/photo-1546094096-0df4bcaaa337?auto=format&fit=crop&w=700&q=80', 7.90, date('now'));
INSERT OR IGNORE INTO produto (id, usuario_id, nome, descricao, foto, preco, data_cadastro) VALUES (3, 1, 'Cenoura orgânica', 'Cenouras doces e selecionadas.', 'https://images.unsplash.com/photo-1447175008436-054170c2e979?auto=format&fit=crop&w=700&q=80', 6.20, date('now'));
INSERT OR IGNORE INTO lote (id, produto_id, origem, data_producao, data_validade, quantidade_total, quantidade_disponivel) VALUES (1, 1, 'Sítio Boa Terra', date('now'), date('now','+7 day'), 80, 80);
INSERT OR IGNORE INTO lote (id, produto_id, origem, data_producao, data_validade, quantidade_total, quantidade_disponivel) VALUES (2, 2, 'Sítio Boa Terra', date('now'), date('now','+10 day'), 45, 45);
INSERT OR IGNORE INTO lote (id, produto_id, origem, data_producao, data_validade, quantidade_total, quantidade_disponivel) VALUES (3, 3, 'Horta da Serra', date('now'), date('now','+14 day'), 60, 60);
INSERT OR IGNORE INTO local_retirada (id, usuario_id, nome, endereco) VALUES (1, 1, 'Feira Central', 'Praça da Matriz, 100 - Centro');
INSERT OR IGNORE INTO horario_retirada (id, local_retirada_id, dia_semana, hora_inicio, hora_fim) VALUES (1, 1, 6, '08:00', '12:00');
