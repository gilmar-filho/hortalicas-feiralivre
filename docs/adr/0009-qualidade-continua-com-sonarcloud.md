# 0009. Qualidade contínua com SonarCloud

## Status
Proposto

## Contexto
O CI responde uma pergunta só: quebrou ou não quebrou. Ele não diz se um
método cresceu demais, se o mesmo trecho está copiado em três lugares,
se uma condição nunca é verdadeira ou se uma classe inteira entrou sem
teste nenhum. São defeitos que não derrubam build e aparecem meses
depois, quando alguém precisa mexer naquilo — e é exatamente o que a
disciplina chama de dívida técnica, com o SonarCloud listado entre as
ferramentas obrigatórias.

Duas coisas atrapalhavam adotá-lo aqui. A primeira é que o projeto são
três builds Maven independentes (`nucleo`, `producao`, `entrega`), sem
nenhum pom na raiz: o Sonar enxergaria três análises soltas, sem visão
do todo. A segunda é que a cobertura de teste não era medida em lugar
nenhum — os testes rodam e passam, mas ninguém sabia que porcentagem do
código eles tocam, e cobertura é metade do que o Sonar avalia.

## Decisão
Um `pom.xml` na raiz, **agregador e não pai**. Ele apenas lista os três
serviços em `<modules>`; os três continuam filhos do
`spring-boot-starter-parent` e nenhum deles muda de parent, de versão ou
de dependência. A alternativa — tornar a raiz o pai de todos — daria
gestão central de versões, mas mexeria nos três poms que já funcionam,
em troca de um ganho que este projeto ainda não precisa. `e2e` e
`frontend` ficam fora da análise: o primeiro só existe para subir o
Compose e o segundo não é Java.

O `jacoco-maven-plugin` entra nos três serviços, com `prepare-agent`
antes dos testes e `report` na fase `verify`. É ele que produz o dado de
cobertura que o Sonar lê.

No `ci.yml`, o job `Qualidade (SonarCloud)` roda `mvn verify` a partir
da raiz e em seguida o scanner, com `fetch-depth: 0` porque o Sonar usa
o histórico do Git para separar código novo de código antigo. O token
vem do segredo `SONAR_TOKEN` do repositório, nunca versionado.

**O job se pula sozinho enquanto o segredo não existir, terminando
verde.** Sem essa guarda, o commit que introduz o arquivo deixaria o CI
vermelho em todo PR até alguém terminar de criar a conta — e "main
sempre verde" cairia junto, por causa de uma ferramenta que existe para
melhorar a qualidade.

## Consequências
A análise passa a depender de uma conta externa e de um segredo no
repositório. Enquanto eles não existirem, o job é um no-op explícito:
não protege nada, mas também não atrapalha ninguém. Quem administra o
repositório precisa importar o projeto no SonarCloud, guardar o token
como `SONAR_TOKEN` e preencher `sonar.organization` e
`sonar.projectKey` no pom da raiz — até lá, os dois ficam com valores
de marcação.

A cobertura passa a ser um número visível, e o primeiro já é conhecido:
86% em núcleo, 97% em Produção e 96% em Entrega, por linha. O build da
raiz foi verificado com JDK 21 e Maven 3.9.16 e passa nos quatro
módulos.

O quality gate do SonarCloud ainda não bloqueia nada — o job falha por
erro de análise, não por nota baixa. Transformá-lo em porta de entrada
da `main` é uma decisão para depois de ver o primeiro relatório, porque
um portão calibrado antes de conhecer a régua reprova trabalho honesto.

O build da raiz roda os três serviços de uma vez, o que torna o job da
qualidade mais lento que os de teste, que rodam em paralelo por serviço.
É aceitável porque ele não está no caminho crítico de ninguém.
