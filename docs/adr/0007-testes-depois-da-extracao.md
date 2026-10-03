# 0007. Redistribuição dos testes e ponta a ponta sobre o Compose

## Status
Proposto

## Contexto
A suíte de testes de integração existente foi escrita para um único
processo com um único banco. Depois da extração de Produção e Entrega
(ADR-0003), boa parte desses testes passa a montar um cenário que não
existe mais — criar um pedido completo só para verificar uma regra que
agora mora inteiramente dentro de um dos serviços extraídos — e os
testes que de fato exercitam a colaboração entre núcleo, Produção e
Entrega deixam de poder contar com uma transação só, porque essa
colaboração agora atravessa rede. O D2 já exigia um teste de fluxo
ponta a ponta; com três processos e um gateway, esse teste só prova
alguma coisa se subir os três de verdade.

## Decisão
Cada teste da suíte antiga foi para o serviço dono da regra que ele
verifica, não para o serviço que por acaso continha o código antes.
Os vinte testes de `EntregaIntegrationTest` que verificam capacidade,
ocupação, janelas e locais ficam em Entrega como estavam, chamando o
`EntregaService` direto, sem passar por um pedido; quem passa a cobrir
`/interno/atendimentos` por HTTP é um teste novo,
`AtendimentoInternoIntegrationTest`, que não existia antes da
extração. Os quatro testes de FEFO e os dois de rastreabilidade de
lote ficam em Produção, chamando `POST /interno/reservas-estoque`. Dos
cinco testes de fluxo completo que exercitavam criação e cancelamento
do pedido sobre os três contextos ainda no mesmo processo, três vão
para o núcleo — validação de entrada, janela de outro produtor e
cancelamento liberando a vaga — com Produção e Entrega substituídas
por WireMock; os outros dois só fazem sentido com os três serviços de
verdade conversando por rede (janela cheia recusando sem gravar nada,
segundo pedido do mesmo comprador entrando na janela cheia) e viram
cenários do teste ponta a ponta.

O núcleo testa contra WireMock, não contra instâncias reais de
Produção e Entrega, pela mesma razão que motivou o ADR-0005: isolar o
teste do núcleo da disponibilidade e do estado de outro processo,
mantendo a prova de que o contrato é respeitado via validação das
interações gravadas contra o OpenAPI de cada serviço.

O fluxo ponta a ponta exigido desde o D2 passa a ser um projeto Maven
próprio, `e2e/`, que sobe `../compose.yaml` inteiro com o
`ComposeContainer` do Testcontainers — projeto Compose com nome
aleatório e volumes removidos ao final, para nunca disputar porta nem
tocar os volumes do ambiente de desenvolvimento. Ele fala só com o
gateway, como o navegador faria, cria os próprios dados pela API
pública, e espera a subida por uma rota de cada um dos três serviços
através do gateway respondendo `200` — não só por `/`, porque o
gateway sobe antes dos serviços atrás dele.

O `testcontainers-bom` é fixado em 1.21.4 ou superior, acima do que o
Spring Boot 3.4.5 gerencia (1.20.6): essa versão mais antiga fala uma
API do Docker que o Docker 29 local recusa. Provar que essa combinação
funciona é a primeira tarefa do plano de implementação, antes de
qualquer outro código ser escrito, porque uma falha aqui bloquearia
tudo que depende do teste ponta a ponta.

O CI passa a rodar `mvn -B test` em matriz, um job por serviço
(`nucleo`, `producao`, `entrega`), mais um job `e2e` separado rodando
`mvn -B verify`.

## Consequências
A cobertura das regras de negócio não muda — a mesma regra que tinha
um teste antes continua tendo um teste, só que no serviço dono dela,
e por vezes expressa como chamada HTTP direta em vez de passar por um
pedido inteiro. O que muda é a velocidade de feedback: os testes de
cada serviço continuam rápidos (banco em arquivo, sem rede), e só o
teste ponta a ponta paga o custo de subir containers de verdade.

O teste ponta a ponta é lento, porque a primeira execução constrói as
quatro imagens Docker a partir do zero — é também o motivo pelo qual
o roteiro de apresentação recomenda construir as imagens com
antecedência antes de qualquer demonstração ao vivo. A fixação do
`testcontainers-bom` numa versão específica é uma dependência externa
nova que o projeto precisa acompanhar conforme o Docker local evolui.
