# shelter-proxy

An nginx reverse proxy in front of the application. The browser talks only to the proxy; the proxy passes
`/api/` to the API and everything else to the UI. Both are services on Render, reached over HTTPS.

## What it does

- Sends `/api/...` to the API instance named in `API_UPSTREAM` and everything else to the UI named in `UI_UPSTREAM`.
- Looks up the addresses of the two services again every 30 seconds, so a change of an address does not stop
  the traffic until the proxy is restarted.
- Checks the certificate of the service it connects to.
- Waits up to 180 seconds for a service that is asleep (a free Render instance needs about two minutes to wake up).
- Adds the security headers from `security-headers.conf` to every response, error ones included, and removes the
  same headers that the services send, so each header is sent once.
- Answers three paths by itself, without asking the services:
  - `/proxy-health`: `ok`, the health check target of the platform;
  - `/proxy-version`: the commit this image was built from, which the delivery pipeline compares with the commit it deployed;
  - `/api-host`: the name of the API instance that receives the traffic (blue or green), which the browser uses to wake that instance.

## Files

| File | Purpose |
|---|---|
| `Dockerfile` | nginx image with the configuration; applies Alpine security patches; stores the commit in the image |
| `default.conf.template` | the server configuration; `${...}` values are filled in from environment variables when the container starts |
| `security-headers.conf` | the headers added to every response |
| `verify.sh` | starts the image with made-up upstreams and checks what the proxy answers by itself |

## Environment variables

| Variable | Meaning |
|---|---|
| `API_UPSTREAM` | host name of the API instance that receives the traffic |
| `UI_UPSTREAM` | host name of the UI service |

The delivery pipeline sets `API_UPSTREAM` when it switches the traffic between the two API instances.

## Check before delivery

The pull request build runs `sh verify.sh IMAGE COMMIT`, so a mistake in the configuration fails the pull request and not the
delivery to staging.

## Limits

- The proxy does not wake a sleeping service. The platform wakes a free instance for requests that come from outside,
  but not for requests that come from another service, such as this proxy. The browser therefore wakes the API instance
  itself (it asks `/api-host` for the name), and the delivery pipeline wakes the services before it tests them.
- The proxy forwards what it receives. It has no rate limit and no web application firewall.
