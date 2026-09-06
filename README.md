# RondaSafe

Aplicativo Android nativo para controle, execução e auditoria de rondas de porteiros, com operação offline-first, leitura de QR Code por câmera, sincronização com Supabase e administração no mesmo APK.

> O repositório continua com o nome `porteirinho`, mas o produto é **RondaSafe**.

## Status

MVP funcional implementado.

O aplicativo já contempla:

- cadastro hierárquico de prédio, bloco, andar e pontos de ronda;
- geração, substituição, revogação e impressão de QR Codes;
- cadastro de porteiros com foto, PIN temporário e PIN pessoal;
- bloqueio temporário após tentativas inválidas de PIN;
- configuração de aparelho da portaria;
- programação de rondas com dias, horários, tolerância e pontos obrigatórios;
- edição transacional das programações sem apagar histórico;
- janelas que atravessam a meia-noite;
- atribuição opcional de responsáveis por janela de ronda;
- regra definitiva de responsáveis: sem responsável específico, qualquer porteiro ativo pode executar; com um ou mais responsáveis, somente os atribuídos podem iniciar;
- início e encerramento de turno;
- execução de ronda com leitura de QR exclusivamente pela câmera;
- operação offline com cache criptografado e fila local persistente;
- sincronização automática via WorkManager;
- separação de falhas transitórias e falhas permanentes de sincronização;
- painel administrativo para diagnóstico e reenfileiramento explícito de falhas locais;
- alertas de atraso, ronda não realizada, ronda incompleta, suspeita de fraude, acesso inválido e dispositivo sem sincronização;
- detecção de leitura rápida entre pontos, alteração de relógio e QR inválido/revogado;
- histórico com filtros por status, porteiro, prédio, bloco, andar e intervalo personalizado;
- suporte correto a fuso horário do prédio e rondas cross-midnight;
- arquivamento/restauração em vez de exclusão destrutiva;
- trilha de auditoria e proteção por RLS no Supabase.

## Stack

- Kotlin Android nativo
- Jetpack Compose
- CameraX + ML Kit Barcode Scanning
- Room
- WorkManager
- Android Keystore
- Supabase Auth, Postgres, RLS, Storage e Edge Functions
- Coil para imagens
- Firebase Cloud Messaging previsto para push remoto

## Configuração Android

Crie `local.properties` a partir de `local.properties.example` e informe:

```properties
SUPABASE_URL=https://SEU-PROJETO.supabase.co
SUPABASE_PUBLISHABLE_KEY=SUA_CHAVE_PUBLICAVEL
```

O aplicativo usa `minSdk 23`, `targetSdk 37`, Java 17 e core library desugaring.

## Build

No Windows:

```bash
./gradlew.bat :app:assembleDebug
```

Em Linux/macOS:

```bash
./gradlew :app:assembleDebug
```

O CI do GitHub também executa `:app:assembleDebug` e publica o APK debug como artefato.

## Fluxo da portaria

1. Selecionar o porteiro por nome/foto.
2. Digitar PIN pessoal.
3. Iniciar turno.
4. Escolher uma ronda disponível.
5. Iniciar ronda.
6. Ler os QR Codes obrigatórios pela câmera.
7. Finalizar ronda.
8. O aplicativo encerra o turno, encerra a sessão do porteiro e retorna à seleção de porteiro.

A operação continua localmente sem internet. Eventos permanecem no aparelho até confirmação do servidor.

## Responsáveis por ronda

A atribuição de responsável é opcional.

- Sem responsável específico: qualquer porteiro ativo pode realizar a ronda.
- Com um ou mais responsáveis: somente os porteiros atribuídos podem ver/iniciar a ronda.

Essa regra é validada tanto na disponibilidade quanto no início da ronda no servidor e na sincronização offline.

## Segurança e antifraude

- QR lido somente pela câmera;
- QR opaco e armazenado localmente apenas como hash durante sincronização pendente;
- timestamps local/servidor e relógio monotônico;
- detecção de alteração relevante de relógio;
- detecção de deslocamento rápido entre checkpoints;
- QR revogado/desconhecido gera sinalização;
- dispositivo precisa estar provisionado e ativo;
- PIN offline possui bloqueio local criptografado;
- falhas de autenticação do servidor não caem silenciosamente para PIN offline;
- antifraude sinaliza anomalias sem bloquear rotas legítimas flexíveis, exceto credenciais/dispositivos/QR inválidos.

## Offline e sincronização

O Room local mantém:

- turno local ativo;
- ronda local ativa;
- checkpoints visitados;
- fila de eventos pendentes.

Estados da fila:

- `PENDING`: aguarda sincronização/retry;
- `FAILED_PERMANENT`: erro não repetível, preservado no aparelho para revisão administrativa.

O administrador pode visualizar e reenfileirar explicitamente falhas permanentes. Nenhum registro é apagado antes de ACK do servidor.

## Supabase

O backend utiliza funções/RPCs, RLS, Storage e Edge Functions. Entre os controles existentes estão:

- `portaria-ops` para operações online da portaria;
- `portaria-cache` para cache operacional criptografado no aparelho;
- `offline-ingest` para ingestão idempotente de eventos offline;
- `admin-guards`, `admin-devices` e `admin-qr` para operações administrativas sensíveis;
- `scan_operational_alerts()` executado periodicamente via `pg_cron`.

## O que ainda depende de configuração externa

O código principal do MVP está funcional. Antes de produção, ainda existem itens que não podem ser ativados apenas pelo repositório:

1. **Firebase Cloud Messaging**: criar/configurar um projeto Firebase, registrar o app Android e disponibilizar as credenciais necessárias para envio de push. Os alertas internos do RondaSafe já funcionam independentemente do FCM.
2. **Proteção contra senhas vazadas no Supabase Auth**: habilitar no painel do Supabase.
3. **Dados operacionais reais**: cadastrar prédio, locais, checkpoints, porteiros, aparelho da portaria e programações para realizar o teste E2E final em dispositivo físico.

Veja o checklist detalhado em [docs/PRODUCTION_CHECKLIST.md](docs/PRODUCTION_CHECKLIST.md).

## Documentação

- [Especificação funcional e técnica](docs/RONDASAFE_SPEC.md)
- [Checklist de produção](docs/PRODUCTION_CHECKLIST.md)
