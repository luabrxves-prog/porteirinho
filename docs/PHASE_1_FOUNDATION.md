# RondaSafe — Fase 1: Fundação

## Status

Iniciada em 06/09/2026.

## Supabase

Projeto conectado: `vnammkljeterezqtpvvm`.

### Estrutura criada

19 tabelas públicas com RLS habilitado:

- `profiles`
- `buildings`
- `blocks`
- `floors`
- `checkpoints`
- `qr_tokens`
- `qr_token_secrets`
- `guards`
- `guard_credentials`
- `devices`
- `device_credentials`
- `patrol_templates`
- `patrol_template_checkpoints`
- `shifts`
- `patrol_runs`
- `patrol_run_checkpoints`
- `patrol_scans`
- `alerts`
- `audit_logs`

### Regras já aplicadas

- nenhum bloco/andar/ponto é pré-cadastrado;
- estrutura de locais é dinâmica;
- arquivamento preserva histórico;
- QR Codes possuem versão e status `ACTIVE`/`REVOKED`;
- somente um QR pode estar ativo por ponto;
- segredos de QR ficam separados dos metadados administrativos;
- credenciais de PIN ficam separadas dos dados do porteiro;
- PIN nunca será armazenado em texto puro;
- IDs de eventos do cliente possuem unicidade para idempotência de sincronização;
- RLS está ativo em todas as tabelas públicas;
- `anon` não possui acesso às tabelas do sistema;
- administradores autenticados recebem somente os grants necessários;
- autorização administrativa usa `app_metadata.role = admin`, não `user_metadata`;
- tabelas sensíveis de credenciais não são acessíveis pelo cliente admin;
- `service_role` fica reservado para operações confiáveis de backend/Edge Functions.

### Validação

- Postgres: 17.6
- tabelas públicas: 19
- tabelas públicas com RLS: 19
- Advisor de segurança: **0 alertas** após os ajustes.

O Advisor de performance apresentou recomendações de índices adicionais e otimização de expressão em políticas RLS. Elas não representam falhas de segurança e serão tratadas incrementalmente antes de carga real de produção.

## Android

Estrutura inicial criada em Kotlin/Jetpack Compose.

### Configuração

- package: `com.rondasafe.app`
- minSdk: 23
- compileSdk / targetSdk: 37
- Java/JDK: 17
- Compose BOM: `2026.08.00`
- CameraX: `1.6.1`
- ML Kit Barcode Scanning bundled: `17.3.0`
- Room: `2.8.4`
- WorkManager: `2.11.2`
- DataStore: `1.2.1`
- Supabase Kotlin BOM: `3.7.0`
- Ktor Android: `3.5.2`

O modelo de leitura de QR do ML Kit é embarcado no APK para não depender de download do modelo durante uma ronda offline.

### Permissões Android iniciais

- `INTERNET`
- `CAMERA`
- `POST_NOTIFICATIONS`

A câmera é declarada como recurso necessário.

## Próximos itens da Fase 1

1. Criar o primeiro usuário administrador no Supabase Auth.
2. Vincular o perfil dele a `profiles` e `app_metadata.role = admin`.
3. Criar a camada Kotlin de conexão Supabase.
4. Implementar login administrativo.
5. Implementar CRUD por arquivamento/restauração de prédio, bloco, andar e ponto.
6. Criar operação privilegiada para gerar/substituir QR Code com confirmação.
7. Criar Room local para o cache offline inicial.
8. Adicionar entidades e fila local de sincronização.

## Regra de segurança importante

O APK utilizará apenas a chave publicável do Supabase. `service_role`, chave secreta de backend, credenciais do banco e segredos de assinatura nunca serão incluídos no APK ou versionados no GitHub.
