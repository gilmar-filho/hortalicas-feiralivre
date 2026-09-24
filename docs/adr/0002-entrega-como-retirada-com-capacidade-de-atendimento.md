# 0002. Entrega como retirada coordenada com capacidade de atendimento

## Status
Proposto

Complementa o ADR-0001, que permanece válido: o monólito modular e os
quatro contextos delimitados continuam como decidido lá. Este ADR
refina apenas a natureza do contexto Entrega e o seu papel na
transação que atravessa contextos.

## Contexto
O ADR-0001 identificou Entrega como um dos quatro contextos, dono de
"locais, horários e data de retirada". A visão de produto, porém,
descreve o mesmo contexto como dono de "janela, rota e capacidade",
com o entregador confirmando a entrega no destino. São dois domínios
diferentes, e a divergência já aparece no código: o contexto se chama
`entrega` e expõe `/api/retiradas`.

A divergência de nome é o problema menor. O problema maior é que
Entrega não participa do fluxo de criação do pedido. Em
`PedidoService.criar()`, o `local_retirada_id` é gravado diretamente
na tabela `pedido` e `EntregaService` nunca é chamado. O teste de
integração existente reflete isso com precisão: ele verifica o estado
de Pedido, o estoque de Produção e a fatura de Faturamento, e não tem
uma única asserção sobre Entrega — porque não há o que verificar.

Isso compromete a transação principal do produto. Nela, o passo 3
(Entrega reserva uma vaga na janela) precisa ser desfeito quando o
passo 4 (cobrança) falha. Um passo só é compensável se satisfizer
duas condições:

1. **Pode falhar** — existe um estado do mundo em que ele recusa.
2. **Disputa recurso compartilhado** — ele altera algo que outros
   pedidos enxergam, e por isso precisa ser devolvido.

Gravar três campos na linha do próprio pedido não satisfaz nenhuma
das duas: a associação nunca é recusada, e cancelar o pedido já apaga
o registro. Compensar não teria efeito observável, e uma compensação
que não pode ser provada por teste não demonstra o padrão.

Considerou-se voltar ao desenho logístico da visão de produto, com
rota e entregador. Descartado: roteirização já estava declarada fora
de escopo, o produtor que vende direto ao consumidor opera em ponto
de encontro, e a restrição de arquitetura que interessa — um recurso
escasso reservado e liberado — existe igualmente nos dois desenhos.

## Decisão

### 1. Entrega é retirada coordenada
O contexto Entrega é dono do ponto de encontro, das janelas de
atendimento e da confirmação da retirada. O produtor leva os pedidos
a um local em um horário combinado e o comprador retira ali. Não há
rota, entregador nem endereço de destino. A visão de produto passa a
descrever o contexto nesses termos.

O contexto mantém o nome `entrega` no código para não quebrar
referências já existentes; os endpoints permanecem em
`/api/retiradas`.

### 2. A janela de atendimento tem capacidade finita
Cada janela comporta um número máximo de atendimentos, definido pelo
produtor. Esgotada a capacidade, novos pedidos para aquela janela são
recusados e o comprador escolhe outra.

O campo se chama `capacidade_atendimento`, e não `capacidade`, porque
existem duas capacidades distintas neste domínio e confundi-las é
fácil:

| Conceito | O que limita | Escopo |
|---|---|---|
| Capacidade volumétrica | quanto produto cabe no transporte | fora |
| Capacidade de atendimento | quantos clientes cabem na janela | **esta** |

A capacidade volumétrica é uma restrição real do domínio, mas fica
deliberadamente fora desta versão.

Modelagem:

- `horario_retirada.capacidade_atendimento` — inteiro, definido pelo
  produtor ao cadastrar a janela.
- `reserva_atendimento (pedido_id, horario_retirada_id,
  data_retirada)` — a reserva concreta, espelhando a relação que
  `reserva_estoque` já estabelece entre pedido e lote.

A capacidade mora na janela recorrente; a ocupação é contada nas
reservas de uma data específica. `horario_retirada` descreve um
padrão semanal (`dia_semana`), não uma data — a janela de um sábado e
a do sábado seguinte são o mesmo registro com ocupações
independentes.

### 3. Entrega entra no fluxo de criação do pedido
`PedidoService.criar()` passa a pedir a reserva do atendimento a
`EntregaService`, que recusa quando a janela está cheia. O
cancelamento do pedido libera a vaga, pelo mesmo caminho em que hoje
devolve a reserva de estoque.

Com isso o passo 3 passa a ter as duas condições de compensabilidade,
e o teste da SAGA ganha a asserção que hoje não existe: após forçar a
recusa do pagamento, verificar que o estoque voltou ao lote **e** que
a vaga voltou à janela.

### 4. O não comparecimento é confirmado, não inferido
Quando a janela encerra e o pedido não foi baixado como retirado, o
sistema **não** conclui que o comprador faltou. Ausência de registro
não é registro de ausência: o produtor pode estar sem rede no ponto
de encontro, ou ter esquecido de dar baixa.

O sistema notifica o produtor perguntando se a retirada ocorreu, e o
pedido permanece em um estado explícito de indefinição até a
resposta. Se o produtor não responder, o sistema insiste e escala,
mas não decide pelo produtor: o pedido fica pendente e visível, sem
que o sistema afirme um fato que não observou.

Confirmado o não comparecimento, a retirada é reagendada para a
próxima janela se o lote ainda estiver acima do limite mínimo de
validade, ou registrada como quebra se estiver abaixo — a mesma regra
que já governa a devolução de reserva na compensação da SAGA.

Este fluxo é assíncrono e separado da SAGA de confirmação do pedido.
A decisão fica registrada aqui porque nasce do mesmo desenho do
contexto, mas a implementação é posterior à extração dos serviços.

## Consequências
A transação que atravessa contextos passa a ter duas compensações
observáveis em vez de uma, e cada uma é verificável por teste — o que
é o que a entrega do padrão exige.

Entrega deixa de ser uma tabela de referência e passa a ter uma
invariante própria a proteger: nunca aceitar um atendimento além da
capacidade da janela. É isso que justifica sua existência como
contexto delimitado e o torna candidato legítimo a serviço extraído.

Surge uma condição de corrida que hoje não existe: dois pedidos
disputando a última vaga da mesma janela. Dentro do monólito a
transação resolve; após a extração, não. O tratamento será decidido
no ADR de extração dos serviços.

O estado do pedido ganha um valor a mais, para a janela encerrada sem
confirmação. Estados que representam incerteza são mais honestos que
estados que escondem um palpite, mas ampliam a máquina de estados e
as telas que a exibem.

A notificação ao produtor será um consumidor de eventos, não um novo
contexto delimitado. Ela reage ao encerramento da janela sem exigir
alteração em Pedido nem em Entrega, e precisa ser idempotente: o
mesmo evento chega mais de uma vez e o produtor não pode receber a
mesma pergunta repetidas vezes.

A capacidade volumétrica do transporte e a decisão econômica do
produtor entre deslocar-se para poucos atendimentos ou adiar a janela
permanecem fora de escopo. São restrições reais, registradas aqui
para que não sejam reintroduzidas sem decisão explícita.
