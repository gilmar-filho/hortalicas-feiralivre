# 0003. Extração de Produção e Entrega em serviços próprios

## Status
Proposto

Supera do ADR-0001 a decisão de backend único: Produção e Entrega
deixam de ser módulos do mesmo processo e do mesmo banco e passam a
ser serviços independentes, com Pedido, Faturamento e Usuário
permanecendo juntos no núcleo.

## Contexto
O D3 pede contratos OpenAPI dos dois serviços e um Compose que suba
tudo, sob a aula-âncora de decomposição, *Database per Service* e
Gateway/BFF. Um contrato que ninguém serve não tem prova, e um Compose
do monólito não exercita nada do tema da aula: a extração é o que dá
sentido ao restante do entregável.

Os slides 12 e 17 situam os serviços extraídos na Sprint 2 (E4–E7), e
esta extração é antecipada em relação a esse cronograma. A
antecipação é deliberada: o D4 (evento via Outbox, consumidor
idempotente) já pressupõe serviços separados, e entregar o contrato
de um serviço sem o serviço que o cumpre não teria prova nenhuma. A
corrida pela última vaga, deixada em aberto pelo ADR-0002 para este
documento, também só pode ser resolvida depois que se sabe se Entrega
continua no mesmo processo que Pedido.

## Decisão

### Critério de extração
Produção e Entrega são extraídos; Pedido, Faturamento e Usuário
permanecem no núcleo. O critério é o acoplamento medido, não a conta
de tabelas: Entrega só toca as próprias tabelas desde o ADR-0002, sem
nenhuma dependência de leitura ou escrita em outro contexto; Produção
corrige nesta mesma extração o FEFO que hoje mora em `PedidoService`,
e passa a ser dona completa da regra que já deveria ser sua. Os dois
são, não por coincidência, os dois passos compensáveis identificados
pelo ADR-0002 para a SAGA do D5 — um contexto que vai precisar de
compensação remota no D5 já precisa de fronteira de serviço antes
disso. Faturamento foi descartado por depender de seis tabelas
alheias (pedido, item_pedido, produto, lote, reserva_estoque,
usuário) só para montar a lista de produtos e lotes de uma fatura;
resolver isso exigiria um read model, que é o problema do D6, não
deste documento.

### Tabelas e `item_pedido`
As tabelas de Produção (`produto`, `lote`, `reserva_estoque`) e de
Entrega (`local_retirada`, `horario_retirada`, `reserva_atendimento`)
vão para os bancos dos respectivos serviços. `item_pedido`, que fica
no núcleo, ganha `produto_nome` e `vendedor_id`: um snapshot gravado
na criação do pedido a partir da resposta de Produção, porque o
núcleo deixa de poder fazer `JOIN` com a tabela `produto` depois da
extração. O efeito colateral é também uma correção: hoje renomear um
produto muda o nome em pedidos já feitos; com o snapshot, a fatura
mostra o nome de quando o pedido foi criado.

### Projetos independentes
`servicos/nucleo`, `servicos/producao` e `servicos/entrega` são três
projetos Maven independentes no mesmo monorepo, sem `pom` pai e sem
módulo comum. Compartilhar um módulo comum criaria acoplamento de
build entre serviços que a extração existe para remover; código
duplicado entre eles (por exemplo helpers de conversão de `Map`) é
copiado, não extraído para uma biblioteca.

### `/interno` fora do gateway
Cada serviço expõe, além dos endpoints públicos que o front já usa,
endpoints `/interno/**` que só o núcleo chama. O gateway nunca roteia
esse prefixo — devolve `404` para ele — porque são operações que só
fazem sentido entre serviços (reservar estoque por um pedido que já
existe, verificar disponibilidade de uma janela) e expô-las ao
público seria superfície de ataque sem benefício.

### Fluxo de `criar()` do pedido
`PedidoService.criar()` passa a, nesta ordem: ler o produto em
Produção; verificar disponibilidade em Entrega; reservar um id de
pedido em `pedido_sequencia`; reservar o estoque em Produção; reservar
o atendimento em Entrega; e só então gravar `pedido`, `item_pedido` e
`fatura` numa transação local curta. A verificação de disponibilidade
antes de tocar em Produção é a pré-checagem: ela existe para que a
disputa pela última vaga, quando acontece, aconteça antes do estoque
ser mexido, não depois — reduz a janela de corrida de minutos (o
tempo da página aberta no navegador) para milissegundos (o tempo
entre a pré-checagem e a reserva real).

O id do pedido é reservado em `pedido_sequencia` com `UPDATE ... SET
valor = valor + 1` seguido de `SELECT valor`, numa transação própria
com commit imediato, antes de qualquer chamada remota. Nenhuma
transação local do núcleo fica aberta durante uma chamada remota: no
SQLite, mesmo uma leitura dentro de transação segura o lock até o
commit, e com um serviço lento toda escrita do núcleo falharia com
"database is locked". Por isso as leituras locais rodam sem
transação, as chamadas remotas vêm depois, e a escrita local fica
numa transação curta só no fim.

### Id nunca reusado
A tabela `pedido_sequencia` existe porque `pedido.id` deixa de ter
`AUTOINCREMENT`. Com `AUTOINCREMENT` e um rollback no meio do fluxo, o
SQLite reutilizaria o id da próxima tentativa, e uma reserva órfã
deixada em Produção ou Entrega por uma tentativa anterior passaria a
pertencer ao pedido seguinte — a fatura dele mostraria lotes de outro
produto, e o índice único de Entrega travaria a criação de pedidos
de vez. Reservar o id antes das chamadas remotas, e nunca reusá-lo,
elimina essa classe de erro ao custo de ids órfãos presos, tratado na
seção de consequências.

### `transaction_mode=IMMEDIATE`
Os três serviços usam `transaction_mode=IMMEDIATE` no datasource
SQLite: o lock de escrita é tomado desde o `BEGIN`, e o `busy_timeout`
padrão do driver (3000 ms) faz a segunda requisição concorrente
esperar em vez de falhar. A corrida pela última vaga em Entrega e a
disputa pelo mesmo lote em Produção resolvem localmente assim, sem
precisar de coordenação entre serviços: a segunda requisição relê o
estado e recebe `409` limpo, não `500`. No núcleo, o mesmo modo é o
que garante que duas reservas de id concorrentes na sequência nunca
leem o mesmo valor.

### Devolução de estoque sem `vendido`
A devolução de reserva em Produção deixa de receber uma flag
`vendido`. Pela visão de produto, o estoque só baixa em definitivo
quando a retirada é confirmada, fluxo que ainda não existe; fatura
aprovada é pagamento, não baixa física. Isso corrige um bug do código
atual: `devolverReserva(..., vendido=true)` decrementa
`quantidade_vendida`, que nunca é incrementada em lugar nenhum, e
deixa `quantidade_reservada` artificialmente inflada depois de
qualquer cancelamento.

## Consequências
A corrida residual — dois pedidos passando juntos pela pré-checagem e
disputando a última vaga, com o perdedor recusado depois de já ter
reservado estoque sob um id que nunca chega a virar pedido — deixa
estoque preso, mas isolado: nenhum pedido herda essa reserva e o
front nunca a exibe, porque só consulta reservas de ids que vieram do
núcleo. O mesmo acontece se um serviço ou o núcleo cair entre a
reserva de estoque e a gravação final do pedido. As duas situações
ficam aceitas e documentadas até a SAGA do D5 resolvê-las por
compensação.

Sem timeout no `RestClient` até o D7, um serviço travado prende
apenas a requisição que o chamou, nunca o banco do núcleo — porque
nenhuma transação local atravessa a chamada remota. O custo de manter
três projetos Maven independentes, com duplicação de pequenos
helpers, é aceito em troca de eliminar o acoplamento de build que um
módulo comum entre serviços estabelece.
