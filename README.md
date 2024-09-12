# HappyAPI + Biff + Temporal

This project uses the [temporal][temporal] workflow orchestrator to
run an async authentication workflow for [happyapi][happyapi] using [Biff][biff].

[temporal]: https://temporal.io/
[happyapi]: https://github.com/timothypratley/happyapi
[biff]: https://biffweb.com

## Why? 

Oauth2 is the dominant authentication mechanism for Web APIs. There are 
thousands of APIs using OAuth2 with valuable data and actions.

OAuth2 is simple in principle, but a bit tricky to implement in an
organized way.

Authentications flows are callback-driven, which can result in a lot
of logic in the handlers.

Web applications need a more explicit handling of the redirects flow and handlers.

Most application [architectures][hexagonal] would recommend this kind
of separation to make code more portable and testable.

[hexagonal]: https://en.wikipedia.org/wiki/Hexagonal_architecture_(software)

HappyAPI provides a middleware stack that starts a web server. This is
appropriate for desktop applications and tools. 

HappyAPI provides an async variant of middleware that can be used with
servers such as netty, [X] and [Y], but the authentication flow itself
is still syncrhonous.

Temporal is a workflow orchestration tool to run long-running
workflows. It provides a friendly user interface and
developer-friendly SDKs for many languages.

This application allows the user to connect efficiently to a Google Sheet.

## Running locally

Set up credentials:

https://console.cloud.google.com/apis/credentials?project=ai-unifica-gsheets-test

Redirect URL should be http://localhost:8080/redirect

Retain client ID and secret, and add to .env file as

`HAPPYAPI_GOOGLE_CLIENT_ID`
`HAPPYAPI_GOOGLE_SECRET`

Go to `dev/repl.clj` and run `add-fixtures`.

Start Temporal development server

```
temporal server start-dev
```

Start application

```
clj -M:dev dev
```

Log onto the application.
