# MacroEasy

Aplicação de macros por passos, em Java. No macOS ela grava, executa e traz janelas à frente. No Windows e no Linux o mesmo programa executa cliques, arrastos, texto, atalhos, rolagem e esperas.

Versão 1.4. Desenvolvedor: Robert.

## Onde roda

| Sistema | O que funciona |
| --- | --- |
| macOS | Tudo: gravação, execução, janelas já abertas e atualização automática. O arquivo publicado é o `MacroEasy.app`. |
| Windows | O `MacroEasy.jar`, com JDK 22 ou mais recente. Clique, arrasto, texto, atalho, rolagem e espera. A gravação e trazer uma janela à frente ficam no macOS. |
| Linux | O mesmo jar e os mesmos limites do Windows. A sessão precisa ser X11; o Wayland bloqueia o controle do mouse e do teclado. |

## Como usar no macOS

1. Baixe `MacroEasy.zip` em [Releases](https://github.com/RobertRuas/MacroEasy/releases/latest).
2. Abra o zip e o `MacroEasy.app`. Ele já traz o Java; não precisa instalar nada. Não use o `MacroEasy.jar` no Mac, porque ele precisa de um Java instalado.
3. Autorize Acessibilidade e Monitoramento de entrada. Ative o interruptor e clique em Reabrir. O macOS só grava a permissão na próxima abertura.
4. Grave o que você faz ou monte os passos. As macros ficam em `~/Documents/MacroEasy`.
5. Execute com um atraso inicial para focar o programa certo. Esc (em qualquer aplicativo), o botão flutuante ou o canto superior esquerdo de qualquer tela interrompem.

## Como usar no Windows e no Linux

1. Instale um JDK 22 ou mais recente e confira com `java -version`.
2. Baixe `MacroEasy.jar` na [release mais recente](https://github.com/RobertRuas/MacroEasy/releases/latest).
3. Na pasta do arquivo:

```bash
java --enable-native-access=ALL-UNNAMED -jar MacroEasy.jar
```

O editor abre sem as permissões do macOS. A gravação avisa que está disponível só no macOS.

## A janela

- Uma barra no topo com Executar, Gravar, Parar, Vezes, Atraso e Intervalo. O botão ☰ guarda Nova macro, Abrir, Salvar, Desfazer, Refazer, a opção de gravar as pausas reais e o Sobre.
- As macros ficam na barra lateral, que pode ser escondida com o botão ao lado de Executar ou com ⌘B.
- O botão **Adicionar passo** abre a lista dos sete tipos de passo.
- O passo selecionado mostra ali mesmo os ícones de editar, duplicar, excluir, subir e descer. O botão direito do mouse abre as mesmas ações.
- Tema moderno (FlatLaf), claro ou escuro conforme o sistema.
- A janela abre com cerca de dois terços da tela, aceita até 600 × 400 e lembra o tamanho e a posição.

## O que dá para fazer

- Montar a macro na mão ou gravar cliques, arrastos, texto, atalhos e rolagem da roda do mouse. Na gravação, Enter, Tab, Esc, setas, teclas F e combinações com ⌘, Ctrl ou ⌥ viram passos de atalho; só os caracteres viram texto. Os atalhos seguem o layout do teclado (AZERTY, Dvorak, ABNT2).
- Número aleatório no texto: escreva `{9-15}` no passo de texto e, a cada execução, ele digita um número inteiro sorteado de 9 a 15 (os dois incluídos). Dá para usar vários no mesmo texto, como `Idade {18-60}, nota {0-10}`.
- Clique e arrasto vão direto para a tela: escolha Clique e clique onde quer clicar; escolha Arrastar e soltar, clique no ponto inicial e logo depois no ponto final. A janela do MacroEasy some enquanto você marca. Para mudar o botão ou o intervalo, use o ícone de editar.
- Arrastar: segura o botão no ponto inicial, começa devagar, desliza até o final e espera um instante antes de soltar, para que listas, pastas e páginas reconheçam o arrasto.
- Desfazer e refazer qualquer mudança nos passos, inclusive uma gravação inteira.
- Rolar para cima ou para baixo, no lugar do ponteiro ou num ponto marcado. No macOS a rolagem gravada (roda ou trackpad) guarda a distância exata em pixels e repete exatamente essa distância. Também dá para informar em linhas. O botão Gravar rolagem preenche a direção, a distância e a posição com uma rolagem real.
- Definir um intervalo único entre as ações e, se quiser, um intervalo próprio em um passo.
- Repetir a lista quantas vezes precisar e esperar um atraso inicial para focar o aplicativo alvo.
- Guardar as macros na barra lateral. Elas são salvas sozinhas em `~/Documents/MacroEasy`.
- Parar a execução com Esc, mesmo com outro aplicativo em foco, com o botão flutuante ou levando o ponteiro ao canto superior esquerdo de qualquer tela.

A gravação não cria passos de espera. O tempo entre as ações gravadas segue o campo Intervalo. Para guardar o tempo real de cada pausa, ative Gravação → Gravar as pausas reais: cada passo gravado recebe um intervalo próprio.

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

O jar fica em `target/MacroEasy.jar`. O `mvn package` também roda os testes automáticos.

## Aplicativo

Para gerar o `MacroEasy.app`:

```bash
jpackage --type app-image --name MacroEasy --dest dist \
  --input target --main-jar MacroEasy.jar --main-class macroeasy.MacroEasy \
  --app-version 1.4 \
  --java-options '--enable-native-access=ALL-UNNAMED'
```

Abra com:

```bash
open dist/MacroEasy.app
```

O aplicativo é assinado com o certificado estável MacroEasy Local. A atualização conserva essa assinatura, então a permissão em Ajustes do Sistema continua valendo em qualquer Mac.

## Atualização

Ao abrir, o app consulta a release mais recente em [github.com/RobertRuas/MacroEasy](https://github.com/RobertRuas/MacroEasy).

No `MacroEasy.app` (macOS), se a tag for maior que a versão instalada, a janela fica bloqueada, o download começa sozinho e a barra fica pelo menos 10 segundos. Se a instalação falhar, o app abre uma vez e espera o botão Tentar de novo, sem repetir sozinho. Sem rede, ou quando o GitHub não responde, o app abre normalmente; só fica bloqueado se uma versão nova já tinha sido encontrada antes.

No `MacroEasy.jar` (Windows, Linux), o app nunca fica bloqueado. Quando há versão nova, ele avisa uma vez e oferece abrir a página da release.

A tag da release deve ser `v1.4.0` ou `1.4.0`. O zip precisa conter `MacroEasy.app`.

## Publicar uma versão

1. Suba o número em `pom.xml` e em `About.VERSION`.
2. Escreva as novidades em `release-notes/vX.Y.md`.
3. Com tudo no `main`, rode o workflow em Actions → Release → Run workflow. Ele compila, roda os testes e cria a release `vX.Y` com o `MacroEasy.jar`, se ainda não existir release para essa versão. Depois gera o `MacroEasy.app` num Mac do GitHub, já com o Java dentro, e anexa o `MacroEasy.zip` à release.

O app sai assinado com o certificado MacroEasy Local quando os segredos `MAC_CERT_P12` (o certificado exportado em .p12, em base64) e `MAC_CERT_PASSWORD` estão em Settings → Secrets → Actions. Sem eles, sai com assinatura local (ad-hoc), e o macOS pede de novo as permissões de Acessibilidade e Monitoramento de entrada a cada versão.

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
| Novo clique, texto, atalho, espera, janela, rolagem, arrasto | ⌘1 a ⌘7 |
| Desfazer / refazer | ⌘Z / ⇧⌘Z (Ctrl+Y também no Windows e no Linux) |
| Mostrar ou ocultar as macros | ⌘B |
| Editar o passo | ⌘E |
| Duplicar o passo | ⌘D |
| Excluir o passo | ⌘⌫ |
| Subir / descer | ⌘↑ / ⌘↓ |
