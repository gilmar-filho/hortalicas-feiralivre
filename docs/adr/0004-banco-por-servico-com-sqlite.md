# 0004. Banco por serviço com SQLite

## Status
Proposto

Supera do ADR-0001 a decisão de banco SQLite compartilhado entre os
contextos do então backend único.

## Contexto
*Database per Service* é tema explícito da aula-âncora do D3 (slides
13, 16 e 19): cada serviço extraído deve ser dono exclusivo dos seus
dados, sem outro serviço lendo ou escrevendo direto no seu banco. Um
banco compartilhado entre núcleo, Produção e Entrega, mesmo com os
três rodando como processos separados, seria o "monólito distribuído"
que o slide 12 descreve como o resultado errado da decomposição: os
três continuariam travados num único esquema, uma única migração, um
único arquivo que qualquer um pode corromper para os outros dois.

O projeto já usa SQLite com `org.xerial:sqlite-jdbc`, e o SQL do
código atual usa funções e pragmas próprios do SQLite (`date('now',
...)`, `PRAGMA foreign_keys`). Trocar de banco neste ponto do projeto
reescreveria SQL sem relação com o tema do D3.

## Decisão
Cada serviço tem o próprio arquivo SQLite: `nucleo.db`, `producao.db`
e `entrega.db`, em `./data/<servico>.db` fora do Docker e num volume
nomeado do Compose dentro dele (`nucleo-dados`, `producao-dados`,
`entrega-dados`). Nenhum serviço abre o arquivo de outro; a única
forma de um serviço saber algo sobre o estado de outro é por chamada
HTTP aos endpoints que esse outro expõe.

Referências entre serviços são só por id, nunca por chave estrangeira
atravessando bancos — um `usuario_id` em `produto` ou um
`comprador_id` em `reserva_atendimento` não tem `FOREIGN KEY` para
`usuario`, porque não há como o SQLite garantir essa restrição contra
um arquivo que ele não enxerga. A consistência entre esses ids deixa
de ser garantida pelo banco e passa a depender do fluxo da aplicação
— o preço de qualquer *Database per Service*.

Alternativa descartada: Postgres por serviço. Resolveria o mesmo
problema de isolamento, mas exigiria reescrever o SQL que hoje depende
de sintaxe do SQLite, sem nenhum ganho para o que o D3 pede provar.
Fica registrada como opção para quando um serviço precisar de
concorrência de escrita que o SQLite não oferece.

## Consequências
Nenhum banco antigo é lido pelos serviços novos: `nucleo.db`,
`producao.db` e `entrega.db` nascem vazios e carregados pelos seeds
de cada serviço, então não existe migração dos dados de
`feira-livre-v2.db`. Esse arquivo fica documentado no README como não
lido mais, e pode ser apagado.

O banco não é um container à parte: ele é um arquivo dentro do volume
do próprio serviço, e não pode ser reiniciado ou inspecionado
separado do processo Spring Boot que o abre. O Outbox previsto para o
D4 continua possível dentro deste desenho — ele seria uma tabela a
mais no mesmo arquivo SQLite do serviço que publica o evento, sem
exigir infraestrutura nova além da já decidida aqui.
