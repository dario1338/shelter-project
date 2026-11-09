import { fireEvent, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { clearAccessToken } from "./auth/tokenStore";
import { dog, dogPage } from "./test/dogs";
import { jsonResponse, routeFetch } from "./test/http";
import { renderApp } from "./test/renderApp";
import { admin, ana } from "./test/users";

const fetchMock = vi.fn<typeof fetch>();

beforeEach(() => {
  clearAccessToken();
  fetchMock.mockReset();
  vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => {
  vi.unstubAllGlobals();
});

// The application as the API would answer it: a session when a user is given, and the dogs of the home page.
function serve(user?: typeof ana) {
  routeFetch(fetchMock, {
    "/api/auth/refresh": () => (user ? jsonResponse(200, { accessToken: "t1" }) : new Response(null, { status: 401 })),
    "/api/auth/me": () => jsonResponse(200, user),
    "/api/auth/logout": () => new Response(null, { status: 204 }),
    "/api/dogs": () => jsonResponse(200, dogPage([dog({ id: 3, name: "Luna" })])),
  });
}

describe("App", () => {
  it("shows an anonymous visitor the way to sign in or register", async () => {
    serve();

    renderApp();

    expect(await screen.findByRole("link", { name: "Sign in" })).toHaveAttribute("href", "/login");
    expect(screen.getByRole("link", { name: "Create account" })).toHaveAttribute("href", "/register");
  });

  it("links to the dogs from the header", async () => {
    serve();

    renderApp();
    await screen.findByRole("link", { name: "Sign in" });

    expect(screen.getByRole("link", { name: "Dogs" })).toHaveAttribute("href", "/dogs");
  });

  it("restores the session from the refresh cookie", async () => {
    serve(ana);

    renderApp();

    expect(await screen.findByRole("button", { name: "Sign out" })).toBeInTheDocument();
    expect(screen.getByText("Ana")).toBeInTheDocument();
  });

  it("keeps the links for the role next to the dogs, and the account on the other side", async () => {
    serve(ana);

    renderApp();
    await screen.findByRole("button", { name: "Sign out" });

    const main = screen.getByRole("navigation", { name: "Main" });
    expect(main).toHaveTextContent("ShelterDogsMy dogsMy applications");
    expect(screen.getByRole("link", { name: "My applications" })).toHaveAttribute("href", "/applications");
    expect(main).not.toContainElement(screen.getByRole("button", { name: "Sign out" }));
  });

  it("gives an administrator the applications next to the dogs", async () => {
    serve(admin);

    renderApp();
    await screen.findByRole("button", { name: "Sign out" });

    const main = screen.getByRole("navigation", { name: "Main" });
    expect(main).toHaveTextContent("ShelterDogsApplications");
    expect(screen.getByRole("link", { name: "Applications" })).toHaveAttribute(
      "href",
      "/admin/applications?status=PENDING",
    );
  });

  it("marks the page that is open in the navigation", async () => {
    serve();

    renderApp("/dogs");

    expect(await screen.findByRole("link", { name: "Dogs" })).toHaveAttribute("aria-current", "page");
  });

  it("signs the user out from the header", async () => {
    serve(ana);
    renderApp();
    await screen.findByRole("button", { name: "Sign out" });

    fireEvent.click(screen.getByRole("button", { name: "Sign out" }));

    expect(await screen.findByRole("link", { name: "Sign in" })).toBeInTheDocument();
  });

  it("signs the user out even when the server cannot be reached", async () => {
    serve(ana);
    routeFetch(fetchMock, {
      "/api/auth/refresh": () => jsonResponse(200, { accessToken: "t1" }),
      "/api/auth/me": () => jsonResponse(200, ana),
      "/api/auth/logout": () => {
        throw new TypeError("network down");
      },
      "/api/dogs": () => jsonResponse(200, dogPage([])),
    });
    renderApp();
    await screen.findByRole("button", { name: "Sign out" });

    fireEvent.click(screen.getByRole("button", { name: "Sign out" }));

    expect(await screen.findByRole("link", { name: "Sign in" })).toBeInTheDocument();
  });

  it("lets the visitor switch the theme from the header", async () => {
    serve();
    renderApp();
    await screen.findByRole("link", { name: "Sign in" });

    fireEvent.click(screen.getByRole("button", { name: "Switch to light theme" }));

    expect(screen.getByRole("button", { name: "Switch to dark theme" })).toBeInTheDocument();
  });

  it("renders the not-found page for unknown routes", async () => {
    serve();

    renderApp("/nope");

    expect(await screen.findByText("Page not found")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Go to the home page" })).toHaveAttribute("href", "/");
  });
});
