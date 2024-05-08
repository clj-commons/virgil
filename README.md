<img src="docs/cocytus.jpg" align="right" width="400"/>

Do you tarnish your Clojure with the occasional hint of Java? Have you become
indescribably tired of reloading your REPL every time you change anything with a
`.java` suffix? Look no further.

Virgil is a library for live-recompiling Java classes from the REPL. This can be
done either manually or by starting a process that watches your source
directories for changes in Java files and triggering recompilation when that
happens.

### Usage

Add `com.clojure-goes-fast/virgil` dependency to your `project.clj` or
`deps.edn`. Since this is a devtime dependency, you probably want to a
profile/alias that you enable only during development.

[![](https://clojars.org/com.clojure-goes-fast/virgil/latest-version.svg)](https://clojars.org/com.clojure-goes-fast/virgil)

```clj
(require 'virgil)
;; To recompile once, manually:
(virgil/compile-java ["src"])

;; To recompile automatically when files change:
(virgil/watch-and-recompile ["src"])
```

The main argument to these functions is a list of directories where Java source
files are located. Both functions can accept a list of string `:options` that is
passed to Java compiler, e.g. `:options ["-Xlint:all"]` to print compilation
warnings, and a `:verbose` flag to print all classnames that got compiled.

`watch-and-recompile` accepts an optional `:post-hook` function. You can use it
to, e.g., trigger `tools.namespace` refresh after the classes get recompiled.

Check [example](example) directory for a sample project.

Happy tarnishing.

### Should I use Virgil in production?

Even though it is possible to dynamically compile Java classes with Virgil in
the production builds of your project, it is not advised. Virgil is primarily a
dev-time tool. For the release, it is preferable to use *javac* task of your
build tool to generate real `.class` files that you will later pack into the JAR
or put onto the classpath in some other way.

### Migration from 0.1.9

From version 0.3.0, Virgil no longer provides `lein-virgil` plugin for
Leiningen. Instead, you should add `virgil` it as a regular dependency to your
project (but preferably only during the development) and call its functions from
the REPL.

### Supported versions

Virgil makes sure to support Clojure 1.10+ and JDK 8, 11, 17, 21, 22 (see [CI
job](https://app.circleci.com/pipelines/github/clojure-goes-fast/virgil).
Supporting future versions of Java so far required only bumping ASM library
dependency, so that shouldn't take long. Please, create an issue if you run into
any compatibility problems.

### License

Copyright © 2016-2019 Zachary Tellman, 2022-2024 Oleksandr Yakushev

Distributed under the MIT License
