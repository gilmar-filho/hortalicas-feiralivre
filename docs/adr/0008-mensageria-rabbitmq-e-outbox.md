# 0008. Mensageria com RabbitMQ e outbox transacional

## Status
Proposto

## Contexto
A confirmação da retirada acontece dentro da Entrega — a reserva de
atendimento sai de `ATIVA` para `RETIRADA` — mas o efeito dela sobre o
estoque (a reserva de produto sair de `ATIVA` para `VENDIDA`) é da
Produção. São dois serviços, dois bancos e nenhuma transação que
atravesse os dois. Fazer isso com uma chamada REST síncrona dentro da
transação de confirmação acopla as pontas: se a Produção estiver fora
do ar, a Entrega ou falha junto — o comprador não consegue confirmar —
ou confirma sem o estoque mudar, deixando os dois estados divergentes;
e qualquer repetição da chamada, por timeout ou pelo reenvio do
cliente, precisaria de tratamento à parte para não aplicar o efeito
duas vezes.

O D4 pede essa fronteira resolvida: um evento, gravado na transação de
origem, entregue por um broker e consumido de forma idempotente.

## Decisão
RabbitMQ como broker. O Compose ganha o serviço `rabbitmq`
(`rabbitmq:4-management`) com healthcheck e volume próprio, e Entrega e
Produção esperam ele saudável (`service_healthy`) antes de subir; a
interface de gestão só é publicada no `compose.override.yaml`, fora do
caminho de uso normal.

A topologia é a exchange topic `feira.eventos`, a routing key
`retirada.confirmada` e a fila durable `producao.retirada-confirmada`,
declaradas dos dois lados pelas constantes de `MensageriaConfig` — o
consumidor declara a mesma topologia que o produtor usa, então nenhum
dos dois depende de ter subido primeiro.

A publicação segue o padrão transactional outbox:
`EntregaService.confirmar` grava a transição da reserva e o INSERT em
`outbox_evento` na mesma transação: ou o evento existe junto com o
efeito, ou nenhum dos dois existe. O `OutboxRelay`, agendado a cada
segundo, varre os eventos `PENDENTE` em lotes de 20 e publica cada um,
marcando-o como `PUBLICADO` ou registrando `tentativas` e `ultimo_erro`
na própria linha; uma falha não interrompe o lote, para que um evento
problemático não trave os demais. O relay pode ser desligado pela
propriedade `mensageria.relay.ativo`, deixada em `false` nos testes de
serviço para rodarem sem broker.

A mensagem é uma String JSON com `eventoId`, `tipo`, `ocorridoEm` e
`dados` (pedido, comprador, janela e data de retirada), serializada com
o `ObjectMapper` da aplicação. A idempotência tem duas camadas: na
origem, repetir a confirmação de uma reserva que já está `RETIRADA` não
gera um novo evento; no destino, `RetiradaConfirmadaConsumer` registra
o `evento_id` em `evento_processado` com `INSERT OR IGNORE` antes de
aplicar o efeito, então a segunda entrega do mesmo evento vira linha
duplicada e o consumidor sai sem fazer nada. O efeito é a transição das
reservas de produto para `VENDIDA`, com `quantidade_reservada`
diminuindo e `quantidade_vendida` crescendo na mesma transação. Payload
ilegível é registrado em log e descartado, porque reprocessá-lo nunca
vai funcionar.

## Consequências
O broker vira um ponto único de falha novo, mas o negócio não passa a
depender dele: com o RabbitMQ fora do ar, a confirmação da retirada
continua funcionando — o outbox grava — e o relay publica tudo que
ficou pendente quando o broker voltar. Em troca, o efeito passa a ser
eventual: a reserva de produto só vira `VENDIDA` alguns segundos depois
da confirmação, e leituras imediatas do estoque enxergam a defasagem —
o teste ponta a ponta faz polling justamente por esse motivo.

São duas tabelas novas dentro de bancos existentes (`outbox_evento` em
Entrega e `evento_processado` em Produção), criadas pelo `schema.sql`
de cada serviço; a fronteira de banco por serviço (ADR-0004) não muda.
Não há processo novo: o relay é uma rotina agendada dentro da Entrega e
o consumidor é um listener dentro da Produção, os donos dos respectivos
dados.

A idempotência em duas camadas é o preço do padrão: a origem não
duplica eventos e o destino não duplica efeitos, e cada camada precisa
do seu próprio teste. O teste ponta a ponta passa a provar o caminho
completo outbox → broker → consumidor com um broker de verdade no
Compose, enquanto os testes de serviço seguem sem broker nenhum.
