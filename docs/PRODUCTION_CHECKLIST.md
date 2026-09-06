# RondaSafe — Checklist de Produção

Este checklist separa o que já está implementado do que precisa ser configurado com dados/credenciais reais antes do uso em produção.

## 1. Backend Supabase

- [x] Auth administrativo configurado.
- [x] RLS nas tabelas expostas.
- [x] Funções/RPCs de portaria e administração.
- [x] Edge Functions administrativas.
- [x] `portaria-ops` com autenticação customizada do aparelho + sessão do porteiro.
- [x] `portaria-cache` para cache operacional offline.
- [x] `offline-ingest` idempotente para sincronização offline.
- [x] Regra de responsável opcional validada no servidor.
- [x] Detecção de rondas atrasadas/não realizadas considerando fuso horário e cross-midnight.
- [x] Índices cobrindo FKs públicas.
- [x] Storage `guard-photos` com limite e MIME permitidos.
- [ ] Habilitar Leaked Password Protection no Supabase Auth.

## 2. Cadastro inicial

Executar pelo aplicativo administrativo:

- [ ] Cadastrar prédio com timezone correto.
- [ ] Cadastrar blocos.
- [ ] Cadastrar andares.
- [ ] Cadastrar checkpoints.
- [ ] Gerar e imprimir QR Codes.
- [ ] Fixar os QR Codes nos locais físicos corretos.
- [ ] Cadastrar porteiros e fotos.
- [ ] Entregar PIN temporário individualmente.
- [ ] Configurar programações de ronda.
- [ ] Definir responsáveis apenas nas rondas que realmente precisam de restrição.

Regra: quando nenhum responsável é selecionado, qualquer porteiro ativo pode realizar a ronda.

## 3. Aparelho da portaria

- [ ] Instalar o APK no aparelho real.
- [ ] Entrar como administrador no mesmo aparelho.
- [ ] Provisionar o aparelho para o prédio correto.
- [ ] Confirmar permissão de câmera.
- [ ] Confirmar permissão de notificações no Android 13+.
- [ ] Confirmar que data, hora e timezone automáticos estão habilitados no aparelho.
- [ ] Confirmar que o Android não restringe excessivamente o WorkManager/bateria para o aplicativo.

## 4. Teste online E2E

- [ ] Selecionar porteiro por nome/foto.
- [ ] Entrar com PIN temporário e criar PIN pessoal.
- [ ] Iniciar turno.
- [ ] Confirmar que apenas rondas permitidas aparecem.
- [ ] Iniciar ronda.
- [ ] Ler todos os QR Codes.
- [ ] Finalizar ronda.
- [ ] Confirmar logout automático.
- [ ] Confirmar registro no histórico administrativo.
- [ ] Confirmar pontos visitados e faltantes.
- [ ] Confirmar alertas administrativos quando aplicável.

## 5. Teste da regra de responsáveis

### Ronda sem responsável

- [ ] Deixar a janela sem atribuições.
- [ ] Confirmar que dois porteiros ativos distintos conseguem visualizar/iniciar a ronda.

### Ronda com responsáveis

- [ ] Atribuir somente um porteiro.
- [ ] Confirmar que o atribuído consegue visualizar/iniciar.
- [ ] Confirmar que um porteiro não atribuído não consegue visualizar.
- [ ] Confirmar que uma tentativa direta de início pelo não atribuído também é rejeitada no servidor.

## 6. Teste offline E2E

Antes do teste, abrir o app online pelo menos uma vez para atualizar o cache operacional.

- [ ] Iniciar turno online ou com cache válido.
- [ ] Desligar Wi-Fi/dados móveis.
- [ ] Autenticar um porteiro já armazenado no cache.
- [ ] Confirmar bloqueio local após 5 PINs incorretos.
- [ ] Iniciar uma ronda disponível offline.
- [ ] Ler QR Codes offline.
- [ ] Finalizar ronda offline.
- [ ] Confirmar logout imediato mesmo sem rede.
- [ ] Confirmar banner “Salvo no aparelho”.
- [ ] Reativar internet.
- [ ] Confirmar redução da fila pendente até zero.
- [ ] Confirmar registros com `captured_offline` no histórico.

## 7. Testes antifraude

- [ ] Ler QR desconhecido e confirmar sinalização.
- [ ] Substituir/revogar um QR e confirmar que o antigo deixa de ser aceito.
- [ ] Fazer leituras de pontos diferentes em menos de 15 segundos e confirmar suspeita.
- [ ] Alterar manualmente o relógio durante uma ronda de teste e confirmar alerta de relógio.
- [ ] Bloquear/desativar um dispositivo e confirmar rejeição do servidor.

## 8. Diagnóstico de sincronização

- [ ] Abrir “Sincronização da Portaria” no painel administrativo.
- [ ] Confirmar contagem de pendentes.
- [ ] Confirmar que falhas permanentes não são apagadas.
- [ ] Corrigir a causa antes de usar “Reenfileirar”.
- [ ] Confirmar sincronização após reenfileirar um evento válido.

## 9. Histórico e alertas

- [ ] Testar filtros de 7, 30 e 90 dias.
- [ ] Testar intervalo personalizado.
- [ ] Filtrar por porteiro, prédio, bloco, andar e status.
- [ ] Confirmar ronda concluída.
- [ ] Confirmar ronda incompleta.
- [ ] Confirmar ronda atrasada.
- [ ] Deixar uma janela expirar para validar “Não realizada”.
- [ ] Testar uma ronda que comece antes e termine depois da meia-noite.

## 10. Firebase Cloud Messaging — ativação externa

O push remoto não pode ser ativado sem um projeto Firebase real e suas credenciais.

Quando essas credenciais existirem:

- [ ] Criar/selecionar o projeto Firebase.
- [ ] Registrar o package Android `com.rondasafe.app`.
- [ ] Adicionar a configuração Android recomendada pelo Firebase.
- [ ] Configurar uma service account/credencial segura somente no backend para envio.
- [ ] Nunca incluir credencial privada de envio dentro do APK ou GitHub.
- [ ] Registrar a instalação/token do aparelho administrativo no backend.
- [ ] Disparar push somente para alertas relevantes.
- [ ] Testar recebimento em foreground, background e app fechado.

Os alertas dentro do aplicativo e do banco funcionam mesmo sem FCM.

## 11. Release

- [ ] Criar assinatura/release keystore fora do repositório.
- [ ] Gerar APK/AAB release assinado.
- [ ] Verificar ProGuard/R8 se habilitado.
- [ ] Instalar a build release em aparelho real.
- [ ] Executar novamente os testes online/offline principais.
- [ ] Registrar versão implantada.

## Critério de liberação

Liberar para produção somente quando:

1. o cadastro inicial estiver completo;
2. pelo menos uma ronda online e uma offline tiverem sido executadas com sucesso em aparelho físico;
3. a regra de responsáveis tiver sido testada nos dois cenários;
4. nenhum evento offline válido permanecer preso em `FAILED_PERMANENT`;
5. os alertas de ronda não realizada tiverem sido validados no timezone real do prédio.
