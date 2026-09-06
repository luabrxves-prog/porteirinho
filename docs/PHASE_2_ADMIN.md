# Fase 2 — Administração inicial

Implementação em andamento e validada por CI Android.

Escopo desta fase:

- login de administrador com Supabase Auth;
- validação de `app_metadata.role = admin`;
- dashboard administrativo;
- cadastro dinâmico de prédios, blocos, andares e pontos;
- arquivamento e restauração sem exclusão física;
- geração de QR Code pelo backend;
- substituição de QR com confirmação obrigatória;
- revogação e histórico de QR Codes;
- renderização do QR no próprio APK.

A geração e substituição dos tokens acontece na Edge Function `admin-qr`. Nenhuma chave secreta do Supabase é incluída no APK.
