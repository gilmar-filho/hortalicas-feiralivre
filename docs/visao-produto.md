# Visão do produto

## O problema
Produtores de alimentos perecíveis de ciclo curto vendem sem
rastreabilidade e sem controle de validade por lote. O resultado é
perda por vencimento e nenhuma resposta quando o cliente pergunta
de onde veio o produto.

## Para quem
Pequeno produtor que vende direto para restaurantes, mercados de
bairro e consumidor final, com produto de validade curta.

## A transação que atravessa contextos: confirmar pedido
1. **Pedido** registra o pedido como pendente
2. **Produção** aloca a quantidade por FEFO e amarra o pedido a
   lotes específicos, criando a rastreabilidade
3. **Entrega** reserva um atendimento na janela de retirada
4. **Faturamento** cobra e emite a nota
5. **Pedido** confirma

O produto não é levado até o comprador: o produtor leva os pedidos a
um ponto de encontro em um horário combinado e o comprador retira
ali. Cada janela atende um número limitado de compradores, então o
passo 3 é recusado quando a janela está cheia — e o comprador
escolhe outra.

Se o passo 4 falhar (pagamento recusado), os passos 3 e 2 precisam
ser desfeitos: o atendimento é liberado e o lote volta ao estoque.
A devolução não é simétrica — o lote volta com menos validade do
que saiu, e abaixo de um limite mínimo vira quebra em vez de
retornar. Como Produção, Entrega e Faturamento terão bancos
separados, não existe ROLLBACK entre eles. Esta é a nossa SAGA.

## O fluxo assíncrono: execução da retirada
Quando **Entrega** confirma a retirada, o estoque baixa em definitivo
e a rastreabilidade fecha. Este fluxo não é compensável — produto
retirado não se desfaz. Ele exige consumidor idempotente, porque a
tela do produtor pode reenviar a confirmação após perda de sinal no
ponto de encontro.

Encerrada a janela sem baixa, o sistema não conclui que o comprador
faltou: ausência de registro não é registro de ausência, e o produtor
pode apenas ter ficado sem rede. O sistema pergunta ao produtor se a
retirada ocorreu, e a retirada fica em um estado explícito de
indefinição até a resposta. Confirmado o não comparecimento, a
retirada é reagendada se o lote ainda estiver acima do limite mínimo
de validade, ou vira quebra se estiver abaixo.

## Contextos delimitados iniciais
| Contexto | É dono de | Não é dono de |
|---|---|---|
| Pedido | pedido, cliente, estado | disponibilidade, janela |
| Produção | lote, origem, validade, reserva | preço, pedido |
| Entrega | ponto de encontro, janela, capacidade de atendimento | estoque, cobrança |
| Faturamento | cobrança, estorno, nota | disponibilidade, retirada |

## Fora de escopo nesta versão
- Custos de produção, financeiro e qualquer módulo de ERP
- Entrega no endereço do comprador, com rota e entregador
- Capacidade volumétrica do transporte (o limite da janela é de
  atendimentos, não de volume de produto)
- Múltiplos produtores no mesmo sistema
- App nativo para o produtor (é uma tela web)
