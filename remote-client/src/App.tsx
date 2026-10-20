import { useRemoteSession } from "./hooks/useRemoteSession";
import { Sidebar } from "./components/Sidebar";
import { ConnectionCard } from "./components/ConnectionCard";
import { RemoteScreen } from "./components/RemoteScreen";
import "./App.css";

function App() {
  const session = useRemoteSession();

  return (
    <div className="app">
      <Sidebar />
      <main className="main">
        <header className="topbar">
          <div>
            <p className="eyebrow">ACESSO REMOTO</p>
            <h2>Conectar a um computador</h2>
            <p className="page-description">
              Acesse e controle seus computadores remotamente.
            </p>
          </div>
          <div className="user-avatar">U</div>
        </header>

        <ConnectionCard
          computerId={session.computerId}
          onComputerIdChange={session.setComputerId}
          status={session.status}
          statusMessage={session.statusMessage}
          onConnect={session.connect}
          onDisconnect={session.disconnect}
        />

        <RemoteScreen
          status={session.status}
          screenImageUrl={session.screenImageUrl}
          remoteSessionId={session.remoteSessionId}
          remoteScreenWidth={session.remoteScreenWidth}
          remoteScreenHeight={session.remoteScreenHeight}
          framesReceived={session.framesReceived}
          socketRef={session.socketRef}
        />
      </main>
    </div>
  );
}

export default App;
