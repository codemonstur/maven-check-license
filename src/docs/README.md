# maven-check-license

A Maven plugin that checks the licenses of your project's dependencies against a
whitelist or blacklist, so a non-compliant license can never slip into a release
unnoticed.

## Usage

```xml
<plugin>
  <groupId>com.github.codemonstur</groupId>
  <artifactId>maven-check-license</artifactId>
  <version>1.3.0</version>
  <executions>
    <execution>
      <goals>
        <goal>check</goal>
      </goals>
    </execution>
  </executions>
</plugin>
```

The goal binds to the `validate` phase.

## Strategies

Two mutually exclusive rule sets:

- **Whitelist** (`passOnMatch`) — only dependencies matching a rule pass; everything
  else violates.
- **Blacklist** (`failOnMatch`) — everything passes unless it matches a rule.

## Rule format

```
field:type:value
```

| Field | Meaning |
| --- | --- |
| `name` | The license name. |
| `url` | The license URL. |

| Type | Meaning |
| --- | --- |
| `equal` | Exact full-string match. |
| `regex` | Regular expression, matched against the **entire** value. |

Example:

```xml
<rules>
  <rule>name:regex:(Apache License.*2\.0|MIT License)</rule>
  <rule>url:equal:https://opensource.org/licenses/MIT</rule>
</rules>
```

## Artifact patterns

Dependencies are matched by `groupId:artifactId[:version]`, with `*` and `?`
wildcards plus Maven version ranges:

```xml
<exclusions>
  <exclusion>org.example:legacy-lib:[1.0,2.0)</exclusion>
</exclusions>
```

### Exclusions vs ignored

- **Exclusions** — the dependency is not checked at all (its license is irrelevant
  to you).
- **Ignored** — the dependency is checked but a known violation is tolerated.

Both can be declared inline, loaded from files (`exclusionsFile`, `ignoredFile`),
or passed on the command line:

```sh
mvn validate -Dchecklicense.exclusions=org.example:lib -Dchecklicense.ignored=org.other:lib
```

(comma-separated, and range-safe.)

## Behaviour notes

- A **stale pattern** warning names the source of the pattern so you can update it.
- Dependencies with an **unknown license always violate** — fail-closed by design.

## Key classes

`checklicense.MavenCheckLicense` (the mojo) plus the model types `Rule`,
`RuleStrategy`, `MatchField`, `MatchType`, `GavPattern`, `Violation`, `Ignored`,
and `Compliant`.

## Building

```sh
make build
make test
```

## License

MIT
