# Repositório do Projeto - Hortaliças Feira Livre

Este repositório contém o código-fonte e a documentação do projeto desenvolvido para a disciplina **GCC267 - Sistemas de Informação** da Universidade Federal de Lavras (UFLA). O objetivo é construir um sistema distribuído a partir da quebra de um monólito modular, aplicando conceitos de arquitetura de microsserviços, SAGA, CI/CD e resiliência.

## 📋 Visão do Produto

O sistema visa resolver o problema de rastreabilidade e gestão de validade de lotes para pequenos produtores de alimentos perecíveis de ciclo curto, estruturado em contextos delimitados independentes.

📖 **Para entender em detalhes o problema, o público-alvo e a transação principal (Nossa SAGA), acesse o documento completo:**

👉 [Visão do Produto](docs/visao-produto.md)

## 📐 Decisões Arquiteturais (ADRs)

Todas as decisões técnicas com impacto entre os contextos do sistema são documentadas formalmente para manter o histórico claro para toda a equipe.

📖 **Para ler nossas decisões e convenções arquiteturais, acesse:**

👉 [Registro de Decisões (ADRs)](docs/adr/0000-registro-de-decisoes.md)

## ⚙️ Tecnologias Utilizadas

O projeto adota práticas de **CI/CD em monorepo**, garantindo que o código na branch `main` esteja sempre testado e funcional.

- **GitHub Actions:** CI automatizado (Build, testes por serviço, ponta a ponta e Lint).
- **Docker & Docker Compose:** quatro containers — gateway nginx, núcleo, Produção e Entrega — sobem com um único comando.
- **Banco de dados:** um arquivo SQLite por serviço (*Database per Service*).
- **Contratos:** OpenAPI 3.0.3 contract-first em [`contracts/`](contracts/), testados nos dois lados.
- **Mensageria:** a definir (D4).

## 🏗️ Arquitetura

```text
                  ┌──────────────────────────────┐
navegador ──8080──▶ gateway (nginx + build React) │
                  └──┬────────────┬───────────┬──┘
           /api/produtos   /api/retiradas   /api/*
                     ▼            ▼           ▼
               ┌─────────┐  ┌─────────┐  ┌─────────┐
               │producao │  │entrega  │  │nucleo   │
               │  :8082  │  │  :8083  │  │  :8081  │
               └────┬────┘  └────┬────┘  └──┬───┬──┘
                    │            │   /interno│   │/interno
                    │            ◀───────────┘   │
                    ◀────────────────────────────┘
               producao.db   entrega.db      nucleo.db
```

O núcleo reúne Pedido, Faturamento e Usuário, e chama Produção e
Entrega por REST síncrono, direto pelo nome do serviço na rede do
Compose. Os endpoints `/interno/**` não passam pelo gateway.

📖 **Contratos dos serviços extraídos:**
[Produção](contracts/producao.yaml) · [Entrega](contracts/entrega.yaml)

📖 **ADRs desta extração:** [0003 — Extração de Produção e
Entrega](docs/adr/0003-extracao-de-producao-e-entrega.md) ·
[0004 — Banco por serviço com SQLite](docs/adr/0004-banco-por-servico-com-sqlite.md) ·
[0005 — Contratos OpenAPI contract-first](docs/adr/0005-contratos-openapi-contract-first.md) ·
[0006 — Compose e nginx como API Gateway](docs/adr/0006-compose-e-nginx-como-api-gateway.md) ·
[0007 — Testes depois da extração](docs/adr/0007-testes-depois-da-extracao.md)

## ▶️ Como Utilizar

1. Clone o repositório:

```bash
git clone https://github.com/gilmar-filho/hortalicas-feiralivre.git
cd hortalicas-feiralivre
```

1. Suba tudo com Docker (constrói as quatro imagens e espera os
   serviços ficarem saudáveis antes de devolver o terminal):

```bash
docker compose up --build --wait
```

Depois disso, abra `http://localhost:8080`.

1. Para desenvolver o frontend separadamente, com o Compose no ar:

```bash
cd frontend && npm run dev
```

O Vite repassa `/api` para `http://localhost:8080` (o gateway).

1. Testes de cada serviço:

```bash
cd servicos/<servico> && mvn test   # nucleo, producao ou entrega
```

1. Teste ponta a ponta (sobe o Compose inteiro com Testcontainers):

```bash
cd e2e && mvn verify
```

1. Verifique os workflows de CI:

Qualquer Pull Request aberto passará automaticamente pelas verificações de `Build` (arquivos obrigatórios), `Testes` (matriz por serviço), `Ponta a ponta` e `Lint` (formatação markdown) configuradas no arquivo `.github/workflows/ci.yml`.

> No dia da apresentação, construa as imagens com antecedência: o
> primeiro `--build` de um clone limpo baixa as dependências de três
> projetos Maven mais o npm, e a rede da sala pode não ser confiável.

## 🗄️ Banco de dados local

Cada serviço guarda o próprio banco SQLite em um volume do Compose
(`nucleo-dados`, `producao-dados`, `entrega-dados`), ou em
`servicos/<servico>/data/<servico>.db` quando roda fora do Docker. Os
arquivos antigos `servicos/nucleo/data/feira-livre.db` e
`servicos/nucleo/data/feira-livre-v2.db` não são mais lidos por nenhum
serviço e podem ser apagados. Para zerar os dados do Compose:
`docker compose down -v`.

## 👥 Equipe e Organização

| Membro | GitHub |
| :---: | :---: |
| gilmar-filho | [@gilmar-filho](https://github.com/gilmar-filho) |
| juliaaribeiro | [@juliaaribeiro](https://github.com/juliaaribeiro) |
| FixRuan | [@FixRuan](https://github.com/FixRuan) |
| SamuVanoni | [@SamuVanoni](https://github.com/SamuVanoni) |

📖 **Para saber mais sobre os papéis de cada membro e como a equipe rotaciona responsabilidades ao longo do semestre, acesse:**

👉 [Organização da Equipe](docs/equipe.md)

## 📄 Licença

Este projeto está licenciado sob a licença **MIT**.
