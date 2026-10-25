
[![GitHub Release](https://img.shields.io/github/v/release/codemonstur/maven-check-license.svg)](https://github.com/codemonstur/maven-check-license/releases)
[![Maven Central](https://img.shields.io/maven-central/v/com.github.codemonstur/maven-check-license.svg)](https://central.sonatype.com/artifact/com.github.codemonstur/maven-check-license)
[![MIT Licence](https://img.shields.io/github/license/codemonstur/maven-check-license.svg)](LICENSE)

## Maven-check-license

A maven plugin that checks which licenses are used by project dependencies.

By default, the plugin will fail the build for any license that is not matched by any configured rule.
The purpose of the plugin is to prevent accidentally agreeing to licenses that are unacceptable.
For example, you may not want to include GPL licensed code.

In addition to the list of rules there are also two other lists that can be configured: exclusions, and ignored.
The exclusions list will remove any matching artifact from consideration, any matching artifact will not be checked
at all.
The ignored list will move any violation to a separate 'Ignored' list. They will be reported but not be counted as 
violations.
Both lists accept artifact patterns, see the pattern format below.

Dependencies without any license information are always reported as a violation, for both strategies: an unknown
license can never be proven acceptable. That includes poms without a `<licenses>` section, and poms that cannot be
read at all (a system scoped dependency for example). A pom that cannot be read is logged as a warning and treated
as having no license information.

The plugin will run during the `validate` phase.

### Example pom configuration

1. Add this code to the pom:
```
<plugin>
    <groupId>com.github.codemonstur</groupId>
    <artifactId>maven-check-license</artifactId>
    <version>1.3.0</version>
    <executions>
        <execution><goals><goal>check</goal></goals></execution>
    </executions>
    <configuration>
        <enabled>true</enabled> <!-- default: true -->
        <printViolations>true</printViolations> <!-- default: true -->
        <printIgnored>true</printIgnored> <!-- default: true -->
        <printCompliant>false</printCompliant> <!-- default: false -->
        <failBuildOnViolation>true</failBuildOnViolation> <!-- default: true -->
        <warnUnmatchedRules>true</warnUnmatchedRules> <!-- default: true -->
        <checkCodeDependencies>true</checkCodeDependencies> <!-- default: true -->
        <checkPluginDependencies>false</checkPluginDependencies> <!-- default: false -->
        <includeCompileDependencies>true</includeCompileDependencies> <!-- default: true -->
        <includeRuntimeDependencies>true</includeRuntimeDependencies> <!-- default: true -->
        <includeProvidedDependencies>false</includeProvidedDependencies> <!-- default: false -->
        <includeTestDependencies>false</includeTestDependencies> <!-- default: false -->
        <includeSystemDependencies>false</includeSystemDependencies> <!-- default: false -->
        <strategy>passOnMatch</strategy> <!-- default: passOnMatch, values: [ passOnMatch, failOnMatch ] -->

        <rules> <!-- default: empty list -->
            <rule>url:equal:http://www.opensource.org/licenses/bsd-license.php</rule>
            <rule>url:equal:http://opensource.org/licenses/BSD-3-Clause</rule>
            <rule>url:regex:(http|https)://(www.)?opensource.org/licenses/(MIT|mit-license.php)</rule>
            <rule>name:equal:The MIT License</rule>
            <rule>name:regex:(The )?Apache(\s|-)(Software )?(License |License, )?(Version |version )?2\.0</rule>
        </rules>
        
        <exclusions> <!-- default: empty list -->
            <exclude>groupId:artifactId:version</exclude>
            <exclude>org.example.*:*</exclude> <!-- any artifact in the org.example namespace -->
        </exclusions>
        
        <ignored> <!-- default: empty list -->
            <ignore>groupId:artifactId:version</ignore>
            <ignore>org.apache.commons:commons-lang3:[3.0,4.0)</ignore> <!-- any 3.x release -->
        </ignored>

        <!-- optional, files with one pattern per line, '#' starts a comment -->
        <exclusionsFile>${project.basedir}/config/license-exclusions.txt</exclusionsFile>
        <ignoredFile>${project.basedir}/config/license-ignores.txt</ignoredFile>
    </configuration>
</plugin>
```
2. Run `mvn validate`

### Configuration settings

The following settings can be used for the plugin:

| config name                 | default value | description                                                                                                      |
|-----------------------------|---------------|------------------------------------------------------------------------------------------------------------------|
| enabled                     | true          | Turns the plugin on or off                                                                                       |
| printViolations             | true          | If true will print a warning in the log for each dependency that failed the rules                                |
| printIgnored                | true          | If true will print a warning in the log for each dependency that failed the rules but was ignored as a violation |
| printCompliant              | false         | If true will print an info message for each dependency that passed the rules                                     |
| failBuildOnViolation        | true          | If true will cause the build to fail if any dependency violates the rules                                        |
| warnUnmatchedRules          | true          | If true will print a warning in the log for each rule that did not match any artifact                            |
| checkCodeDependencies       | true          | If true will include all code dependencies in the pom, including transitive dependencies                         |
| checkPluginDependencies     | false         | If true will include all plugin dependencies in the pom                                                          |
| includeCompileDependencies  | true          | If true will include all dependencies with the compile scope                                                     |
| includeRuntimeDependencies  | true          | If true will include all dependencies with the runtime scope                                                     |
| includeProvidedDependencies | false         | If true will include all dependencies with the provided scope                                                    |
| includeTestDependencies     | false         | If true will include all dependencies with the test scope                                                        |
| includeSystemDependencies   | false         | If true will include all dependencies with the system scope                                                      |
| strategy                    | passOnMatch   | passOnMatch makes the rules behave like a whitelist, failOnMatch makes the rules behave like a blacklist         |
| rules                       | []            | The list of rules. Use the tag `<rule>` and follow the rule format below                                         |
| exclusions                  | []            | The list of excluded artifacts. Use the tag `<exclude>` and write a pattern, see the pattern format below       |
| ignored                     | []            | The list of ignored artifacts. Use the tag `<ignore>` and write a pattern, see the pattern format below         |
| exclusionsFile              | none          | Optional file with one exclusion pattern per line, '#' starts a comment                                         |
| ignoredFile                 | none          | Optional file with one ignored pattern per line, '#' starts a comment                                           |

### Rule format

A rule in the configuration looks like this:

    <field>:<type>:<value>

The field can be one of `name` or `url`, which will make the plugin compare either the name or url field of the license.

The type can be one of `equal` or `regex`, which will make the plugin either compare for a direct equality match or a regular expression match.

The value is either the string to match with or the regular expression to match with depending on what was chosen for the type.
A regex must match the whole license name or url, not just a part of it: `name:regex:Apache` does not match
`The Apache Software License, Version 2.0`, you need something like `name:regex:.*Apache.*` for that.

### Pattern format

The `exclusions` and `ignored` lists (and the optional files) use artifact patterns:

    <groupId>:<artifactId>[:<version>]

Every field supports the wildcards `*` (any number of characters) and `?` (a single character), and the version
field additionally supports maven version ranges. Omitting the version, or using `*`, matches any version.

| pattern                                        | matches                                                                    |
|------------------------------------------------|----------------------------------------------------------------------------|
| `com.google.guava:guava:30.1-jre`              | exactly that artifact and version                                          |
| `com.google.guava:guava`                       | any version of that artifact                                               |
| `com.google.guava:*`                           | any artifact of that group, any version                                    |
| `org.slf4j:*:1.7.*`                            | the 1.7.x releases of any slf4j artifact                                   |
| `com.google.*:*:*`                             | everything in the com.google namespace                                     |
| `org.apache.commons:commons-lang3:[3.0,4.0)`   | the 3.x releases (maven version range, `[3.0,)` and `1.0+` also work)      |

A pattern that matches nothing is reported as a warning, so that stale entries are noticed after a version bump.
The same warning is printed for an ignored pattern that never suppressed a violation. The warnings are skipped
when no artifacts were examined at all, for example when both `checkCodeDependencies` and
`checkPluginDependencies` are false. Ignored violations name the pattern that matched them:

    [WARNING] Ignored License violation: com.example:demo:1.2 (matched 'com.example.*:*' from the configuration)

Patterns can also come from two other places, next to the pom configuration:

* a file, with `<exclusionsFile>` and `<ignoredFile>`: one pattern per line, blank lines and lines starting with
  `#` are skipped. Useful for a list that is shared between projects.
* the command line, with `-Dchecklicense.exclusions=...` and `-Dchecklicense.ignored=...`: a comma separated list
  of patterns that is added to the configured ones, for example to ignore a dependency during an investigation
  without editing the pom. Commas inside a version range do not split the list, so
  `-Dchecklicense.ignored=org.apache.commons:commons-lang3:[3.0,4.0),com.example:demo:*` works as expected.

The source of each pattern is printed with the warnings above: `the configuration`, the path of the file it came
from, or the name of the system property for command line patterns (`'checklicense.exclusions'` or
`'checklicense.ignored'`).
