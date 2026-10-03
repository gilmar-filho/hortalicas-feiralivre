# 0006. Compose e nginx como API Gateway

## Status
Proposto

Substitui a expectativa de BFF registrada no ADR-0002: a composição
entre contextos que existe hoje já está no front, e a composição de
várias fontes para uma tela nova é papel do read model previsto para
o D6, não de uma camada intermediária que recomponha chamadas.

## Contexto
O D3 pede um Compose que suba o sistema inteiro a partir de um clone
limpo, e a aula-âncora cobre Gateway/BFF como o componente que
simplifica o que o cliente precisa saber sobre os serviços internos.
Com três serviços Spring e um front React, alguma coisa precisa ser a
única porta de entrada: publicar as quatro portas separadamente
exporia a topologia interna e obrigaria o navegador a resolver CORS
entre origens diferentes.

## Decisão
nginx é a única porta de entrada do sistema e o único serviço com
porta publicada — `compose.override.yaml` publica só `8080:80` no
`gateway`, nenhum outro arquivo de Compose publica porta nenhuma. O
mesmo nginx serve o build estático do React, então front e API ficam
na mesma origem e o `WebConfig` de CORS sai do núcleo.

O roteamento é por prefixo: `/api/produtos` vai para `producao:8082`,
`/api/retiradas` vai para `entrega:8083`, qualquer outro `/api/` vai
para `nucleo:8081`, e `/interno` devolve `404` sem alcançar serviço
nenhum — reforçando no gateway a regra do ADR-0003 de que esses
endpoints são só para tráfego entre serviços. A rota `/` serve os
estáticos com `try_files $uri /index.html`, porque o front roteia
client-side por `window.location.pathname`.

O nginx resolve o nome de cada serviço a cada requisição
(`resolver 127.0.0.11 valid=10s;` com `proxy_pass` apontando para uma
variável), em vez de resolver uma vez só na partida. Isso tem dois
efeitos: o gateway sobe mesmo que um serviço esteja fora do ar,
porque não depende de resolver o nome dele para iniciar; e, se um
serviço cai e volta com outro IP (por exemplo depois de recriado), o
gateway volta a alcançá-lo sem precisar ser reiniciado. Um serviço
fora do ar responde `502` só nas rotas que apontam para ele — as
rotas dos outros dois continuam `200`.

A imagem do gateway é construída em multi-stage: um estágio `node:22`
roda `npm ci` e `npm run build` sobre `frontend/`, e o estágio final
`nginx:1.27-alpine` só copia o `dist/` resultante e o `nginx.conf`.
Os três serviços Spring seguem o mesmo princípio de build multi-stage:
uma imagem com Maven e JDK 21 resolve dependências numa camada
reaproveitável, compila com `package -DskipTests` (os testes já
rodaram no CI), e a imagem final é `eclipse-temurin:21-jre`, só com o
jar. `spring-boot-starter-actuator` em cada serviço dá o endpoint de
healthcheck que o Compose usa para `--wait` só devolver o terminal
quando todos estiverem de fato prontos.

Alternativas descartadas: Spring Cloud Gateway resolveria o mesmo
roteamento, mas adicionaria um quarto processo Java ao Compose só para
fazer o que um nginx de poucas linhas já faz, sem necessidade de nada
que o Spring Cloud Gateway tenha e o nginx não. Um BFF dedicado foi
descartado porque hoje não há composição de múltiplas fontes para uma
mesma tela que justifique uma camada própria: a única composição que
existe (fatura com lotes) já é feita no front, buscando as duas fontes
separadamente; compor no servidor para várias telas de uma vez é
exatamente o papel do read model do D6.

## Consequências
Não existe composição programável no servidor: se duas telas
precisarem combinar dados de serviços diferentes, quem compõe hoje é
o front, fazendo duas chamadas e juntando o resultado — o que
degrada melhor quando um serviço cai (a tela mostra o que conseguiu
buscar) do que uma composição no servidor que falharia a chamada
inteira. Essa é uma limitação aceita até o D6 trazer um read model
de verdade.

`docker compose up --build --wait` é o comando recomendado para subir
o ambiente: sem o `--wait`, o terminal volta antes dos healthchecks
passarem e as primeiras chamadas ao gateway recebem `502` enquanto os
serviços ainda sobem.
