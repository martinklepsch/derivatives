# Derivatives [![CircleCI](https://circleci.com/gh/martinklepsch/derivatives.svg?style=svg)](https://circleci.com/gh/martinklepsch/derivatives)

*Subscriptions distilled.*

[usage](#usage) | [comparisons](#comparisons) | [change log](https://github.com/martinklepsch/derivatives/blob/master/CHANGES.md) | [API docs](https://martinklepsch.github.io/derivatives/)

**A note on terminology:** There are a lot of things with similar meanings/use-cases around: subscriptions, reactions, derived atoms, view models. 
I'll introduce another to make things even worse: *derivative*. A *derivative* implements `IWatchable` and it's value is the result of applying a function to the value of other things (*sources*) implementing `IWatchable`. Whenever any of the *sources* change the value of the *derivative* is updated.

## Why this library

Let's assume you're hooked about the idea of storing all your application state in a single atom (`db`). (Why this is a great idea is covered elsewhere.)

Most of your components don't need the entirety of this state and instead receive a small selection of it that is enough to allow the components to do their job. Now that data is not always a subtree (i.e. `(get-in db ...)`) but might be a combination of various parts of your state. To transform the data in a way that it becomes useful for components you can use a function: `(f @db)`. 

Now you want to re-render your application whenever `db` changes so the views are representing the data in `db`. You end up calling `f` a lot, and remember, `f` has to do all the transformation for all components that **could** be rendered on the page, pretty inefficient!

To optimise we can create *derivatives* that contain data in shapes ideal to specific components and re-render those components when the *derivative* supplying the data changes.

These *derivatives* may depend on other *derivatives*, all ultimately leading up to your single `db` atom. To keep things efficient we only recalculate the value of a *derivative* when any of it's *sources* changes.

The intention of this library is to make the creation and usage of these interdependent references (*derivatives*) simple and efficient. 

A secondary objective is also to achieve the above without relying on global state being defined at the namespace level of this library. (See re-frame vs. pure-frame.)

### What this library helps with

- transform `db` into shapes suited for rendering (a.k.a. view models)
- managing a pool of *derivatives* so only needed *derivatives* are created and freed as soon as they become unused (currently Rum specific)
- server-side rendering (to some degree)

### What this library doesn't help with

- Ensuring the required data is in `db` (server/client rendering)
- [Parameterized Subscriptions](#why-no-parameterized-subscriptions)

## Usage

[](dependency)
```clojure
[org.martinklepsch/derivatives "0.3.1-alpha"] ;; latest release
```
[](/dependency)

*Derivatives* of your application state can be defined via a kind of specification like the one below:

```clojure
(def *db-atom (atom 0))

(def drv-spec
  {;; a source with no dependencies
   :db     [[]         *db-atom]
   ;; a derivative with a dependency
   :inc    [[:db]      (fn [db] (inc db))]
   ;; a derivative with multiple dependencies
   :as-map [[:db :inc] (fn [db inc] {:db db :inc inc})]}
```

A specification like the above can be easily turned into a map with the same keys where the values are *derivatives* (see `org.martinklepsch.derivatives/build`).

Also it can be turned into a registry that can help with only creating needed derivatives and freeing them up when they become unused (see `org.martinklepsch.derivatives/derivatives-pool`).

> What follows is Rum specific and this library has a dependency on Rum but this pattern could be used with old Om apps, or even Reagent's reactions. I'm very open to changes in that direction.

In a Rum component tree you might use *derivatives* as follows (assuming `*db-atom` and `drv-spec` as above):

```clojure
(rum/defcs derived-view < rum/reactive (d/drv :inc) (d/drv :as-map)
  [s]
  [:div
   [:p ":inc "    (-> (d/react s :inc) pr-str)]
   [:p ":as-map " (-> (d/react s :as-map) pr-str)]])

(rum/defc app < (d/rum-derivatives drv-spec)
  [spec]
  [:div
   [:button {:on-click #(swap! *db-atom inc)} "inc"]
   (derived-view)])
```

The `rum-derivatives` mixin adds two functions to the React context of all child components: one to get a *derivative* and one to release it. The `drv` mixin adds hooks to your components that do exactly that and allow you to access the derivatives via component state. 

## Comparisons

#### Plain `rum.core/derived-atom`

Rum's derived-atoms serve as building block in this library but there are some things which are (rightfully) not solved by derived-atoms:

- Creation of interdependent *derivative* chains and
- a mechanism to only create actually needed derived-atoms.

A small code sample should illustrate this well:

```clojure
(def *db (atom {:count 0})) ; base db

(def *increased 
  (rum/derived-atom [*db]
                    ::increased 
                    (fn [db]
                      (inc (:count db)))))
  
(def *as-map
  (rum/derived-atom [*db *increased] 
                    ::as-map 
                    (fn [db incd] 
                      {:db db :increased incd})))
```

compared with the way this could be described using *derivatives*:

```clojure
(def *db (atom {:count 0}))

(def spec
  ;; {name    [depends-upon     derive-fn]}
  {:db        [[]               *db]
   :increased [[:db]            (fn [db] (inc (:count db)))]
   :as-map    [[:db :increased] (fn [db incd] {:db db :increased incd})]})
```

The benefit here is that we don't use vars to make sure the dependencies are met and that we provide this information in a way that can easily be turned into a dependency graph (data FTW) which will later help us only calculating required *derivatives* (done by `derivatives-pool`). In comparison the first snippet will create derived-atoms and recalculate them whenever any of their dependencies change, no matter if you're using the derived-atom in any of your views.


#### Re-Frame Subscriptions

The way they work Re-Frame's dynamic subscriptions are not much different from the approach chosen here, they vary in two ways however:

- In Re-Frame you can do `(subscribe [:sub-id "a parameter"])`, with *derivatives* you can't. Instead these parameters need to be put into `db` and be used (potentially via another *derivative*) from there.
- In Re-Frame subscriptions may have side-effects to listen to remote changes etc. This library does not intend to solve this kind of problem and thus side effects are discouraged.

#### Why no parameterized subscriptions?

In my personal experience a lot of non-idiomatic, non performance
optimal re-frame use comes from having subscriptions in every corner
of the code. Parameterized subscriptions enable this even more.

While a bandaid more than a solution the lack of parameterized
subscriptions in Derivatives is meant to discourage ad-hoc, throwaway
subscription use and instead encourage thoughtful reshaping of data
from your DB into a form suitable for rendering.

## Contributing

Feedback and PRs welcome.

Tests can be run with `boot --source-paths test test` or `boot -s test test`.

--

**License:** MPLv2, see `LICENSE`.
