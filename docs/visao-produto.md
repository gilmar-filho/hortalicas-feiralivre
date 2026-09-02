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
3. **Entrega** reserva uma vaga na janela de entrega da rota
4. **Faturamento** cobra e emite a nota
5. **Pedido** confirma

Se o passo 4 falhar (pagamento recusado), os passos 3 e 2 precisam
ser desfeitos: a janela é liberada e o lote volta ao estoque.
A devolução não é simétrica — o lote volta com menos validade do
que saiu, e abaixo de um limite mínimo vira quebra em vez de
retornar. Como Produção, Entrega e Faturamento terão bancos
separados, não existe ROLLBACK entre eles. Esta é a nossa SAGA.

## O fluxo assíncrono: execução da entrega
Quando **Entrega** confirma a entrega, o estoque baixa em definitivo
e a rastreabilidade fecha. Este fluxo não é compensável — produto
entregue não se desfaz. Ele exige consumidor idempotente, porque o
app do entregador pode reenviar a confirmação após perda de sinal.

## Contextos delimitados iniciais
| Contexto | É dono de | Não é dono de |
|---|---|---|
| Pedido | pedido, cliente, estado | disponibilidade, rota |
| Produção | lote, origem, validade, reserva | preço, pedido |
| Entrega | janela, rota, capacidade | estoque, cobrança |
| Faturamento | cobrança, estorno, nota | disponibilidade, entrega |

## Fora de escopo nesta versão
- Custos de produção, financeiro e qualquer módulo de ERP
- Roteirização otimizada (a rota é uma lista ordenada manual)
- Múltiplos produtores no mesmo sistema
- App nativo para o entregador (é uma tela web)
