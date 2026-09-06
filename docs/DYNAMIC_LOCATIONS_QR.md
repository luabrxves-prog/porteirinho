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
- desativar ou excluir logicamente um ponto;
- desativar ou excluir logicamente um andar;
- consultar histórico de QR Codes antigos.

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

## Troca de QR Code

Fluxo recomendado no admin:

1. Abrir o ponto de ronda.
2. Tocar em **Substituir QR Code**.
3. Confirmar a operação.
4. O QR atual é marcado como revogado.
5. Um novo token criptograficamente aleatório é criado.
6. O app gera a imagem do novo QR.
7. O QR antigo deixa de ser válido para novas rondas após a sincronização das regras.
8. O histórico permanece preservado.

Não é necessário excluir o andar para trocar um QR Code.

## Exclusão e auditoria

Por segurança e rastreabilidade, registros que já possuem histórico de ronda não devem ser apagados fisicamente do banco.

A interface poderá mostrar a ação **Excluir**, mas internamente o comportamento padrão será arquivamento/soft delete:

- `active = false`
- `archived_at`
- `archived_by`

Um registro poderá ser apagado fisicamente apenas se nunca tiver sido referenciado por ronda, leitura ou auditoria.

Isso vale para:

- blocos;
- andares;
- pontos de ronda;
- QR Codes.

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

### `blocks`

- `id uuid PK`
- `building_id uuid FK`
- `name`
- `sort_order`
- `active`
- `created_at`
- `archived_at nullable`

### `floors`

- `id uuid PK`
- `block_id uuid FK`
- `name`
- `sort_order`
- `active`
- `created_at`
- `archived_at nullable`

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
- Arquivar/Excluir

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
6. Ao substituir um QR, o QR anterior fica revogado e o histórico permanece.
7. Trocar um QR não apaga o andar nem o ponto.
8. Um andar/ponto com histórico pode ser arquivado, mas seu histórico não é perdido.
9. Programações de ronda utilizam pontos cadastrados dinamicamente.
10. A tela de ronda mostra progresso `visitados / obrigatórios` de forma dinâmica.
