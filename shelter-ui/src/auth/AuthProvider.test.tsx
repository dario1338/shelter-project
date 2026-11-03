import { fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { jsonResponse } from "../test/http";
import { AuthProvider } from "./AuthProvider";
import { clearAccessToken } from "./tokenStore";
import { useAuth } from "./useAuth";

const fetchMock = vi.fn<typeof fetch>();

function Probe() {
  const { state, signIn, signOut } = useAuth();
  return (
    <div>
      <p>
        {state.status === "authenticated"
          ? `user:${state.user.email}`
          : state.status}
      </p>
      <button onClick={() => void signIn("ana@example.com", "secret")}>
        sign in
      </button>
      <button onClick={() => void signOut()}>sign out</button>
    </div>
  );
}

const user = {
  id: 1,
  email: "ana@example.com",
  firstName: "Ana",
  lastName: "Anić",
  role: "USER",
};

beforeEach(() => {
  clearAccessToken();
  fetchMock.mockReset();
  vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe("AuthProvider", () => {
  it("signs a visitor in and loads the current user", async () => {
    fetchMock
      .mockResolvedValueOnce(new Response(null, { status: 401 })) // restore attempt on load
      .mockResolvedValueOnce(jsonResponse(200, { accessToken: "t1" })) // login
      .mockResolvedValueOnce(jsonResponse(200, user)); // /me

    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    );
    expect(await screen.findByText("anonymous")).toBeInTheDocument();

    fireEvent.click(screen.getByText("sign in"));

    expect(await screen.findByText("user:ana@example.com")).toBeInTheDocument();
  });

  it("signs the user out", async () => {
    fetchMock
      .mockResolvedValueOnce(jsonResponse(200, { accessToken: "t1" })) // restore
      .mockResolvedValueOnce(jsonResponse(200, user)) // /me
      .mockResolvedValueOnce(new Response(null, { status: 204 })); // logout

    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    );
    expect(await screen.findByText("user:ana@example.com")).toBeInTheDocument();

    fireEvent.click(screen.getByText("sign out"));

    expect(await screen.findByText("anonymous")).toBeInTheDocument();
  });

  it("treats a failed user lookup after a restored session as anonymous", async () => {
    fetchMock
      .mockResolvedValueOnce(jsonResponse(200, { accessToken: "t1" }))
      .mockResolvedValueOnce(new Response(null, { status: 500 }));

    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    );

    expect(await screen.findByText("anonymous")).toBeInTheDocument();
  });
});

describe("useAuth", () => {
  it("fails outside of an AuthProvider", () => {
    vi.spyOn(console, "error").mockImplementation(() => undefined);

    expect(() => render(<Probe />)).toThrow(
      "useAuth must be used inside AuthProvider",
    );
  });
});
