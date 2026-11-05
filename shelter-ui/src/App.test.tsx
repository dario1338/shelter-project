import { fireEvent, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { clearAccessToken } from "./auth/tokenStore";
import { jsonResponse } from "./test/http";
import { renderApp } from "./test/renderApp";
import { ana } from "./test/users";

const fetchMock = vi.fn<typeof fetch>();

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
    expect(screen.getByRole("link", { name: "Sign in" })).toHaveAttribute(
      "href",
      "/login",
    );
    expect(
      screen.getByRole("link", { name: "Create account" }),
    ).toHaveAttribute("href", "/register");
  });

  it("restores the session from the refresh cookie", async () => {
    fetchMock
      .mockResolvedValueOnce(jsonResponse(200, { accessToken: "t1" }))
      .mockResolvedValueOnce(jsonResponse(200, ana));

    renderApp();

    expect(
      await screen.findByText("Signed in as ana@example.com"),
    ).toBeInTheDocument();
    expect(screen.getByText("Ana")).toBeInTheDocument();
  });

  it("signs the user out from the header", async () => {
    fetchMock
      .mockResolvedValueOnce(jsonResponse(200, { accessToken: "t1" }))
      .mockResolvedValueOnce(jsonResponse(200, ana))
      .mockResolvedValueOnce(new Response(null, { status: 204 }));
    renderApp();
    await screen.findByText("Signed in as ana@example.com");

    fireEvent.click(screen.getByRole("button", { name: "Sign out" }));

    expect(
      await screen.findByText("You are not signed in."),
    ).toBeInTheDocument();
  });

  it("signs the user out even when the server cannot be reached", async () => {
    fetchMock
      .mockResolvedValueOnce(jsonResponse(200, { accessToken: "t1" }))
      .mockResolvedValueOnce(jsonResponse(200, ana))
      .mockRejectedValueOnce(new TypeError("network down"));
    renderApp();
    await screen.findByText("Signed in as ana@example.com");

    fireEvent.click(screen.getByRole("button", { name: "Sign out" }));

    expect(
      await screen.findByText("You are not signed in."),
    ).toBeInTheDocument();
  });

  it("lets the visitor switch the theme from the header", async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 401 }));
    renderApp();
    await screen.findByText("You are not signed in.");

    fireEvent.click(screen.getByRole("button", { name: "Switch to light theme" }));

    expect(
      screen.getByRole("button", { name: "Switch to dark theme" }),
    ).toBeInTheDocument();
  });

  it("renders the not-found page for unknown routes", async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 401 }));

    renderApp("/nope");

    expect(await screen.findByText("Page not found")).toBeInTheDocument();
    expect(
      screen.getByRole("link", { name: "Go to the home page" }),
    ).toHaveAttribute("href", "/");
  });
});
