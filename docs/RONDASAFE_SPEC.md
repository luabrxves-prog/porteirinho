# RondaSafe — Especificação Funcional e Técnica do MVP

## 1. Visão geral

O RondaSafe é um aplicativo Android para controlar rondas de porteiros em um prédio comercial com dois blocos (A e B) e 28 pontos obrigatórios de ronda.

Cada bloco possui:

- Garagem
- Térreo
- Play
- 1º ao 11º andar

Total: 14 pontos por bloco, 28 QR Codes.

O mesmo APK será usado por dois perfis:

- **Porteiro:** usado principalmente no celular compartilhado da portaria.
- **Administrador:** instalado em qualquer celular autorizado para consulta e gestão.

O app será offline-first no dispositivo da portaria e sincronizará com a nuvem quando houver conexão.

---

## 2. Arquitetura geral

### Android

- Kotlin
- Jetpack Compose
- MVVM + Repository pattern
- Room Database
- WorkManager
- CameraX
- ML Kit Barcode Scanning
- DataStore
- Android Keystore

### Backend

- Supabase Postgres
- Supabase Auth para administradores
- Row Level Security (RLS)
- Edge Functions para operações privilegiadas e regras server-side
- Supabase Cron/pg_cron ou agendamento equivalente para detecção de atrasos e rondas ausentes

### Notificações

- Firebase Cloud Messaging (FCM)
- Tokens FCM vinculados a usuários/dispositivos
- Edge Function envia push quando uma regra de alerta for satisfeita

### Fluxo de dados

```text
Celular Portaria
  ├─ PIN local validado
  ├─ Room
  ├─ QR Scan
  └─ WorkManager
        ↓
     Supabase
        ↓
Celular Administrador
```

O celular da portaria deve continuar operando sem internet. O servidor é a fonte de verdade após sincronização.

---

## 3. Perfis e autenticação

### 3.1 Administrador

Administrador usa Supabase Auth com e-mail e senha.

Permissões:

- cadastrar porteiros;
- criar PIN temporário;
- redefinir PIN;
- ativar/desativar porteiros;
- gerenciar QR Codes;
- consultar rondas;
- consultar alertas;
- consultar auditoria;
- bloquear dispositivos;
- revogar QR Codes.

### 3.2 Porteiro

O porteiro não usa e-mail no fluxo diário.

Fluxo:

1. Abre o app no celular da portaria.
2. Seleciona nome/foto.
3. Digita PIN.
4. Se o PIN for temporário, troca obrigatoriamente.
5. Inicia turno.
6. Inicia ronda.
7. Escaneia QR Codes.
8. Finaliza ronda.

### 3.3 PIN

Regras recomendadas:

- 4 a 6 dígitos;
- nunca armazenar PIN em texto puro;
- armazenar hash forte no backend;
- cache offline protegido localmente;
- PIN temporário com `must_change_pin = true`;
- bloqueio temporário após tentativas inválidas consecutivas;
- registrar tentativas inválidas em auditoria;
- PIN redefinido invalida o PIN anterior após sincronização.

### 3.4 Login offline

O app poderá autenticar offline somente porteiros previamente sincronizados no dispositivo.

O banco local terá uma credencial derivada protegida pelo Android Keystore.

O login offline deverá registrar:

- usuário;
- dispositivo;
- horário local;
- modo `offline`;
- estado de sincronização.

---

## 4. Dispositivos

Cada instalação possui um `device_id` aleatório persistente.

Campos relevantes:

- nome amigável;
- modelo do aparelho;
- versão Android;
- versão do app;
- primeiro acesso;
- último sync;
- status ativo/bloqueado;
- token FCM;
- tipo `PORTARIA`, `ADMIN` ou `MISTO`.

Um dispositivo bloqueado pelo administrador não deve conseguir sincronizar novas operações privilegiadas.

---

## 5. Turnos

### Fluxo

1. Porteiro autentica.
2. Toca em **Iniciar turno**.
3. App cria sessão de turno local.
4. Rondas executadas são associadas ao turno.
5. Porteiro encerra turno ou outro porteiro assume.

A troca de usuário encerra a sessão operacional atual.

---

## 6. Planejamento de rondas

O sistema deve permitir configuração de horários de ronda.

Estrutura sugerida:

- janela de início;
- tolerância de atraso;
- pontos obrigatórios;
- ativo/inativo;
- dias da semana;
- blocos incluídos.

Exemplo:

```text
Ronda 01
Disponível: 22:00
Limite normal: 22:15
Após 22:15: atrasada
```

Uma ronda pode ser iniciada fora da janela, mas será marcada conforme a regra.

---

## 7. QR Codes

### 7.1 Cadastro

Cada ponto físico terá:

- bloco;
- pavimento;
- nome;
- código lógico;
- token QR ativo;
- versão;
- status ativo/revogado.

### 7.2 Conteúdo do QR

Não usar textos simples como:

```text
BLOCO-A-ANDAR-5
```

O QR deverá carregar um token opaco e aleatório, por exemplo:

```text
rs:v1:7edc1f1c...token-aleatorio...
```

O backend relaciona esse token ao ponto real.

### 7.3 Rotação

O administrador poderá:

- revogar token;
- gerar novo token;
- manter histórico do token antigo;
- identificar tentativa de leitura de token revogado.

### 7.4 Leitura

A tela de ronda usa apenas câmera em tempo real.

Não haverá importação da galeria no fluxo do MVP.

---

## 8. Regras de negócio da ronda

### 8.1 Sequência

A rota é flexível.

O sistema não bloqueia uma leitura por ordem diferente.

### 8.2 Ponto obrigatório

Cada ponto obrigatório conta uma única vez por ronda.

### 8.3 QR duplicado

Ao reler ponto já registrado:

- não criar nova visita válida;
- avisar `QR Code já lido`;
- opcionalmente registrar evento técnico de duplicidade para auditoria.

### 8.4 QR desconhecido

QR não reconhecido:

- não conta para a ronda;
- exibir aviso;
- registrar tentativa suspeita;
- sincronizar evento depois.

### 8.5 Ronda completa

Todos os pontos obrigatórios foram visitados.

### 8.6 Ronda incompleta

Ao finalizar faltando pontos:

- exibir pontos pendentes;
- exigir confirmação de encerramento;
- status final `INCOMPLETA`;
- gerar alerta administrativo.

### 8.7 Ronda atrasada

Se o início ocorrer após a tolerância configurada:

- status contém flag `ATRASADA`;
- não bloquear execução.

### 8.8 Ronda não iniciada

Job no backend detecta ronda cujo prazo expirou sem execução e gera alerta.

---

## 9. Antifraude e detecção de suspeita

O sistema prioriza sinalização e auditoria em vez de bloqueios agressivos.

### 9.1 Leitura rápida demais

Cada ponto pode ter um tempo mínimo configurável para comparação com a leitura anterior.

Se o intervalo for abaixo do limite:

- leitura continua registrada;
- `suspicious = true`;
- motivo `TEMPO_IMPROVAVEL`.

### 9.2 Ordem improvável

Regra heurística opcional baseada em:

- troca de bloco em intervalo curto;
- distância lógica muito alta entre pavimentos;
- deslocamentos incompatíveis com limiar configurado.

Não bloqueia leitura.

### 9.3 Alteração do relógio

Registrar para cada leitura:

- timestamp de parede do aparelho;
- timestamp monotônico/elapsed realtime;
- timezone;
- timestamp servidor no recebimento.

O app compara progressão do relógio de parede com relógio monotônico.

Diferenças significativas geram `CLOCK_TAMPER_SUSPECTED`.

O servidor também compara o horário local informado com o horário de chegada.

### 9.4 Origem offline

Toda leitura criada sem conectividade recebe `captured_offline = true`.

Isso não implica fraude, apenas informação de auditoria.

---

## 10. Funcionamento offline

### Deve funcionar offline

- seleção de porteiro previamente sincronizado;
- autenticação por PIN previamente provisionado;
- início de turno;
- início de ronda;
- câmera e leitura dos QR Codes previamente sincronizados;
- registro de visitas;
- finalização de ronda;
- consulta da ronda corrente;
- fila de eventos pendentes.

### Não depender da internet durante a ronda

QR Codes ativos necessários à operação devem existir em cache local.

### Sincronização

WorkManager executa sync quando:

- internet estiver disponível;
- app abrir;
- operação crítica for encerrada;
- execução periódica permitida pelo Android ocorrer.

Cada operação local possui UUID próprio para idempotência.

O backend deve rejeitar duplicação lógica sem perder o registro original.

---

## 11. Conflitos de sincronização

Princípios:

- eventos operacionais são append-only sempre que possível;
- edição retroativa de leitura não faz parte do MVP;
- UUID criado no dispositivo garante idempotência;
- timestamps locais nunca substituem timestamps do servidor;
- servidor calcula flags adicionais na ingestão.

Em conflito entre dados cadastrais:

- backend é fonte de verdade;
- próxima sincronização atualiza cache local.

---

## 12. Estados principais

### Ronda

- `DISPONIVEL`
- `EM_ANDAMENTO`
- `CONCLUIDA`
- `INCOMPLETA`
- `NAO_INICIADA`
- `CANCELADA`

Flags independentes:

- `ATRASADA`
- `OFFLINE`
- `SUSPEITA`

### Sincronização

- `PENDING`
- `SYNCING`
- `SYNCED`
- `FAILED`

---

## 13. Notificações

### Porteiro

- ronda disponível;
- ronda atrasada;
- pontos pendentes;
- QR já lido;
- ronda incompleta;
- sem internet, salvando offline;
- sincronização concluída.

Mensagens instantâneas da própria interface podem ser notificações locais/snackbars; push é usado quando o app precisa receber evento vindo do backend.

### Administrador

Push FCM para:

- ronda não iniciada;
- ronda atrasada;
- ronda finalizada incompleta;
- leitura suspeita;
- dispositivo sem sincronizar por período configurado;
- tentativa de acesso inválida relevante.

---

## 14. Telas — Porteiro

### P01 — Seleção de perfil

- lista de porteiros ativos;
- foto/avatar;
- nome;
- status offline/online.

### P02 — PIN

- teclado numérico;
- tentativas restantes/bloqueio;
- estado offline.

### P03 — Troca obrigatória do PIN

- novo PIN;
- confirmação;
- regras de segurança.

### P04 — Home do turno

- porteiro ativo;
- horário de início;
- status da internet;
- próxima ronda;
- botão iniciar ronda;
- botão encerrar turno.

### P05 — Ronda em andamento

- progresso `x / 28` ou conforme template;
- bloco/pontos visitados;
- pontos pendentes;
- botão abrir câmera;
- status de sync.

### P06 — Scanner

- câmera em tela cheia;
- moldura de leitura;
- lanterna;
- feedback sonoro/háptico;
- resultado imediato.

### P07 — Resumo/finalização

- duração;
- pontos visitados;
- pontos faltantes;
- suspeitas;
- atraso;
- botão finalizar.

### P08 — Sincronização

- itens pendentes;
- última sincronização;
- erros recuperáveis.

---

## 15. Telas — Administrador

### A01 — Login admin

- e-mail;
- senha.

### A02 — Dashboard

Cards:

- ronda atual;
- atrasadas hoje;
- incompletas hoje;
- suspeitas;
- último sync portaria.

### A03 — Porteiros

- lista;
- criar;
- editar;
- ativar/desativar;
- PIN temporário;
- redefinir PIN.

### A04 — Detalhe do porteiro

- dados;
- histórico;
- últimos acessos;
- redefinição de PIN.

### A05 — Pontos de ronda

- Bloco A/B;
- pavimento;
- token ativo;
- gerar/rotacionar QR;
- visualizar QR para impressão.

### A06 — Programação de rondas

- horários;
- tolerância;
- dias;
- pontos obrigatórios.

### A07 — Histórico

Filtros:

- data;
- porteiro;
- bloco;
- pavimento;
- status.

### A08 — Detalhe da ronda

- início/fim;
- porteiro;
- dispositivo;
- sequência das leituras;
- timestamps local/servidor;
- offline;
- suspeitas;
- pontos ausentes.

### A09 — Alertas

- não iniciadas;
- atrasadas;
- incompletas;
- suspeitas;
- falhas de sync;
- tentativas inválidas.

### A10 — Dispositivos

- aparelhos conhecidos;
- último sync;
- token push;
- bloquear/desbloquear.

### A11 — Auditoria

- login;
- alteração cadastral;
- PIN redefinido;
- QR rotacionado;
- dispositivo bloqueado;
- alterações de configuração.

---

## 16. Modelo de dados Supabase

### `profiles`

Administradores autenticados pelo Supabase Auth.

- `id uuid PK -> auth.users.id`
- `name`
- `role = ADMIN`
- `active`
- `created_at`

### `guards`

- `id uuid PK`
- `name`
- `photo_url nullable`
- `pin_hash`
- `must_change_pin`
- `active`
- `failed_pin_attempts`
- `locked_until nullable`
- `created_at`
- `updated_at`

### `devices`

- `id uuid PK`
- `installation_id unique`
- `name`
- `device_type`
- `model`
- `android_version`
- `app_version`
- `fcm_token nullable`
- `blocked`
- `last_seen_at`
- `last_sync_at`

### `guard_shifts`

- `id uuid PK`
- `guard_id FK`
- `device_id FK`
- `started_local_at`
- `started_server_at`
- `ended_local_at nullable`
- `ended_server_at nullable`
- `captured_offline`

### `buildings`

- `id uuid PK`
- `name`

### `blocks`

- `id uuid PK`
- `building_id FK`
- `code` (`A`, `B`)
- `name`

### `round_points`

- `id uuid PK`
- `block_id FK`
- `floor_code`
- `name`
- `sort_order`
- `active`

### `qr_tokens`

- `id uuid PK`
- `round_point_id FK`
- `token_hash unique`
- `version`
- `active`
- `issued_at`
- `revoked_at nullable`

### `round_templates`

- `id uuid PK`
- `name`
- `active`
- `late_tolerance_minutes`
- `days_of_week`

### `round_template_points`

- `round_template_id FK`
- `round_point_id FK`
- `required`

### `round_schedules`

- `id uuid PK`
- `round_template_id FK`
- `scheduled_time`
- `active`

### `rounds`

- `id uuid PK`
- `client_uuid unique`
- `guard_id FK`
- `shift_id FK`
- `device_id FK`
- `round_template_id FK`
- `scheduled_for`
- `started_local_at`
- `started_server_at nullable`
- `finished_local_at nullable`
- `finished_server_at nullable`
- `status`
- `is_late`
- `is_suspicious`
- `captured_offline`
- `clock_suspected`
- `sync_status`

### `round_visits`

- `id uuid PK`
- `client_uuid unique`
- `round_id FK`
- `round_point_id nullable FK`
- `qr_token_id nullable FK`
- `device_id FK`
- `captured_local_at`
- `received_server_at nullable`
- `elapsed_realtime_ms`
- `timezone`
- `captured_offline`
- `is_suspicious`
- `suspicion_reason nullable`

Restrição única recomendada para visita válida:

- uma visita válida por `round_id + round_point_id`.

### `security_events`

- `id uuid PK`
- `event_type`
- `guard_id nullable`
- `admin_id nullable`
- `device_id nullable`
- `round_id nullable`
- `metadata jsonb`
- `created_at`

### `notifications`

- `id uuid PK`
- `recipient_type`
- `admin_id nullable`
- `guard_id nullable`
- `type`
- `title`
- `body`
- `read_at nullable`
- `created_at`

### `audit_logs`

- `id uuid PK`
- `actor_type`
- `actor_id nullable`
- `device_id nullable`
- `action`
- `entity_type`
- `entity_id nullable`
- `before_data jsonb nullable`
- `after_data jsonb nullable`
- `created_at`

---

## 17. Segurança Supabase

Todas as tabelas expostas no schema `public` devem ter RLS habilitado.

Princípios:

- admin acessa dados administrativos conforme `app_metadata`/perfil confiável;
- nunca usar `user_metadata` como autorização;
- `service_role` nunca entra no APK;
- operações privilegiadas como cadastrar porteiro, redefinir PIN, rotacionar QR e enviar notificações passam por Edge Functions autenticadas;
- tabelas sensíveis podem ficar em schema não exposto;
- funções privilegiadas devem ter grants mínimos.

O PIN não será retornado ao cliente após definição.

---

## 18. Geração de QR Codes

Administrador escolhe um ponto e toca em **Gerar novo QR**.

Backend:

1. gera token criptograficamente aleatório;
2. armazena hash/identificador seguro;
3. invalida token anterior se solicitado;
4. retorna conteúdo do novo QR uma única vez para renderização/impressão;
5. registra auditoria.

---

## 19. Permissões Android

Obrigatórias:

- `android.permission.CAMERA`
- `android.permission.INTERNET`
- `android.permission.ACCESS_NETWORK_STATE`
- `android.permission.POST_NOTIFICATIONS` (Android 13+)

Possíveis conforme implementação:

- `android.permission.WAKE_LOCK` (WorkManager/FCM pode depender indiretamente via libs)
- `com.google.android.c2dm.permission.RECEIVE` via FCM manifest merger

Não solicitar armazenamento/galeria para leitura do QR no MVP.

---

## 20. Estrutura Android sugerida

```text
app/
  core/
    database/
    network/
    security/
    sync/
    notifications/
    ui/
  domain/
    auth/
    guard/
    shift/
    round/
    qr/
    audit/
  data/
    local/
    remote/
    repository/
  feature/
    entry/
    pin/
    guardhome/
    round/
    scanner/
    admin/
    history/
    alerts/
    devices/
```

---

## 21. Room — principais tabelas locais

- `local_guards`
- `local_round_points`
- `local_qr_tokens`
- `local_round_templates`
- `local_rounds`
- `local_round_visits`
- `pending_sync_events`
- `local_device_state`

Dados sensíveis locais devem ser minimizados e protegidos.

---

## 22. Sincronização — contrato

Cada evento enviado contém:

```json
{
  "client_uuid": "UUID",
  "device_id": "UUID",
  "event_type": "ROUND_VISIT",
  "captured_local_at": "ISO-8601",
  "elapsed_realtime_ms": 123456789,
  "payload": {}
}
```

Servidor responde individualmente:

- `ACCEPTED`
- `ALREADY_PROCESSED`
- `REJECTED_DEVICE_BLOCKED`
- `REJECTED_INVALID_REFERENCE`
- `ACCEPTED_SUSPICIOUS`

Somente após confirmação o item é marcado `SYNCED` localmente.

---

## 23. Alertas automáticos backend

Processo periódico avalia:

- rondas previstas sem início;
- rondas em atraso;
- dispositivo da portaria sem sync;
- ronda finalizada incompleta;
- eventos suspeitos ainda não notificados.

Alertas devem ser idempotentes para não disparar push repetido pela mesma ocorrência.

---

## 24. Critérios de aceitação do MVP

### Autenticação

- [ ] Admin entra com e-mail/senha.
- [ ] Porteiro entra selecionando perfil + PIN.
- [ ] Primeiro PIN temporário obriga troca.
- [ ] Admin redefine PIN temporário.
- [ ] Porteiro previamente sincronizado consegue autenticar offline.

### Pontos

- [ ] Existem 2 blocos.
- [ ] Existem 14 pontos por bloco.
- [ ] Total padrão de 28 pontos cadastrados.
- [ ] Admin consegue gerar e rotacionar QR de ponto.

### Ronda

- [ ] Ronda pode ser feita em ordem livre.
- [ ] Leitura válida conta ponto uma vez.
- [ ] QR duplicado não duplica ponto.
- [ ] QR desconhecido não conta.
- [ ] Finalização identifica pontos faltantes.
- [ ] Ronda incompleta gera status e alerta.
- [ ] Ronda atrasada é identificada sem bloquear execução.

### Offline

- [ ] Ronda inteira pode ser feita sem internet.
- [ ] Leituras ficam persistidas após fechar/reabrir o app.
- [ ] Dados sincronizam quando conexão retorna.
- [ ] Uma operação não duplica no servidor após retry.
- [ ] Leitura offline fica marcada.

### Antifraude

- [ ] Scanner não oferece leitura por galeria.
- [ ] Token do QR não revela bloco/pavimento diretamente.
- [ ] Horário local e servidor são preservados.
- [ ] Dispositivo é registrado.
- [ ] Leitura rápida demais pode ser marcada suspeita.
- [ ] QR revogado deixa de ser válido.
- [ ] Dispositivo pode ser bloqueado.
- [ ] Alteração significativa do relógio gera flag.

### Administração

- [ ] Admin consulta histórico por data.
- [ ] Admin filtra por porteiro.
- [ ] Admin filtra por bloco/ponto.
- [ ] Admin vê faltas e atrasos.
- [ ] Admin vê sequência de leituras.
- [ ] Admin vê eventos suspeitos.
- [ ] Admin consulta auditoria básica.

### Notificações

- [ ] Admin recebe push de ronda não iniciada.
- [ ] Admin recebe push de ronda incompleta.
- [ ] Admin recebe push de evento suspeito.
- [ ] Admin recebe alerta de falta de sincronização.
- [ ] Porteiro recebe feedback local sobre atraso, duplicidade, pendências e offline.

---

## 25. Fora do MVP

Para evitar aumentar complexidade antes da validação:

- painel web;
- iOS;
- geolocalização indoor;
- NFC;
- biometria obrigatória;
- reconhecimento facial;
- analytics avançado;
- múltiplos condomínios/tenants;
- edição manual de visita concluída;
- exportações avançadas em PDF/Excel.

A arquitetura deve permitir evolução posterior.

---

## 26. Referência do CredenciaPass

O `credenciapass` já implementa conceitos úteis:

- câmera para QR Code;
- trava de leitura para evitar múltiplos callbacks da câmera;
- feedback positivo/erro;
- detecção de registro duplicado no servidor;
- associação da operação ao operador;
- histórico recente de leituras.

Esses conceitos devem ser reaproveitados no design lógico, porém reimplementados em Kotlin/CameraX/ML Kit e com persistência offline.

---

## 27. Decisões técnicas do MVP

1. **Android nativo em Kotlin**, não PWA.
2. **Supabase** como backend principal.
3. **FCM** somente para notificações push.
4. **Room + WorkManager** como base offline-first.
5. **Administrador via Supabase Auth**.
6. **Porteiro via identidade própria + PIN**, otimizado para aparelho compartilhado.
7. **RLS em todas as tabelas públicas**.
8. **Operações administrativas sensíveis via Edge Functions**.
9. **QR com token opaco rotacionável**.
10. **Suspeitas geram flags e auditoria; não bloqueiam a ronda por padrão**.
