export function Sidebar() {
  return (
    <aside className="sidebar">
      <div className="brand">
        <div className="brand-icon">R</div>

        <div>
          <h1>RemoteAccess</h1>
          <span>Controle remoto</span>
        </div>
      </div>

      <nav>
        <div className="nav-item active">
          <span>▦</span>
          Computador remoto
        </div>
      </nav>

      <div className="sidebar-footer">
        <span className="status-dot" />
        Aguardando conexão
      </div>
    </aside>
  );
}

