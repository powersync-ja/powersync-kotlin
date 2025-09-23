# SQLDelight + Sync streams demo

This demo is a simple JVM todolist application using PowerSync. It differs from the
[main demo](../supabase-todolist) by:

1. Using SQLDelight for database queries.
2. Using sync streams to sync items in each list on demand instead of upfront.
3. Again for simplicity, using a self-host demo instead of Supabase.

## Start

To start the self-hosted backend, run `docker compose up` in [this directory](https://github.com/powersync-ja/self-host-demo/tree/main/demos/nodejs).

Also, to use sync streams, update the contents of [this file](https://github.com/powersync-ja/self-host-demo/blob/main/config/sync_rules.yaml) with

```yaml
# Sync-rule docs: https://docs.powersync.com/usage/sync-rules
streams:
  lists:
    query: SELECT * FROM lists #WHERE owner_id = auth.user_id()
    auto_subscribe: true
  todos:
    query: SELECT * FROM todos WHERE list_id = subscription.parameter('list') #AND list_id IN (SELECT id FROM lists WHERE owner_id = auth.user_id())

config:
  edition: 2
```

Afterwards, start the `:demos:self-host-sync-streams:run` task in Gradle.
