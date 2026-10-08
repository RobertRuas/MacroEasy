# MacroEasy

Aplicação de macros por passos, em Java. No macOS ela grava, executa e traz janelas à frente. No Windows e no Linux o mesmo programa executa cliques, texto, atalhos, rolagem e esperas.

Versão 1.1. Desenvolvedor: Robert.

## Onde roda

| Sistema | O que funciona |
| --- | --- |
| macOS | Tudo: gravação, execução, janelas já abertas e atualização automática. O arquivo publicado é o `MacroEasy.app`. |
| Windows | O `MacroEasy.jar`, com JDK 22 ou mais recente. Clique, texto, atalho, rolagem e espera. A gravação e trazer uma janela à frente ficam no macOS. |
| Linux | O mesmo jar e os mesmos limites do Windows. |

## Como usar no macOS

1. Baixe `MacroEasy.zip` em [Releases](https://github.com/RobertRuas/MacroEasy/releases/tag/v1.1).
2. Abra o zip e o `MacroEasy.app`.
3. Autorize Acessibilidade e Monitoramento de entrada. Ative o interruptor e clique em Reabrir. O macOS só grava a permissão na próxima abertura.
4. Grave o que você faz ou monte os passos. As macros ficam em `~/Documents/MacroEasy`.
5. Execute com um atraso inicial para focar o programa certo. Esc, o botão flutuante ou o canto superior esquerdo da tela interrompem.

## Como usar no Windows e no Linux

1. Instale um JDK 22 ou mais recente e confira com `java -version`.
2. Baixe `MacroEasy.jar` na mesma release.
3. Na pasta do arquivo:

```bash
java --enable-native-access=ALL-UNNAMED -jar MacroEasy.jar
```

O editor abre sem as permissões do macOS. A gravação avisa que está disponível só no macOS.

## O que dá para fazer

- Montar a macro na mão ou gravar cliques, texto, atalhos e rolagem da roda do mouse. Na gravação, Enter, Tab, Esc, setas, teclas F e combinações com ⌘, Ctrl ou ⌥ viram passos de atalho; só os caracteres viram texto.
- Rolar a roda do mouse para cima ou para baixo, no lugar do ponteiro ou num ponto marcado. O botão Gravar rolagem preenche a direção, as linhas e a posição com uma rolagem real.
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

- macOS, Windows ou Linux
- JDK 22 ou mais recente para compilar, e também para abrir o jar no Windows e no Linux
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
  --app-version 1.1 \
  --java-options '--enable-native-access=ALL-UNNAMED'
```

Abra com:

```bash
open dist/MacroEasy.app
```

O aplicativo é assinado com o certificado estável MacroEasy Local. A atualização conserva essa assinatura, então a permissão em Ajustes do Sistema continua valendo em qualquer Mac.

## Atualização

Ao abrir, o app consulta a release mais recente em [github.com/RobertRuas/MacroEasy](https://github.com/RobertRuas/MacroEasy). Se a tag for maior que a versão instalada, a janela fica bloqueada, o download começa sozinho e a barra fica pelo menos 10 segundos. Se a instalação falhar, o app abre uma vez e espera o botão Tentar de novo, sem repetir sozinho. Sem rede, o app só abre se esta mesma versão foi confirmada nos últimos 5 minutos. Apagar essa confirmação, ou ficar offline para não ver uma release nova, deixa a janela bloqueada até a próxima consulta.

A tag da release deve ser `v1.2.0` ou `1.2.0`. O zip precisa conter `MacroEasy.app`.

A página do projeto, para publicar depois, está em `http/`.

## Atalhos

Cada botão mostra o seu atalho ao lado do nome. No Windows e no Linux, ⌘ é Ctrl.

| Ação | Atalho |
| --- | --- |
| Executar | ⌘R |
| Gravar | ⇧⌘R |
| Parar | Esc |
| Abrir | ⌘O |
| Salvar | ⌘S |
| Nova macro | ⌘N |
| Novo clique, texto, atalho, espera, janela, rolagem | ⌘1 a ⌘6 |
| Editar o passo | ⌘E |
| Duplicar o passo | ⌘D |
| Excluir o passo | ⌘⌫ |
| Subir / descer | ⌘↑ / ⌘↓ |
