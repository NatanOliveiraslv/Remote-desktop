import { AuthProvider } from "./auth/AuthProvider";
import { useAuth } from "./auth/auth-context";
import { Dashboard } from "./components/Dashboard";
import { LoginPage } from "./components/LoginPage";
import "./App.css";

function AppContent() {
  const { status } = useAuth();

  // Enquanto confirma se já existe uma sessão (por exemplo, após F5).
  if (status === "loading") {
    return (
      <div className="splash">
        <div className="spinner" role="status" aria-label="Carregando" />
      </div>
    );
  }

  return status === "authenticated" ? <Dashboard /> : <LoginPage />;
}

function App() {
  return (
    <AuthProvider>
      <AppContent />
    </AuthProvider>
  );
}

export default App;
