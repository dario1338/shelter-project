import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import App from "./App";
import { AuthProvider } from "./auth/AuthProvider";
import { clearAccessToken } from "./auth/tokenStore";
import { jsonResponse } from "./test/http";

const fetchMock = vi.fn<typeof fetch>();

function renderApp(path = "/") {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <App />
      </AuthProvider>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  clearAccessToken();
  fetchMock.mockReset();
  vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("App", () => {
  it("shows an anonymous visitor when there is no session to restore", async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 401 }));

    renderApp();

    expect(
      await screen.findByText("You are not signed in."),
    ).toBeInTheDocument();
  });

  it("restores the session from the refresh cookie", async () => {
    fetchMock
      .mockResolvedValueOnce(jsonResponse(200, { accessToken: "t1" }))
      .mockResolvedValueOnce(
        jsonResponse(200, { email: "ana@example.com", role: "USER" }),
      );

    renderApp();

    expect(
      await screen.findByText("Signed in as ana@example.com"),
    ).toBeInTheDocument();
  });

  it("renders the not-found page for unknown routes", async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 401 }));

    renderApp("/nope");

    expect(await screen.findByText("Page not found")).toBeInTheDocument();
  });
});
