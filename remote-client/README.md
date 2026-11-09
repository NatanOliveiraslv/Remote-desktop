# remote-client

Front-end (React + TypeScript + Vite) do sistema de acesso remoto.

## Como rodar

```bash
npm install
npm run dev
```

O app abre em http://localhost:5173. Em desenvolvimento, o Vite encaminha tudo que
começa com `/api` (REST e WebSocket) para o servidor Spring Boot em
`http://localhost:8080`, então o servidor precisa estar em execução.

## Fluxo atual

1. A lista de computadores vem de `GET /api/v1/computers` e é atualizada a cada 5 s.
2. Ao clicar em um computador **online**, abre uma sessão em tela cheia com o
   carregamento e, assim que chega o primeiro frame, a tela do PC remoto.
3. Zoom na tela remota (até 10x, conforme o tamanho da tela):
   - **Celular:** pinça com dois dedos dá zoom e move a imagem. Um dedo continua
     sendo o mouse (toque = clique, arrastar = arrastar).
   - **PC:** botões − / + na barra, Ctrl + roda do mouse (ou pinça do trackpad) e
     o botão "Mover" para arrastar a imagem ampliada. Clicar no percentual volta a 100%.
4. **Login:** e-mail e senha, cadastro (nome, sobrenome, e-mail e senha) e login com
   Google. O access token fica só em memória e vai em `Authorization: Bearer` nas
   requisições; ele é renovado sozinho pelo cookie `refreshToken` (HttpOnly), o que
   também mantém o login após F5. O WebSocket recebe o token em `?access_token=`
   (o navegador não envia headers no WebSocket).

## Configuração do servidor para o login

- `frontend.url` (application.yaml) deve ser a origem do front: `http://localhost:5173`.
  É para lá que o servidor redireciona (`/login-success`) depois do login com Google.
- No Google Cloud, a URI de redirecionamento autorizada continua apontando para o
  servidor: `http://localhost:8080/api/v1/login/oauth2/code/google`.
- O `SecurityFilter` precisa aceitar `access_token` na URL do handshake de `/ws/client`.

## Scripts

- `npm run dev`: servidor de desenvolvimento
- `npm run build`: checagem de tipos e build de produção
- `npm run lint`: ESLint
