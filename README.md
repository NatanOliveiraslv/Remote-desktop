# RemoteAccess — Sistema de acesso remoto

Projeto de acesso remoto a computadores, com um **Agent Java instalado no computador que será acessado**, um **servidor Spring Boot** responsável por intermediar as conexões e um **Client web em React** para visualizar e controlar o computador pelo navegador.

> **Estado do projeto:** MVP em desenvolvimento, voltado para uso e testes locais. Ainda não deve ser considerado seguro para exposição à internet ou uso em produção.

## Índice

- [Objetivo](#objetivo)
- [Arquitetura](#arquitetura)
- [Tecnologias](#tecnologias)
- [Funcionalidades](#funcionalidades)
- [Estrutura dos projetos](#estrutura-dos-projetos)
- [Pré-requisitos](#pré-requisitos)
- [Executar localmente](#executar-localmente)
- [Como funciona a comunicação](#como-funciona-a-comunicação)
- [Mensagens WebSocket](#mensagens-websocket)
- [Identidade e estado dos computadores](#identidade-e-estado-dos-computadores)
- [Próximas etapas](#próximas-etapas)
- [Segurança](#segurança)
- [Solução de problemas](#solução-de-problemas)

## Objetivo

Construir uma aplicação de acesso remoto em que um usuário utiliza o navegador para conectar a um computador que esteja ligado e executando o Agent. O Agent mantém uma conexão de saída com o servidor; o Client não precisa instalar software nativo.

O servidor intermedeia a comunicação entre o navegador e o Agent, permitindo identificar computadores conectados e encaminhar mensagens e imagens da tela.

## Arquitetura

```text
┌────────────────────────┐       WebSocket        ┌────────────────────────┐
│      remote-client     │ ◄────────────────────► │      remote-server     │
│                        │                        │                        │
│ React + TypeScript     │                        │ Spring Boot             │
│ Navegador              │                        │ WebSocket handlers      │
└────────────────────────┘                        │ Registries em memória    │
                                                  └────────────┬───────────┘
                                                               │ WebSocket
                                                               │
                                                  ┌────────────▼───────────┐
                                                  │       remote-agent     │
                                                  │                        │
                                                  │ Java 21                │
                                                  │ Captura de tela        │
                                                  │ Controle via Robot     │
                                                  └────────────────────────┘
```

O servidor mantém as conexões ativas em memória. O cadastro persistente dos computadores e o histórico das sessões podem ser integrados ao banco de dados conforme a evolução do projeto.

### Componentes

| Componente | Responsabilidade |
|---|---|
| `remote-server` | Receber conexões WebSocket, registrar Agents, iniciar sessões e encaminhar mensagens e frames entre Agent e Client. |
| `remote-agent` | Conectar ao servidor, registrar a identidade do computador, capturar a tela e executar comandos de controle local. |
| `remote-client` | Interface web para solicitar conexão, exibir a tela remota e enviar comandos de interação. |

## Tecnologias

### Servidor (`remote-server`)

- Java e Spring Boot
- Spring WebSocket, usando WebSocket puro (sem STOMP no fluxo atual)
- Jackson para serialização e leitura de JSON
- Registries em memória para conexões e sessões remotas

### Agent (`remote-agent`)

- Java 21+
- Maven
- `java.net.http.WebSocket`
- Jackson para mensagens JSON
- `java.awt.Robot` e `java.awt.Toolkit` para controle e captura da tela
- JPEG enviado como mensagem WebSocket binária

### Client (`remote-client`)

- React
- TypeScript
- Vite
- ESLint
- WebSocket nativo do navegador
- CSS para interface e visualização da tela remota

## Funcionalidades

### Implementado no MVP

- Conexão WebSocket do Agent ao servidor.
- Registro do Agent usando um `computerId` persistido localmente em `computer-id.txt`.
- Conexão WebSocket do Client ao servidor.
- Solicitação de acesso usando `computerId`.
- Criação de uma sessão remota em memória e confirmação pelo Agent.
- Encaminhamento de mensagens de texto entre Client, servidor e Agent.
- Captura da tela principal no Agent e envio de frames JPEG em mensagens binárias WebSocket.
- Exibição dos frames recebidos na interface web.
- Movimento do mouse por meio de `MOUSE_MOVE` e `java.awt.Robot`.

### Em desenvolvimento / próximas etapas

- Finalizar e validar os controles de clique e arraste do mouse.
- Controle completo do teclado e liberação segura de teclas/botões quando a sessão for encerrada.
- Refinar a experiência de tela cheia e controles da visualização.
- Listagem persistente de computadores com status online/offline.
- Autenticação, autorização e consentimento explícito do usuário remoto.
- Histórico persistente de sessões, auditoria e reconexão do Agent.
- Transferência de arquivos e otimizações para vídeo/frames com maior taxa de atualização.

> A lista reflete o estado de desenvolvimento descrito para o MVP. O comportamento de cada recurso depende da versão dos componentes que estiver no repositório local.

## Estrutura dos projetos

A organização recomendada é manter os três componentes em pastas separadas, no mesmo repositório ou em repositórios independentes:

```text
RemoteAccess/
├── remote-server/      # Aplicação Spring Boot
├── remote-agent/       # Aplicação Java instalada no computador remoto
├── remote-client/      # Aplicação React + TypeScript + Vite
└── README.md
```

Os arquivos e nomes internos podem variar de acordo com a estrutura atual de cada projeto.

## Pré-requisitos

- JDK 21 ou compatível com a configuração do Agent e do servidor.
- Maven 3.9+.
- Node.js e npm para o Client React.
- Um ambiente desktop com sessão de usuário ativa para executar o Agent. `java.awt.Robot` não funciona em ambientes sem interface gráfica (headless).
- Porta `8080` disponível para o Spring Boot e, normalmente, `5173` para o servidor de desenvolvimento do Vite.

## Executar localmente

Execute cada componente em seu próprio terminal. Os comandos abaixo pressupõem que você esteja dentro da respectiva pasta do projeto.

### 1. Iniciar o servidor Spring Boot

Na pasta `remote-server`, execute:

```bash
mvn spring-boot:run
```

Também é possível abrir o projeto na IDE e executar a classe principal do Spring Boot.

O endereço local usado nos exemplos é `http://localhost:8080`. Confirme as configurações de porta e os endpoints no `application.properties` ou `application.yml` do seu servidor.

### 2. Iniciar o Agent

Na pasta `remote-agent`, compile o projeto:

```bash
mvn clean package
```

Execute o JAR gerado:

```bash
java -jar target/remote-agent.jar
```

O endpoint WebSocket padrão usado pelo Agent é:

```text
ws://localhost:8080/api/v1/ws/agent
```

É possível passar outro endpoint como argumento, se a versão do Agent o suportar:

```bash
java -jar target/remote-agent.jar ws://localhost:8080/api/v1/ws/agent
```

Na primeira execução, o Agent cria `computer-id.txt` no diretório de trabalho para conservar seu `computerId`. **Mantenha esse arquivo** se quiser preservar a identidade daquele Agent entre execuções. Se apagar o arquivo, a aplicação poderá gerar outro identificador.

### 3. Iniciar o Client React

Na pasta `remote-client`, instale as dependências:

```bash
npm install
```

Inicie o Vite:

```bash
npm run dev
```

Abra o endereço informado no terminal, normalmente:

```text
http://localhost:5173
```

O Client utiliza este endpoint WebSocket:

```text
ws://localhost:8080/api/v1/ws/client
```

Se o projeto usa o proxy de desenvolvimento do Vite, o navegador conecta à origem local do próprio Vite e ele encaminha o tráfego WebSocket ao Spring Boot. Nesse caso, mantenha a configuração de proxy em `vite.config.ts` e reinicie o Vite após alterá-la.

### 4. Testar uma sessão remota

1. Inicie o servidor Spring Boot.
2. Inicie o Agent e copie o `computerId` exibido no terminal.
3. Abra o Client React no navegador.
4. Informe o `computerId` e selecione **Conectar**.
5. O Client envia `CONNECT`; o servidor encaminha `REMOTE_CONNECTION_REQUEST` ao Agent.
6. O Agent responde `CONNECTION_ACCEPTED` e começa a enviar frames de tela enquanto a sessão estiver ativa.
7. O Client exibe a imagem recebida. Os comandos implementados, como `MOUSE_MOVE`, são enviados pelo servidor ao Agent.

Para testar em outra máquina da rede local, substitua `localhost` pelo endereço IP/DNS do servidor nos endpoints necessários e configure firewall, origem do navegador e rede conforme apropriado. Acesso remoto pela internet exige HTTPS/WSS e controles de segurança adicionais.

## Como funciona a comunicação

### Iniciar a conexão

```text
Client                    Spring Boot                       Agent
  │                            │                              │
  │── CONNECT ────────────────►│                              │
  │◄─ CONNECTING ──────────────│                              │
  │                            │── REMOTE_CONNECTION_REQUEST ►│
  │                            │◄─ CONNECTION_ACCEPTED ───────│
  │◄─ CONNECTION_ACCEPTED ─────│                              │
```

A mensagem `CONNECTION_ACCEPTED` deve incluir o `remoteSessionId`. Para calcular coordenadas de mouse corretamente quando a imagem é redimensionada no navegador, a resposta também deve conter `screenWidth` e `screenHeight` reais do monitor remoto.

### Transmitir a tela e comandos

- As mensagens de controle são objetos JSON enviados como mensagens WebSocket de texto.
- Os frames JPEG são enviados como mensagens WebSocket binárias; não são colocados dentro de JSON nem convertidos para Base64.
- O servidor deve encaminhar os frames binários do Agent ao Client associado à sessão remota ativa.
- O Client envia movimentos do mouse em coordenadas relativas à imagem exibida, convertidas para as dimensões reais do monitor remoto.

## Mensagens WebSocket

Os exemplos abaixo ilustram o protocolo do MVP. Os campos exatos devem corresponder aos DTOs e handlers da versão em execução.

### Registro do Agent

```json
{
  "type": "AGENT_REGISTER",
  "computerId": "550e8400-e29b-41d4-a716-446655440000"
}
```

### Solicitação do Client

```json
{
  "type": "CONNECT",
  "computerId": "550e8400-e29b-41d4-a716-446655440000"
}
```

### Solicitação encaminhada ao Agent

```json
{
  "type": "REMOTE_CONNECTION_REQUEST",
  "remoteSessionId": "UUID-DA-SESSAO",
  "computerId": "550e8400-e29b-41d4-a716-446655440000"
}
```

### Confirmação do Agent

```json
{
  "type": "CONNECTION_ACCEPTED",
  "remoteSessionId": "UUID-DA-SESSAO",
  "screenWidth": 1920,
  "screenHeight": 1080
}
```

### Movimento do mouse

```json
{
  "type": "MOUSE_MOVE",
  "remoteSessionId": "UUID-DA-SESSAO",
  "x": 500,
  "y": 300
}
```

### Encerramento da sessão

O protocolo pode utilizar `REMOTE_SESSION_CLOSED` para avisar ao Agent que a sessão terminou e interromper a captura de tela. Confirme que os handlers do servidor limpam o registry e encaminham essa mensagem ao Agent ao desconectar.

## Identidade e estado dos computadores

- `computerId`: identifica o computador registrado; deve permanecer estável entre reinicializações do Agent.
- `WebSocketSession`: representa uma conexão de rede específica e temporária. Seu identificador não deve ser utilizado como identidade permanente do computador.
- `remoteSessionId`: identifica uma sessão remota entre um Client e um Agent.
- **Online/offline:** deve ser calculado a partir da conexão ativa mantida no `ComputerConnectionRegistry`, validando também se a sessão WebSocket continua aberta. `lastSeenAt` pode complementar essa informação no futuro, mas não substitui a conexão ativa.

Responsabilidades recomendadas no servidor:

- `ComputerConnectionRegistry`: mapa de `computerId` para a conexão WebSocket do Agent.
- `RemoteSessionRegistry`: associa as conexões do Client e do Agent durante uma sessão remota.
- `ComputerRepository` e entidade `Computer`: persistência dos computadores cadastrados, se o banco estiver configurado.
- `ComputerService` e DTO de resposta: combinar os dados persistidos e o estado online/offline para a API REST, sem expor `WebSocketSession` ou entidades internas desnecessariamente.

## Próximas etapas

1. Consolidar os testes dos comandos de mouse e teclado, incluindo a liberação de botões/teclas após falhas.
2. Garantir encerramento de sessão e limpeza de recursos em todas as desconexões.
3. Implementar listagem de computadores cadastrados com status atualizado.
4. Adicionar autenticação e autorização do Client e credenciais próprias do Agent.
5. Persistir computadores e histórico das sessões em banco de dados.
6. Adicionar consentimento explícito, logs de auditoria e proteções contra conexões indevidas.
7. Otimizar a captura e transmissão de tela; avaliar WebRTC ou codec de vídeo se for necessária menor latência e maior taxa de quadros.
8. Implementar transferência de arquivos somente depois de estabelecer controles de autorização e validação.

## Segurança

**Não exponha esta versão do MVP diretamente à internet.** O Agent descrito neste projeto aceita automaticamente pedidos de conexão e captura a tela do computador durante uma sessão. Sem autenticação e autorização adequadas, qualquer pessoa capaz de atingir os endpoints pode tentar solicitar ou controlar uma sessão.

Antes de usar fora de um ambiente controlado, implemente pelo menos:

- HTTPS/WSS e validação de origem apropriada.
- Autenticação do Client e identidade/credencial independente para cada Agent.
- Autorização explícita para acessar cada computador e consentimento visível no computador remoto.
- Validação das mensagens, limites de tamanho/taxa e tratamento de desconexões.
- Liberação garantida de botões e teclas em caso de erro ou encerramento.
- Registro auditável de início e fim das sessões e proteção dos dados sensíveis.

Não considere o `computerId` um segredo ou credencial de autenticação: ele serve para identificar um computador, não para provar autorização de acesso.

## Solução de problemas

### O navegador mostra `ERR_BLOCKED_BY_LOCAL_NETWORK_ACCESS_CHECKS`

Em desenvolvimento, use o proxy WebSocket do Vite para encaminhar a comunicação ao Spring Boot. Verifique se `vite.config.ts` tem `ws: true` e reinicie o Vite após a alteração. Não use configurações abertas de origem como substituto para autenticação em produção.

### O Client conecta, mas não recebe a tela

- Confirme que o Agent recebeu `REMOTE_CONNECTION_REQUEST` e iniciou a captura.
- Confirme que o servidor encaminha mensagens binárias, não apenas mensagens de texto.
- Confira se a mensagem binária chega ao WebSocket do Client em **DevTools → Network → WS**.
- Verifique se o tipo de mensagem é um frame JPEG e se os limites de tamanho binário do servidor são suficientes.

### O mouse se move para a posição errada

Confirme que o Agent informa `screenWidth` e `screenHeight` reais na resposta `CONNECTION_ACCEPTED`. O Client deve calcular a posição usando a área real ocupada pela imagem, levando em consideração qualquer redimensionamento e margens.

### O Agent não consegue capturar a tela

Execute-o em uma sessão de desktop ativa. Ambientes headless, serviços executados sem uma sessão interativa e algumas configurações de múltiplos monitores podem impedir o uso esperado de `java.awt.Robot`.

### O computador aparece offline após reconectar

Confirme que `computerId` persiste em `computer-id.txt` e que o handler registra a nova `WebSocketSession`. Ao fechar a conexão antiga, remova-a apenas se ainda for a sessão atual daquele computador; isso evita que uma desconexão antiga remova uma conexão nova.

---

Projeto de aprendizado e prototipação. Ajuste os endpoints, nomes de classes e comandos deste README para refletir a estrutura efetiva dos repositórios à medida que o projeto evoluir.
