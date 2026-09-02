# 0000. Registrar decisões arquiteturais como ADR

## Status
Aceito

## Contexto
O projeto vai evoluir de um monólito modular para um sistema
distribuído ao longo de oito quinzenas, com decisões de arquitetura
tomadas por pessoas diferentes em momentos diferentes — escolha entre
SAGA coreografada e orquestrada, forma de particionar contextos,
tecnologia de broker, entre outras. Sem um registro, a razão por trás
de uma decisão se perde assim que quem decidiu deixa de estar na
conversa, e decisões acabam sendo contestadas ou revertidas sem que
ninguém saiba por que foram tomadas daquele jeito.

## Decisão
Toda decisão de arquitetura com impacto entre contextos delimitados —
escolha de padrão (SAGA, CQRS, Event Sourcing, Circuit Breaker),
tecnologia compartilhada (broker, formato de mensagem) ou mudança de
contrato entre serviços — é registrada como um Architecture Decision
Record (ADR) em `docs/adr/`.

Convenções:
- Arquivo: `docs/adr/NNNN-titulo-curto-em-kebab-case.md`, numeração
  sequencial a partir de 0001 (este documento é o 0000).
- Formato: Título, Status (Proposto / Aceito / Rejeitado / Superado
  por ADR-NNNN), Contexto, Decisão, Consequências.
- Um ADR aceito não é editado depois — se a decisão muda, um novo ADR
  é criado e referencia o anterior como superado.
- Toda decisão registrada passa por revisão de pelo menos uma pessoa
  da equipe diferente de quem propôs, via pull request.

## Consequências
Decisões ficam rastreáveis e defensáveis nas arguições dos encontros —
a equipe consegue mostrar não só o que foi construído, mas por que foi
construído daquele jeito. O custo é a disciplina de escrever o ADR
antes de considerar a decisão fechada, não depois de já implementada.
