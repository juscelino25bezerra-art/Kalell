# Chave de TESTE

`kalendrium-teste.jks` é uma chave **pública e só para testes**: a senha está aqui e no workflow, então qualquer um
pode assinar um APK com ela. Serve para o GitHub gerar um APK instalável sem configurar nada.

- Arquivo: `ci/kalendrium-teste.jks` (PKCS12)
- Alias: `kalendrium-teste`
- Senha da keystore e da chave: `kalendrium-teste`

**Não publique na Play Store com ela.** Para usar a chave de verdade no workflow, cadastre em
*Settings › Secrets and variables › Actions* os secrets `KALENDRIUM_KEYSTORE_BASE64` (o `.jks` em base64:
`base64 -w0 kalendrium-upload.jks`), `KALENDRIUM_STORE_PASSWORD`, `KALENDRIUM_KEY_ALIAS` e `KALENDRIUM_KEY_PASSWORD`.
Com eles presentes, o workflow ignora esta chave de teste.

Um APK assinado com esta chave não instala por cima de um assinado com outra (e vice-versa): desinstale antes.
