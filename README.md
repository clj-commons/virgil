![](docs/cocytus.jpg)

Do you tarnish your Clojure with the occasional hint of Java?  Have you become indescribably tired of reloading your REPL every time you change anything with a `.java` suffix?  Look no further.

In your `project.clj` or `~/.lein/profiles.clj`, add this:

```clj
{:plugins [[lein-virgil "0.1.9"]]}
```

Now, as if by magic, every time the `.java` files on your `:java-source-paths` change, they will be recompiled within your REPL and all the namespaces that rely on those files will be reloaded.  A helpful message will be printed out, including any compilation errors you may have introduced along the way (these are written to `stdout`, so they may show up in a different buffer than your REPL).

Happy tarnishing.

### Boot

For [Boot](http://boot-clj.com/), add this to your `build.boot`:

```clj
(set-env! :dependencies '[[virgil "0.1.9"]
                          ...])

(require '[virgil.boot :refer [javac*]])
```

Now you have the option to run Virgil manually from the REPL. Virgil will
automatically scan your `:source-paths` for Java files. This will compile Java
classes once:

```clj
boot.user=> (boot (javac*))
```

Or you can enable automatic background recompilation like in lein-virgil:

```clj
boot.user=> (def f (future (boot (comp (watch) (javac*)))))
;; Then, to disable:
boot.user=> (future-cancel f)
```

In your build pipelines, you can continue to use the Boot's built-in `javac`
task.

`javac*` task supports the following parameters:

- `:verbose` — print every class name that is compiled.
- `:options` — a vector of strings that is passed to Java compiler, same as to
  `javac` binary. E.g., you can use `(javac* :options ["-Xlint:unchecked"])` to
  print additional warnings from the compiler.

### license

Copyright © 2016 Zachary Tellman

Distributed under the MIT License
