[![CircleCI](https://img.shields.io/circleci/build/github/clj-commons/virgil/master.svg)](https://circleci.com/gh/clj-commons/virgil/tree/master)
[![Clojars Project](https://img.shields.io/clojars/v/virgil/virgil.svg)](https://clojars.org/virgil/virgil)
[![Downloads](https://img.shields.io/clojars/dt/virgil/virgil?color=cornflowerblue)](https://clojars.org/virgil/virgil)

# Virgil

<img src="docs/cocytus.jpg" align="right" width="400"/>

Do you tarnish your Clojure with the occasional hint of Java? Have you become
indescribably tired of reloading your REPL every time you change anything with a
`.java` suffix? Look no further.

Virgil is a library for live-recompiling Java classes from the REPL. This can be
done either manually or by starting a process that watches your source
directories for changes in Java files and triggering recompilation when that
happens.

### Usage

Add `virgil/virgil` dependency to your `project.clj` or `deps.edn`. If you plan
to use Virgil just as a devtime dependency, then you probably want to add it to
an alias/profile which you enable only during development.

[![](https://clojars.org/virgil/virgil/latest-version.svg)](https://clojars.org/virgil/virgil)

```clj
(require 'virgil)
;; To recompile once, manually:
(virgil/compile-java ["src"])

;; To recompile automatically when files change:
(virgil/watch-and-recompile ["src"])
```

The main argument to these functions is a list of directories where Java source
files are located.

After the Java classes are recompiled, it is often needed to refresh the Clojure
code that depends on them (see below the detailed explanation of the process).
To do that conveniently, you may use
[clj-reload](https://github.com/tonsky/clj-reload) or
[tools.namespace](https://github.com/clojure/tools.namespace) like this:

```clj
(require 'clj-reload.core)
;; For manual usage:
(do (virgil/compile-java ["src"])
    (clj-reload.core/reload {:only :all}))

;; For automatic usage:
(virgil/watch-and-recompile ["src"] :post-hook #(clj-reload.core/reload {:only :all}))
```

`compile-java` and `watch-and-recompile` have these optional arguments:

- `:options` - a list of strings that are passed to Java compiler, e.g.
`:options ["-Xlint:all"]` to print compilation warnings.
- `:verbose` - when true, print all classnames that were compiled.

Check [example](example) directory for a sample project.

Happy tarnishing.

### What happens when I recompile a class?

When Virgil is triggered, it compiles Java sources into classfiles (`.class`) on
the fly and loads them into the top-level Clojure's DynamicClassLoader. Here are
a few important details:

- **New class definitions are loaded alongside old ones.** Each recompilation
  loads a fresh copy of the class with the same fully qualified name. The
  previous version of the class remains in memory, though it is no longer
  resolvable by name.

- **Existing objects continue to reference their original class.** Any instances
  that were created before recompilation remain valid and keep behaving
  according to the old class definition. They do not automatically "upgrade" to
  the new class.

- **Fully qualified classnames automatically resolve to the newest version.**
  For example, after recompiling `foo/bar/Acme.java`, any code like:

  ```clj
  (foo.bar.Acme.)
  ```

  will construct an instance of the new class definition.

- **Imported names stick to the old class until refreshed.** Say you have this:

  ```clj
  (ns my.app
    (:import foo.bar.Acme))

  (defn make-acme []
    (Acme.))
  ```

  After Virgil recompilation, `make-acme` will continue referencing the **old**
  version of the class. Even recompiling `make-acme` will not make it "see" the
  new version. You must recompile the `ns` form first to "re-import" the newest
  version of the class into the namespace, and then recompile `make-acme`. In an
  editor that supports reloading whole files (e.g. `C-c C-k` in Emacs),
  performing that is usually enough to ensure that the newest class is picked
  up. Otherwise, you can couple Virgil with tools like
  [clj-reload](https://github.com/tonsky/clj-reload) to guarantee that all code
  across the project is updated with the latest compiled versions.

### Can I use Virgil in production?

Virgil can compile Java classes at runtime in a production environment the same
way as it does during the development, so the answer is yes. However, when you
do a release build, it is advised to build real Java classes explicitly during
your build step using *javac* task of your build tool. There are multiple
arguments for it:
  * You get extra reliability and assurance that the compiled Java classes will
    be correctly discoverable by other code.
  * You get one fewer runtime dependency.
  * You won't have to rely on JDK-specific tools like `javax.tools` package that
    might not be available in your production environment (e.g., if it runs on
    JRE).

### Migration from 0.1.9

Since version 0.3.0, Virgil no longer provides `lein-virgil` plugin for
Leiningen. Instead, you should add `virgil` as a regular dependency to your
project and call its functions from the REPL.

### Supported versions

Virgil makes sure to support Clojure 1.10+ and JDK 8, 11, 17, 21, 25 (see [CI
job](https://app.circleci.com/pipelines/github/clj-commons/virgil)). Supporting
future versions of Java so far required only bumping ASM library dependency, so
that shouldn't take long. Please, create an issue if you run into any
compatibility problems.

### Publishing new releases

Releases are handled by CircleCI. All you need to do is to tag a commit with a
`x.y.z` and push the tag.

### License

Copyright © 2016-2019 Zachary Tellman, 2022-2025 Oleksandr Yakushev

Distributed under the MIT License
