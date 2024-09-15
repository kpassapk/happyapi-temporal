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

Most Web application [architectures][hexagonal] would recommend strict separation 
between handler code and domain logic to result in more portable and testable code.

[hexagonal]: https://en.wikipedia.org/wiki/Hexagonal_architecture_(software)

HappyAPI provides a middleware stack that starts a web server. This is
appropriate for desktop applications and tools, and works out of the box.

HappyAPI provides an async variant of middleware that can be used with
servers such as netty, but the authentication flow itself is still
syncrhonous.

## OAuth2 flow

In this project, we implement an authentication workflow in Tepmoral
to model the authenticaiton workflow. The workflow is as follows:

1. Create OAuth2 state string
2. In handler, construct a login URL with the state string

User clicks the login link, and Google redirects back with authentication code and state

3. If state is the same as the original state string, exchange authentication code for a token.

4. Store the token to a database.

It is not hard to implement this flow with a database. However

- You don't have a single function that looks like steps 1-4 above.
- You may end up with database models for this work-in-progress, which are not significant
to the application in any other way
- You will have to store timestamps and check 

In this project we use Temporal to implement the authorization Oauth2 code for HappyAPI.

## Temporal

Temporal is a workflow orchestration tool to run long-running workflows. It provides a friendly user
interface and developer-friendly SDKs for many languages.

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
