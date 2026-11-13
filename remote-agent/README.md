# Remote Agent (MVP)

Projeto Java 21 para o computador que será controlado remotamente. Ele conecta ao WebSocket do `remote-server` com autenticação segura (`computerId` e `agentToken`), aceita pedidos de conexão remota, transmite a tela em tempo real e processa comandos de mouse.

## Requisitos

- JDK 21 instalado
- Maven 3.9+ instalado
- O Spring Boot `remote-server` em execução
- Sessão de desktop ativa com interface gráfica (necessário para `java.awt.Robot` e captura de tela)

## Autenticação e Credenciais

O `remote-server` agora exige autenticação durante o handshake do WebSocket.
Ao cadastrar um computador no servidor (`POST /api/v1/computers/create`), o servidor gera o `id` (`computerId`) e o `agentToken` correspondente.

### Formas de fornecer as credenciais ao Agent

1. **Parâmetros de linha de comando**:
   ```bash
   java -jar target/remote-agent.jar --computer-id <UUID> --token <AGENT_TOKEN>
   ```
2. **Propriedades de Sistema (JVM)**:
   ```bash
   java -Dagent.computer-id=<UUID> -Dagent.token=<AGENT_TOKEN> -jar target/remote-agent.jar
   ```
3. **Variáveis de Ambiente**:
   ```bash
   export AGENT_COMPUTER_ID="<UUID>"
   export AGENT_TOKEN="<AGENT_TOKEN>"
   java -jar target/remote-agent.jar
   ```
4. **Armazenamento Seguro Criptografado (`.agent-credentials`)**:
   - Ao executar o Agent pela primeira vez em um terminal interativo sem passar credenciais, o Agent solicita interativamente o `computerId` e o `agentToken`.
   - As credenciais são criptografadas em repouso com **AES-256-GCM** e chave derivada via **PBKDF2** vinculada ao usuário e máquina atual.
   - No Windows, são aplicadas permissões estritas de ACL; em Linux/macOS, permissão `0600` (`rw-------`), garantindo que outros usuários do sistema operacional não tenham acesso ao token.
   - Nas execuções subsequentes, o Agent carrega automaticamente as credenciais salvas em `.agent-credentials`.

## Endpoint do Servidor

O endpoint padrão configurado é:

```text
ws://localhost:8080/api/v1/ws/agent
```

Durante o handshake HTTP do WebSocket, o Agent envia os parâmetros de autenticação via query string e headers:
- Query parameters: `?computerId=<UUID>&token=<AGENT_TOKEN>&agentToken=<AGENT_TOKEN>`
- Headers: `X-Agent-Token` e `X-Computer-Id`

Para apontar para outro servidor:
```bash
java -jar target/remote-agent.jar --server-uri ws://seu-servidor:8080/api/v1/ws/agent
```

## Compilar e Testar

Na pasta `remote-agent`:

```bash
# Executar testes unitários
mvn test

# Gerar JAR executável com dependências
mvn clean package
```

O arquivo executável será gerado em `target/remote-agent.jar`.

## Executar

```bash
java -jar target/remote-agent.jar
```
Ou com parâmetros explícitos:
```powershell
java -jar target/remote-agent.jar --computer-id "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx" --token "7f8b9c..."
```

## Protocolo

1. **Handshake e Conexão**:
   - O Agent conecta a `/api/v1/ws/agent?computerId=...&token=...`
   - O `AgentHandshakeInterceptor` valida o hash SHA-256 do token no banco de dados em tempo constante.
   - Após o handshake aceito, o Agent é registrado e fica online imediatamente.
2. **Reconexão Automática**:
   - Em caso de desconexão ou indisponibilidade temporária do servidor, o Agent agenda tentativas automáticas de reconexão.
3. **Controle Remoto e Transmissão**:
   - `REMOTE_CONNECTION_REQUEST`: O Agent responde com `CONNECTION_ACCEPTED` e envia as dimensões da tela.
   - Transmissão periódica de frames JPEG via mensagens binárias WebSocket.
   - Comandos `MOUSE_MOVE`, `MOUSE_BUTTON_DOWN`, `MOUSE_BUTTON_UP` validados pelo `remoteSessionId` ativo.
