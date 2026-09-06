# RondaSafe — Cadastro dinâmico de locais e QR Codes

Esta decisão substitui a premissa inicial de 28 pontos fixos. O RondaSafe não deve nascer com uma estrutura rígida de 1 QR por pavimento.

## Objetivo

Permitir que o administrador configure a estrutura física pelo próprio APK, sem alterar código ou banco manualmente.

O administrador poderá:

- criar blocos;
- criar andares/pavimentos dentro de um bloco;
- criar um ou vários pontos de ronda dentro do mesmo andar;
- gerar QR Code para cada ponto;
- visualizar e compartilhar o QR gerado para impressão;
- substituir/rotacionar o QR de um ponto;
- revogar um QR antigo;
- arquivar blocos, andares e pontos que não são mais utilizados;
- consultar histórico de QR Codes antigos.

O sistema **não terá exclusão física de locais pelo aplicativo administrativo**. Registros deixam de aparecer na operação normal quando arquivados, mas permanecem no banco para preservar histórico e auditoria.

## Hierarquia

```text
Bloco
  └── Andar/Pavimento
       ├── Ponto de ronda
       │    ├── QR atual
       │    └── histórico de QRs
       └── Ponto de ronda
            ├── QR atual
            └── histórico de QRs
```

Exemplo:

```text
Bloco A
  └── 5º andar
       ├── Hall dos elevadores
       └── Escada de emergência
```

Nesse exemplo existem dois pontos obrigatórios no mesmo andar, portanto dois QR Codes distintos.

## Separação entre andar, ponto e QR

O sistema não deve tratar um QR Code como se fosse o próprio andar.

- `floor` representa o pavimento.
- `checkpoint` representa o local físico que precisa ser visitado.
- `qr_token` representa uma credencial física vinculada ao ponto.

Essa separação permite trocar um QR sem apagar o ponto, o andar ou o histórico de rondas.

## Substituição de QR Code

A substituição é uma operação sensível e **nunca deve acontecer com um único toque**.

Fluxo obrigatório no admin:

1. Abrir o ponto de ronda.
2. Tocar em **Substituir QR Code**.
3. Abrir uma caixa/modal de confirmação.
4. Informar claramente que o QR atual será revogado e deixará de ser válido para novas rondas.
5. Oferecer os botões **Cancelar** e **Substituir QR Code**.
6. Nenhuma alteração é realizada ao tocar em **Cancelar** ou fechar a confirmação.
7. Somente após confirmação explícita o QR atual é marcado como revogado.
8. Um novo token criptograficamente aleatório é criado.
9. O app gera a imagem do novo QR.
10. O histórico do QR anterior permanece preservado.

### Confirmação recomendada

```text
Substituir QR Code?

O QR Code atual deste ponto será revogado e não poderá mais ser usado em novas rondas após a atualização dos dispositivos.

O histórico de leituras anteriores será preservado.

[ Cancelar ]   [ Substituir QR Code ]
```

O botão de confirmação deve ter aparência de ação sensível e não deve ser acionado automaticamente ao abrir a janela.

Após a substituição, exibir mensagem de sucesso e o novo QR Code:

```text
QR Code substituído com sucesso.
O código anterior foi revogado.
```

Não é necessário arquivar ou recriar o andar/ponto para trocar um QR Code.

## Arquivamento e auditoria

A regra oficial do RondaSafe é **arquivar, não excluir**.

Quando um bloco, andar ou ponto deixa de ser utilizado:

- `active = false`
- `archived_at`
- `archived_by`

O item arquivado:

- não aparece na seleção normal de novas rondas;
- não pode receber novos QR Codes ou novas leituras enquanto estiver arquivado;
- continua disponível em consultas históricas e auditoria;
- preserva todas as rondas, leituras e QR Codes anteriormente associados.

A interface administrativa deve usar o termo **Arquivar**, e não **Excluir**, para deixar claro que o histórico não será perdido.

QR Codes antigos devem ser preservados como `REVOKED` para que uma tentativa de leitura futura seja identificada como token revogado, e não apenas como QR desconhecido.

## Geração do QR dentro do APK

O administrador não precisa usar sites externos de geração de QR Code.

Fluxo:

```text
Novo ponto
   ↓
Salvar ponto
   ↓
Gerar QR Code
   ↓
Backend cria token aleatório
   ↓
App recebe o token
   ↓
APK renderiza a imagem do QR
   ↓
Visualizar / Compartilhar / Salvar para impressão
```

O token deve ser criado em uma operação privilegiada no backend e não a partir de nomes previsíveis como `BLOCO-A-5-ANDAR`.

Formato conceitual:

```text
rondasafe:v1:<token-aleatorio>
```

## Modelo de dados revisado

### `buildings`

Opcional para o MVP, mas recomendado para permitir expansão futura para mais de um prédio.

- `id uuid PK`
- `name`
- `active`
- `created_at`
- `archived_at nullable`
- `archived_by nullable`

### `blocks`

- `id uuid PK`
- `building_id uuid FK`
- `name`
- `sort_order`
- `active`
- `created_at`
- `archived_at nullable`
- `archived_by nullable`

### `floors`

- `id uuid PK`
- `block_id uuid FK`
- `name`
- `sort_order`
- `active`
- `created_at`
- `archived_at nullable`
- `archived_by nullable`

Exemplos de `name`: `Garagem`, `Térreo`, `Play`, `1º andar`, `11º andar`.

### `checkpoints`

- `id uuid PK`
- `floor_id uuid FK`
- `name`
- `description nullable`
- `sort_order`
- `active`
- `created_at`
- `archived_at nullable`
- `archived_by nullable`

Exemplos: `Hall dos elevadores`, `Escada de emergência`, `Entrada da garagem`.

### `qr_tokens`

- `id uuid PK`
- `checkpoint_id uuid FK`
- `token_hash` ou identificador seguro
- `version`
- `status` (`ACTIVE`, `REVOKED`)
- `created_at`
- `revoked_at nullable`
- `created_by`
- `revoked_by nullable`

Regra: um checkpoint possui no máximo um QR `ACTIVE` por vez, mas pode possuir vários QRs históricos revogados.

## Programação de rondas

Uma programação de ronda não deve depender de uma quantidade fixa como 28.

Ela referencia uma lista de `checkpoints` obrigatórios.

Exemplo:

```text
Ronda noturna
- Bloco A / Térreo / Hall
- Bloco A / 5º / Hall dos elevadores
- Bloco A / 5º / Escada de emergência
- Bloco B / Garagem / Portão
```

O progresso deve ser dinâmico:

```text
7 / 34 pontos visitados
```

em vez de `7 / 28` fixo.

## Telas administrativas revisadas

### Locais

Hierarquia navegável:

```text
Prédio
  → Blocos
    → Andares
      → Pontos de ronda
```

Ações:

- Novo bloco
- Novo andar
- Novo ponto
- Editar
- Arquivar

### Detalhe do ponto

Exibe:

- bloco;
- andar;
- nome do ponto;
- status;
- QR atual;
- versão do QR;
- data de geração;
- histórico de QRs anteriores.

Ações:

- Gerar QR;
- Visualizar QR;
- Compartilhar/salvar;
- Substituir QR;
- Revogar QR;
- Editar ponto;
- Arquivar ponto.

## Regras de aceitação

1. O APK não possui quantidade fixa de blocos, andares ou pontos.
2. O administrador consegue criar um novo andar sem atualização do aplicativo.
3. O administrador consegue criar dois ou mais pontos no mesmo andar.
4. Cada ponto consegue possuir um QR ativo próprio.
5. O próprio APK gera e exibe o QR após criação do token no backend.
6. Ao tocar em **Substituir QR Code**, nenhuma mudança ocorre antes de uma confirmação explícita.
7. Ao cancelar a confirmação de substituição, o QR atual continua exatamente como estava.
8. Após confirmar a substituição, o QR anterior fica revogado e o histórico permanece.
9. Trocar um QR não arquiva nem recria o andar ou o ponto.
10. Blocos, andares e pontos são arquivados, nunca excluídos fisicamente pelo APK.
11. Um item arquivado permanece disponível em histórico e auditoria.
12. Programações de ronda utilizam pontos cadastrados dinamicamente.
13. A tela de ronda mostra progresso `visitados / obrigatórios` de forma dinâmica.
