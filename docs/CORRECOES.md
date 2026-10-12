# Correções da auditoria (versão 5.2)

Cada item da auditoria e o que mudou. "Testado" = coberto por teste automático que passa; "No aparelho" = depende de teste seu.

## Problemas importantes

| Item | Correção | Verificação |
|---|---|---|
| C1 Editar blocos virava trabalho em folga | `Engine.remapChanges` mapeia trabalho e folga separadamente e conta como "movida" toda mudança cujo rótulo mudou | Testado (5 testes) |
| C2 Pessoal corrompido abria vazio | `Personal.loadChecked`: avisa, conta o que foi descartado e guarda o original em `.bak` (com `.bak2` rotativo) | Testado |
| C3 Gravação não atômica / leitura falha = instalação nova | `AtomicFile` (fsync + versão anterior), leitura distingue "não existe" de "erro"; com erro o app avisa e não grava por cima; arquivo vazio avisa | Testado (JVM) + teste instrumentado escrito |
| C4 Release sem assinatura | `signingConfigs` lendo `keystore.properties` ou variáveis de ambiente; passo a passo no README | No aparelho |
| C5 "Manter os dias anteriores" | A data de referência passa a acompanhar a data "a partir de": a escala nova começa no 1º dia do 1º bloco (pode ser alterada) | Testado (dia a dia por 2 anos) |
| C6 Card Hoje ignorava férias | "Férias até 10 de outubro • Volta ao trabalho …" e aviso de período próximo (14 dias) | Testado + prévia |
| C7 Limite de 2 MB | 10 MB; teste com 5.000 eventos + 5.000 notas | Testado |
| C8 Importação parcial | Parte pessoal validada antes da confirmação (itens inválidos informados, tipo errado recusa o arquivo); se a 2ª gravação falhar, a 1ª é desfeita | Testado |
| C9 `.bak` sem restauração | "Restaurar dados anteriores à importação" em Ajustes › Dados (troca atual ↔ cópia, dá para desfazer) | Testado + prévia |
| C10 Texto perdido ao recriar a Activity | `rememberSaveable` em todas as folhas | Teste instrumentado escrito |
| C11 Trabalho na thread principal | Gravação em fila serial de IO, leitura inicial fora da Main, imagem em `Dispatchers.Default` limitada a 6 Mpx com "Gerando imagem…", PDF desenhado fora da Main, alarmes reagendados em fila própria | Testado (JVM) / fluidez no aparelho |
| C12 Lembrete mudo com notificação negada | Aviso "Notificações desativadas" + botão Ativar (pede a permissão ou abre os ajustes do app) | Prévia / no aparelho |
| C13 "Material You" sem cores dinâmicas | Paleta renomeada para "Material" e nova opção "Cores do papel de parede" (Android 12+) | No aparelho |

## Problemas menores

| Item | Correção |
|---|---|
| D1 | Excluir mudança pede confirmação |
| D2 | Limites na criação (200 períodos, 5.000 notas) com mensagem; `cleanPeriods` ordena antes de cortar |
| D3 | Resumo conta por tipo ("1 compromisso e 2 lembretes hoje") e o relógio/dia se atualizam a cada minuto |
| D4 | Horários com `Locale.ROOT` |
| D5 | `requestCode` do alarme = posição (0..63); id da notificação vem do evento |
| D6 | Compartilhar apaga só imagens com mais de 1 hora |
| D7 | Dica "Toque de novo no dia" no card vazio; fechar uma folha com alterações pede "Descartar?" |
| D8 | Se o Android fechar o app com o seletor de arquivos aberto, uma mensagem avisa ao voltar |
| D9 | Imagem e PDF listam todos os ciclos do intervalo ("1×1 → 2×2") |

## Desempenho, segurança, arquitetura, acessibilidade e limpeza

- E3: `PersonalData` indexa os eventos por data uma vez (`byDate`).
- F: backup na nuvem só com criptografia de ponta a ponta; texto do Sobre atualizado; opção "Ocultar na tela de recentes" (FLAG_SECURE); declaração do `USE_EXACT_ALARM` no README.
- G: regra do editor de escala em `Engine.applyCycleEdit` (domínio, testável); persistência em `ui/Storage.kt`; `Ids.newId` único; limites em `AppData.MAX_*`.
- Acessibilidade: cores com nome e estado para o TalkBack, controles de matiz/brilho ajustáveis por acessibilidade, ícones da barra inferior sem leitura dupla, título com reticências e seletor que desce de linha com fonte grande.
- Gradle: AGP 8.13.0, Gradle 8.13 com `distributionSha256Sum`, `compileSdk`/`targetSdk` 36, `compilerOptions`, Voltar preditivo declarado.
- Limpeza: removidos cores `ic_bg_*`, `glassHi`, imports sem uso, plugin `kotlin-serialization`, `ui-tooling`, `-dontwarn org.slf4j`, dependências lifecycle-compose sem uso; era preservada guarda só as mudanças anteriores à data.

## Não incluído (recursos novos, não defeitos)

Eventos que se repetem, eventos de vários dias, lista de próximos eventos e aviso de conflito com dia de trabalho.
