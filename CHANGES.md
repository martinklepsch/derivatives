### 0.1.1 (unreleased)

- add tests
- implement `build` in terms of `sync-derivatives`
- add assertions that spec is a map
- when checking if something is a function to use to derive a new
  value or if it is a source we now use `(implements? IWatchable x)`
  instead of `(fn? x)`
- refactor pooling implementation to allow testing of internals

- **Rum mixins:** simplifiy keys used in `childContext` to be strings
- **Rum mixins:** make sure the token is correctly passed to `get!` and `release!` functions

### 0.1.0 (04-06-2016)

- initial release