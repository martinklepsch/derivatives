### to be released


- implement own `derived-value` that can be disposed, which will remove watches on sources (atoms or other watchable things) [PR #2](https://github.com/martinklepsch/derivatives/pull/2)
- add Rum mixins to inject multiple derivatives [PR #5](https://github.com/martinklepsch/derivatives/pull/5)  
  Previously you called `d/drv` multiple times:

    ```clojure
    (rum/defcs block < rum/reactive (d/drv :product/page) (d/drv :product/images) (d/drv :product/text) 
      [state]
      (let [page (d/react state :product/page)
            images (d/react state :product/images)
            text (d/react state :product/text)] 
        ...))
    ```

  Now it is possible to pass multiple keywords to `org.martinklepsch.derivatives/drv` and also there is a new function
  `react-all` that can be used to dereference multiple or all known derivatives at once:

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

- **Interal:** the `sync-derivatives!` function and the
  `DerivativesPool` constructor now receive an extra argument
  `watch-key-prefix` that helps avoiding conflicts when creating
  multiple pools from a single source atom with specs that have
  overlapping keys. See
  [#10](https://github.com/martinklepsch/derivatives/issues/10) for
  details.

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