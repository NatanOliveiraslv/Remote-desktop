# Remote Agent (MVP)

Projeto Java 21 mínimo para o computador que será acessado remotamente. Ele conecta ao WebSocket do `remote-server`, registra um `computerId` persistente, aceita pedidos de conexão e trata o comando `MOUSE_MOVE`.

## Requisitos

- JDK 21 instalado
- Maven 3.9+ instalado
- O Spring Boot `remote-server` em execução
- Uma sessão de desktop local disponível (o `java.awt.Robot` não funciona em ambientes sem interface gráfica)

## Endpoint do servidor

O padrão configurado é:

```text
ws://localhost:8080/api/v1/ws/agent
```

Esse caminho precisa ser igual ao registrado no `WebSocketConfig` do seu Spring Boot. Para usar outro endereço, informe a propriedade `agent.server-uri` na execução.

## Compilar

No terminal, na pasta `remote-agent`:

```bash
mvn clean package
```

O arquivo executável será criado em `target/remote-agent.jar`.

## Executar

Windows (PowerShell):

```powershell
java -jar target/remote-agent.jar
```

Linux/macOS:

```bash
java -jar target/remote-agent.jar
```

Para indicar outro endpoint:

```bash
java -Dagent.server-uri=ws://localhost:8080/api/v1/ws/agent -jar target/remote-agent.jar
```

Na primeira execução, o programa cria `computer-id.txt` na pasta atual. Esse UUID é reutilizado nas próximas execuções. Não apague esse arquivo se quiser que o computador mantenha a mesma identidade registrada.

## Protocolo implementado

### Agent -> servidor, ao conectar

```json
{
  "type": "AGENT_REGISTER",
  "computerId": "UUID-PERSISTIDO-EM-COMPUTER-ID.TXT"
}
```

### Servidor -> Agent, ao solicitar conexão

```json
{
  "type": "REMOTE_CONNECTION_REQUEST",
  "remoteSessionId": "UUID-DA-SESSAO",
  "computerId": "UUID-DO-COMPUTADOR"
}
```

O Agent responde automaticamente, para o MVP:

```json
{
  "type": "CONNECTION_ACCEPTED",
  "remoteSessionId": "UUID-DA-SESSAO"
}
```

### Client -> servidor -> Agent

```json
{
  "type": "MOUSE_MOVE",
  "remoteSessionId": "UUID-DA-SESSAO",
  "x": 500,
  "y": 300
}
```
O servidor deve encaminhar a mensagem ao Agent com o mesmo `remoteSessionId`. O Agent só executa o comando se corresponder à sessão aceita e se as coordenadas estiverem dentro da tela principal.
