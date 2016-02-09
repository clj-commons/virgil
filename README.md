![](docs/underworld.jpg)

Do you tarnish your Clojure with the occasional hint of Java?  Have you become indescribably tired of reloading your REPL every time you change anything with a `.java` suffix?  Look no further.

In your `project.clj` or `~/.lein/profiles.clj`, add this:

```clj
{:plugins [[lein-virgil "0.1.0"]]}
```

Now, as if by magic, every time the `.java` files on your `:java-source-paths` change, they will be recompiled within your REPL and all the namespaces that rely on those files will be reloaded.  A helpful message will be printed out, including any compilation errors you may have introduced along the way (these are written to `stdout`, so they may show up in a different buffer than your REPL).

Happy tarnishing.

### license

Copyright © 2015 Zachary Tellman

Distributed under the MIT License
