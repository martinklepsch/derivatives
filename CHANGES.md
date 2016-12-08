(See [legend](#legend) at the end for some rough definition of the bold labels in front of each item.)

### 0.2.0

- **Improvement** Implement own `derived-value` that can be disposed, which will remove watches on sources (atoms or other watchable things). Previously Rum's `derived-atom` was used which does not support cleaning up watches when they are no longer needed. [PR #2](https://github.com/martinklepsch/derivatives/pull/2)
- **New Feature** Extend Rum `drv` mixin to accept multiple arguments [PR #5](https://github.com/martinklepsch/derivatives/pull/5) & [PR #8](https://github.com/martinklepsch/derivatives/pull/8).
  Previously you called `d/drv` multiple times:

    ```clojure
    (rum/defcs block < rum/reactive (d/drv :product/page) (d/drv :product/images) (d/drv :product/text) 
      [state]
      (let [page   (d/react state :product/page)
            images (d/react state :product/images)
            text   (d/react state :product/text)] 
        ...))
    ```

  Now it is possible to pass multiple keywords to the same function
  with the same result as multiple invocations. Also there is a new
  function `react-all` that can be used to dereference multiple or all
  known derivatives at once:

    ```clojure
    (rum/defcs block < rum/reactive (d/drv :product/page :product/images :product/text)
      [state]
      (let [{:keys [:product/text]} (d/react-all state)]
        [:p text] ,,,))
    ```

    ```clojure
    (d/react-all state) -> {:product/page 'val :product/images 'val :product/text 'val}
    (d/react-all state :product/page) -> {:product/page 'val}
    ```

- **Improvement** The `sync-derivatives!` function and the
  `DerivativesPool` record constructors now receive an extra argument
  `watch-key-prefix` that helps avoiding conflicts when creating
  multiple pools from a single source atom with specs that have
  overlapping keys. See
  [#10](https://github.com/martinklepsch/derivatives/issues/10) for
  details.
- **Bugfix** Fix wrong assumption in tests that would cause them to fail when a spec
  contains more complex keys: [`37cda80`](https://github.com/martinklepsch/derivatives/commit/37cda80c35a5c936ac8bf4fe84a2595362bd93e4)
- **Bugfix** Fix issue where `sync-derivatives!` would fail if spec keys don't implement IFn [`b5f9545`](https://github.com/martinklepsch/derivatives/commit/b5f9545437823fa8b9730ca8f32a00eaa9d85f02)

### 0.1.1

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

#### Legend

- **Improvement**: An improvement that is not breaking compatibility and thus should not require action from your side
- **New Feature**: An extension to the API that may make your life easier
- **Bugfix**: Fix of broken behavior. If you think you might rely on the bug check the linked commit.
