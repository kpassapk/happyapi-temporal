# HappyAPI / Temporal

Set up credentials:

https://console.cloud.google.com/apis/credentials?project=ai-unifica-gsheets-test

`HAPPYAPI_GOOGLE_CLIENT_ID`
`HAPPYAPI_GOOGLE_SECRET`

Go to `dev/repl.clj` and seed the DB with a user



Start Temporal development server

```
temporal server start-dev
```

Start application

```
clj -M:dev dev
```
