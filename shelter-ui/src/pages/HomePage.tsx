import type { AuthState } from "../auth/AuthContext";
import { useAuth } from "../auth/useAuth";

function describe(state: AuthState): string {
  switch (state.status) {
    case "loading":
      return "Loading…";
    case "anonymous":
      return "You are not signed in.";
    case "authenticated":
      return `Signed in as ${state.user.email}`;
  }
}

export default function HomePage() {
  const { state } = useAuth();
  return (
    <main>
      <h1>Shelter</h1>
      <p>{describe(state)}</p>
    </main>
  );
}
