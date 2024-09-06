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
a profile/alias which you enable only during development.

[![](https://clojars.org/virgil/virgil/latest-version.svg)](https://clojars.org/virgil/virgil)

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

From version 0.3.0, Virgil no longer provides `lein-virgil` plugin for
Leiningen. Instead, you should add `virgil` as a regular dependency to your
project and call its functions from the REPL.

### Supported versions

Virgil makes sure to support Clojure 1.10+ and JDK 8, 11, 17, 21, 22 (see [CI
job](https://app.circleci.com/pipelines/github/clj-commons/virgil)). Supporting
future versions of Java so far required only bumping ASM library dependency, so
that shouldn't take long. Please, create an issue if you run into any
compatibility problems.

### License

Copyright © 2016-2019 Zachary Tellman, 2022-2024 Oleksandr Yakushev

Distributed under the MIT License
