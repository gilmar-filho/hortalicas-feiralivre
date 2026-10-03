# 0005. Contratos OpenAPI contract-first, com testes dos dois lados

## Status
Proposto

## Contexto
O slide 22 pede, como entregável do D3, contratos OpenAPI dos dois
serviços extraídos. Um contrato só tem valor se alguém o escreve antes
do código concordar com ele por acidente, e só continua tendo valor se
alguma coisa o mantém honesto depois que o código muda. Os
controllers atuais de Produção e Entrega recebem e devolvem
`Map<String, Object>` quase sempre construído direto de linhas do
banco; gerar o contrato a partir desse código produziria um
`additionalProperties` aberto em tudo, sem nenhuma garantia real.

## Decisão
Os contratos são escritos à mão, em OpenAPI 3.0.3, em
`contracts/producao.yaml` e `contracts/entrega.yaml` — contract-first,
não gerado do código. Cada contrato documenta tudo o que o serviço
serve: os endpoints públicos que atravessam o gateway
(`/api/produtos/**`, `/api/retiradas/**`), inclusive os que o front
hoje não chama, e os endpoints internos novos (`/interno/**`).

Os dois grupos de endpoint têm convenções diferentes porque nascem de
origens diferentes. Os internos são desenhados para este D3, em
camelCase, e seus schemas fecham com `additionalProperties: false` —
não há motivo para deixar passar um campo que o cliente (o núcleo) não
espera. Os públicos mantêm o formato atual, porque hoje devolvem
linhas inteiras do banco em `snake_case`; fechá-los agora quebraria o
front sem necessidade, e abri-los é honesto sobre o estado real do
código. O formato de erro, nos dois grupos, é o padrão do Spring já em
uso (`{timestamp, status, error, message, path}`), descrito uma vez
como o schema `Erro`.

O contrato é validado nos dois lados. No provedor (Produção e Entrega),
um interceptor instalado no `TestRestTemplate` valida cada requisição e
cada resposta de teste contra o YAML do próprio serviço, usando o
`swagger-request-validator-core` da Atlassian; toda operação descrita
no contrato tem pelo menos um teste que passa por HTTP, então nenhuma
rota documentada fica sem essa prova. No consumidor (o núcleo), o
WireMock substitui Produção e Entrega nos testes, e ao fim de cada
teste toda interação que o WireMock gravou — a requisição que o
cliente HTTP do núcleo mandou e a resposta que o stub devolveu — é
validada contra o contrato do serviço simulado, com o mesmo validador.
Um stub desatualizado em relação ao contrato, ou um cliente que manda
um corpo fora do formato combinado, falha o teste do núcleo antes de
qualquer coisa chegar a um ambiente real.

Alternativas descartadas: springdoc (code-first) foi descartado pelo
motivo já dado — geraria contrato vazio a partir de `Map`. Pact e
Spring Cloud Contract foram descartados por introduzirem um segundo
formato de contrato (pacto ou DSL de contrato) a manter ao lado do
OpenAPI que o D3 já exige; o ganho de testes de contrato por
consumidor não compensa manter dois artefatos quando o mesmo OpenAPI
pode validar os dois lados.

## Consequências
O YAML não pode se afastar do código sem que algum teste falhe, dos
dois lados: uma mudança de resposta em Produção sem atualizar
`producao.yaml` derruba o teste HTTP do próprio serviço; um stub do
núcleo que não reflete mais o contrato derruba o teste do núcleo. Isso
tem um custo de manutenção — todo campo novo precisa ser descrito no
YAML antes do teste passar — em troca da garantia de que o contrato
publicado é o contrato real.

O contrato dos endpoints públicos continua aberto enquanto os
controllers devolverem linhas de banco cruas; fechar esse contrato é
trabalho futuro ligado a separar modelo de persistência de modelo de
API, fora do escopo deste documento.
