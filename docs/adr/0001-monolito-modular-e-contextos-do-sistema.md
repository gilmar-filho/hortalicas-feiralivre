# 0001. Adotar monólito modular para a feira livre

## Status
Aceito

## Contexto
O sistema Feira Livre precisa apoiar a comercialização de hortaliças com
rastreabilidade de produtos, controle de estoque por lote, pedidos de
compradores, faturamento e retirada. A entrega acadêmica exige a aplicação
de Event Storming, a identificação de contextos delimitados, a construção
de um monólito modular com pelo menos três contextos e a demonstração de um
fluxo ponta a ponta.

A aplicação também precisa permitir que compradores consultem o catálogo,
criem pedidos e acompanhem suas compras, enquanto vendedores cadastram
produtos, controlam lotes, visualizam pedidos recebidos e acompanham as
faturas. Esses comportamentos atravessam mais de uma responsabilidade de
negócio, mas o escopo atual não justifica a operação de serviços distribuídos
com bancos e comunicação remota independentes.

A análise de Event Storming identificou os principais eventos do fluxo:
produto cadastrado, lote cadastrado, pedido criado, estoque reservado,
fatura gerada, pedido confirmado, pedido cancelado e retirada realizada.
Esses eventos evidenciam quatro áreas de responsabilidade:

- **Pedido**: comprador, itens, estado do pedido e acompanhamento.
- **Produção**: produtos, lotes, validade, disponibilidade e reserva de
  estoque.
- **Entrega**: locais, horários e data de retirada.
- **Faturamento**: faturas, valores e estado da cobrança.

## Decisão
A aplicação será implementada como um **monólito modular** em Java/Spring
Boot, com o React como frontend web separado por responsabilidade de
apresentação. Cada contexto terá seus próprios pacotes, controladores e
regras de negócio dentro do mesmo backend e compartilhará o banco SQLite da
aplicação nesta primeira versão.

Os módulos backend ficam organizados por contexto em:

- `backend/src/main/java/br/ufla/feiralivre/pedido/`
- `backend/src/main/java/br/ufla/feiralivre/producao/`
- `backend/src/main/java/br/ufla/feiralivre/entrega/`
- `backend/src/main/java/br/ufla/feiralivre/faturamento/`

O módulo de usuário apoia autenticação e identificação do comprador ou
vendedor. O frontend em `frontend/src/` oferece as telas de login, cadastro,
compra, venda, acompanhamento de pedidos e leitura de faturas, consumindo a
API REST do monólito.

Na visão C4, a solução fica representada assim:

- **Nível de contexto**: comprador e vendedor interagem com o sistema Feira
  Livre.
- **Nível de containers**: frontend React, backend Spring Boot e banco
  SQLite.
- **Nível de componentes**: os módulos Pedido, Produção, Entrega,
  Faturamento e Usuário, cada um responsável por seu conjunto de regras e
  endpoints.

```text
Comprador ──┐
            ├──▶ React ──REST──▶ Spring Boot ──▶ SQLite
Vendedor ───┘                     │
                       ┌──────────┼──────────┐
                       ▼          ▼          ▼
                     Pedido   Produção   Faturamento
                                  │
                                  ▼
                                Entrega
```

O fluxo ponta a ponta entregue é:

1. O vendedor cadastra um produto e um lote com quantidade e validade.
2. O comprador autentica, consulta o catálogo e seleciona um produto.
3. O comprador informa quantidade e data de retirada.
4. Pedido registra a compra como pendente.
5. Produção reserva a quantidade disponível no lote válido mais próximo do
   vencimento e registra a relação da reserva com o pedido.
6. Entrega associa o pedido ao local, horário e data de retirada.
7. Faturamento gera a fatura pendente associada ao pedido.
8. O comprador acompanha o pedido e a próxima retirada; o vendedor consulta
   os pedidos recebidos e as faturas de seus produtos.
9. A alteração de status sincroniza pedido e fatura; em caso de
   cancelamento, a quantidade reservada retorna ao estoque disponível.

As fronteiras entre os módulos são mantidas por seus endpoints e tabelas,
mesmo com a execução dentro do mesmo processo. A separação permite evoluir
cada contexto sem antecipar a complexidade operacional de uma arquitetura de
microserviços.

## Consequências
A decisão atende à entrega D2 com quatro contextos delimitados, um fluxo
ponta a ponta demonstrável e uma estrutura de monólito modular que pode ser
executada localmente com Java, Maven, Node.js e SQLite.

O mesmo banco e processo simplificam a execução, os testes e a consistência
transacional durante a disciplina. Também reduzem a quantidade de
infraestrutura necessária para validar o fluxo de compra e venda.

Como consequência, os módulos ainda compartilham o ciclo de vida da mesma
aplicação e do banco SQLite. Uma futura extração para serviços distribuídos
exigirá contratos de integração, eventos, idempotência e estratégia de
compensação para os fluxos de Pedido, Produção, Entrega e Faturamento. Essa
complexidade fica deliberadamente fora do escopo desta versão, embora as
fronteiras dos contextos já estejam documentadas para permitir essa evolução.
