# 0010. SAGA orquestrada para criação do pedido

## Status
Aceito

## Contexto

A criação de um pedido atravessa três fronteiras que não compartilham uma
transação: Produção reserva o estoque, Entrega reserva a vaga de atendimento
e o Núcleo grava o pedido, seus itens e a fatura. Um erro depois da primeira
reserva pode deixar estoque ou capacidade presos sem existir um pedido que os
referencie.

O D5 exige uma SAGA completa, com uma falha forçada e compensações observáveis.
Não existe rollback distribuído entre os bancos dos serviços, portanto cada
passo precisa ter uma ação de desfazimento própria.

## Decisão

Implementamos uma **SAGA orquestrada e completa**. Ela é orquestrada porque o
`PedidoService`, no Núcleo, é o orquestrador: ele conhece a ordem dos passos, chama os participantes e decide
quando executar as compensações. Produção e Entrega não conhecem a sequência
completa nem chamam uns aos outros.

Ela é completa porque contém o fluxo feliz, as compensações dos passos já
executados, o tratamento de falha e testes que comprovam os efeitos de
compensação. Portanto, a SAGA implementada não é coreografada: a alternativa
coreografada foi analisada, mas rejeitada para este fluxo.

### Coreografada x orquestrada

| Alternativa | Funcionamento | Decisão |
|---|---|---|
| Coreografada | Cada participante publica eventos e o próximo reage a eles. A lógica fica distribuída entre consumidores e exige eventos de compensação para desfazer cada passo. | Rejeitada para o D5: adicionaria broker, consumidores e estados intermediários ao fluxo síncrono de criação, além da mensageria assíncrona já usada no D4 para a retirada. |
| Orquestrada | Um coordenador chama os participantes em uma ordem explícita e executa as compensações quando um passo posterior falha. | Adotada: o Núcleo já coordena a criação e os contratos HTTP internos já oferecem as operações necessárias. |

### Fluxo feliz

1. O Núcleo valida o produto e a disponibilidade da janela.
2. O Núcleo reserva um id que não será reutilizado.
3. Produção reserva o estoque para esse id.
4. Entrega reserva a vaga de atendimento para esse id.
5. O Núcleo grava o pedido, o item e a fatura em uma transação local curta.
6. O pedido é retornado ao cliente.

Nenhuma transação local fica aberta durante as chamadas remotas.

### Compensações

Se a reserva de Entrega ou a gravação local falhar depois da reserva de
Produção, o orquestrador executa:

1. `DELETE /interno/atendimentos/{pedidoId}` para liberar a vaga;
2. `DELETE /interno/reservas-estoque/{pedidoId}` para devolver o estoque.

As operações são idempotentes: liberar ou devolver uma segunda vez não altera
outros pedidos nem aumenta o estoque. Se uma compensação também falhar, a
falha original continua sendo retornada e a exceção da compensação é anexada
como suprimida para diagnóstico. Uma nova tentativa explícita do fluxo pode
reexecutar as operações sem duplicar o efeito.

A confirmação de cada reserva só é marcada após a resposta bem-sucedida do
serviço. Assim, uma chamada que falha antes de reservar não dispara uma
compensação desnecessária; uma reserva aceita sempre é compensada quando o
passo seguinte falha.

### Falha provada por teste

`PedidoIntegrationTest.falhaNaReservaDeAtendimentoCompensaEstoque` força a
recusa de Entrega depois que Produção aceitou a reserva e verifica a devolução
do estoque, sem tentar liberar uma vaga que nunca foi reservada.

`PedidoIntegrationTest.falhaNaGravacaoLocalCompensaEstoqueEAtendimento`
renomeia temporariamente `item_pedido` depois que Produção e Entrega aceitam
suas reservas. A gravação local falha, o teste verifica as duas chamadas
`DELETE` de compensação e confirma que nenhum pedido foi persistido.

O teste também mantém a validação dos contratos OpenAPI dos serviços
participantes. Portanto, a evidência não é apenas a exceção: ela prova o
retorno do estoque, a liberação da vaga e a ausência de pedido pela metade.

### Limite de escopo do D5

Este ADR implementa somente o necessário para a SAGA orquestrada: sequência
de etapas, compensações, propagação da falha e testes do fluxo. O D5 não
implementa Event Sourcing, read model, replay, retry automático, circuit
breaker ou tracing distribuído.

As fronteiras atuais deixam pontos de extensão para as próximas etapas: os
passos da SAGA estão concentrados no `PedidoService`, as chamadas remotas
passam por `ProducaoCliente` e `EntregaCliente`, e cada operação usa o
`pedidoId` como identificador comum. O D6 poderá publicar eventos e alimentar
projeções sem transformar esta SAGA em uma coreografia; o D7 poderá envolver
as chamadas remotas com resiliência e instrumentação sem alterar a ordem ou
as compensações definidas aqui.

## Consequências

A criação do pedido deixa de abandonar silenciosamente reservas remotas
quando a etapa local falha. O custo é que o Núcleo passa a conhecer a ordem
dos participantes e suas operações de compensação, tornando-se responsável
pela evolução desse contrato de coordenação.

A solução ainda tem uma janela de incerteza inerente a chamadas remotas: se um
serviço executar a reserva e a resposta se perder, o Núcleo pode não saber se
a etapa terminou. Os endpoints de compensação idempotentes reduzem esse risco,
mas não substituem uma reconciliação durável. Uma futura evolução pode
persistir o estado da SAGA e as tentativas de compensação para recuperação
assíncrona.

A mensageria do D4 continua separada: ela trata a retirada confirmada com
Outbox e consumidor idempotente; a SAGA deste ADR trata a criação síncrona do
pedido e usa HTTP interno para coordenar e compensar reservas.

Eventual publicação de eventos para read models e eventual instrumentação de
retry, circuit breaker e tracing pertencem aos ADRs das etapas D6 e D7, não a
esta decisão.
