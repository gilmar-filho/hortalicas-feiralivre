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

- **GitHub Actions:** CI automatizado (Build & Lint).
- **Docker & Docker Compose:** (Infraestrutura a definir nas próximas quinzenas).
- **Mensageria & Bancos de Dados:** (A definir).

## ▶️ Como Utilizar

1. Clone o repositório:

```bash
git clone https://github.com/gilmar-filho/hortalicas-feiralivre.git
cd hortalicas-feiralivre
```

1. Verifique os workflows de CI:

Qualquer Pull Request aberto passará automaticamente pelas verificações de segurança de `Build` (arquivos obrigatórios) e `Lint` (formatação markdown) configuradas no arquivo `.github/workflows/ci.yml`.

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
