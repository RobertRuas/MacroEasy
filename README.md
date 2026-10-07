# MacroEasy

Aplicação de macros por passos, em Java, para o macOS. Cada macro é uma lista de ações: clique, texto, atalho, espera ou trazer uma janela já aberta para a frente.

Versão 1.0.7. Desenvolvedor: Robert.

## O que dá para fazer

- Montar a macro na mão ou gravar cliques, texto e atalhos.
- Definir um intervalo único entre as ações e, se quiser, um intervalo próprio em um passo.
- Repetir a lista quantas vezes precisar e esperar um atraso inicial para focar o aplicativo alvo.
- Guardar as macros na barra lateral. Elas são salvas sozinhas em `~/Documents/MacroEasy`.
- Parar a execução com Esc, com o botão flutuante ou levando o ponteiro ao canto superior esquerdo da tela.

A gravação não cria passos de espera. O tempo entre as ações gravadas segue o campo Intervalo.

## Permissões no macOS

Na primeira abertura, o MacroEasy mostra o que precisa ser autorizado:

- **Acessibilidade**, para clicar, digitar e trazer janelas à frente.
- **Monitoramento de entrada**, para gravar o que você faz.

O botão Autorizar coloca o app na lista do sistema. Ative o interruptor e clique em Reabrir. O macOS só aplica a permissão na próxima abertura.

## Requisitos

- macOS
- JDK 22 ou mais recente
- Maven, para compilar

No Mac com Homebrew, o JDK fica em `/opt/homebrew/opt/openjdk`.

## Compilar

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk
export PATH="$JAVA_HOME/bin:$PATH"
mvn package
```

O jar fica em `target/MacroEasy.jar`.

## Aplicativo

Para gerar o `MacroEasy.app`:

```bash
jpackage --type app-image --name MacroEasy --dest dist \
  --input target --main-jar MacroEasy.jar --main-class macroeasy.MacroEasy \
  --app-version 1.0.7 \
  --java-options '--enable-native-access=ALL-UNNAMED'
```

Abra com:

```bash
open dist/MacroEasy.app
```

Cada geração nova muda a assinatura do app. Se o macOS pedir de novo, ative as permissões outra vez e use Reabrir.

## Atualização

Ao abrir, o app consulta a release mais recente em [github.com/RobertRuas/MacroEasy](https://github.com/RobertRuas/MacroEasy). Se a tag for maior que a versão instalada, a janela fica bloqueada, o download começa sozinho e a barra fica pelo menos 10 segundos. Se a instalação falhar, o app abre uma vez e espera o botão Tentar de novo, sem repetir sozinho. Sem rede, o app só abre se esta mesma versão foi confirmada nos últimos 5 minutos. Apagar essa confirmação, ou ficar offline para não ver uma release nova, deixa a janela bloqueada até a próxima consulta.

A tag da release deve ser `v1.2.0` ou `1.2.0`. O zip precisa conter `MacroEasy.app`.

A página do projeto, para publicar depois, está em `http/`.

## Atalhos

| Ação | Atalho |
| --- | --- |
| Executar | ⌘R |
| Parar | Esc |
| Abrir | ⌘O |
| Salvar | ⌘S |
| Editar o passo | ⌘E |
| Duplicar o passo | ⌘D |
| Subir / descer | ⌘↑ / ⌘↓ |
