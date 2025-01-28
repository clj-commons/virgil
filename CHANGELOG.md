# Changelog

### 0.3.2 (2025-01-28)

- [#40](https://github.com/clj-commons/virgil/pull/40): Don't throw exception on
  warnings.
- [#40](https://github.com/clj-commons/virgil/pull/40): Don't let compilation
  errors crash the watch-and-recompile loop.

### 0.3.1 (2024-11-11)

- [#39](https://github.com/clj-commons/virgil/pull/39): Throw exception if
  compilation failed.

### 0.3.0 (2024-05-08)

The first version published under clj-commons.

- Drop `lein-virgil` plugin.
- Bump ASM to support latest JDK and Clojure versions.
- Rework public API functions.
- Remove dependency on `tools.namespace`. Users wishing to reload namespaces
  after Java code changes can pass a custom hook to
  `virgil/watch-and-recompile`.

### 0.1.9

The final version published by [Zach Tellman](https://github.com/ztellman). Last
version to contain `lein-virgil` plugin.
