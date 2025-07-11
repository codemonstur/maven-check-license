
[![GitHub Release](https://img.shields.io/github/release/codemonstur/maven-check-license.svg)](https://github.com/codemonstur/maven-check-license/releases)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.codemonstur/maven-check-license/badge.svg)](http://mvnrepository.com/artifact/com.github.codemonstur/maven-check-license)
[![MIT Licence](https://badges.frapsoft.com/os/mit/mit.svg?v=103)](https://opensource.org/licenses/mit-license.php)

## Maven-check-license

A maven plugin that checks which licenses are used by project dependencies.

By default, the plugin will fail the build for any license that is not matched by any configured rule.
The purpose of the plugin is to prevent accidentally agreeing to licenses that are unacceptable.
For example, you may not want to include GPL licensed code.

In addition to the list of rules there are also two other lists that can be configured: exclusions, and ignored.
The exclusions list will remove any listed GAV from consideration, any matching artifact will not be checked at all.
The ignored list will move any violation to a separate 'Ignored' list. They will be reported but not be counted as 
violations.

The plugin will run during the `validate` phase.

### Example pom configuration

1. Add this code to the pom:
```
<plugin>
    <groupId>com.github.codemonstur</groupId>
    <artifactId>maven-check-license</artifactId>
    <version>1.1.0</version>
    <executions>
        <execution><goals><goal>check</goal></goals></execution>
    </executions>
    <configuration>
        <enabled>true</enabled> <!-- default: true -->
        <printViolations>true</printViolations> <!-- default: true -->
        <printIgnored>true</printIgnored> <!-- default: true -->
        <printCompliant>false</printCompliant> <!-- default: false -->
        <failBuildOnViolation>true</failBuildOnViolation> <!-- default: true -->
        <checkCodeDependencies>true</checkCodeDependencies> <!-- default: true -->
        <checkPluginDependencies>false</checkPluginDependencies> <!-- default: false -->
        <includeCompileDependencies>true</includeCompileDependencies> <!-- default: true -->
        <includeRuntimeDependencies>true</includeRuntimeDependencies> <!-- default: true -->
        <includeProvidedDependencies>false</includeProvidedDependencies> <!-- default: false -->
        <includeTestDependencies>false</includeTestDependencies> <!-- default: false -->
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
        </exclusions>
        
        <ignored> <!-- default: empty list -->
            <ignore>groupId:artifactId:version</ignore>
        </ignored>
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
| checkCodeDependencies       | true          | If true will include all code dependencies in the pom, including transitive dependencies                         |
| checkPluginDependencies     | false         | If true will include all plugin dependencies in the pom                                                          |
| includeCompileDependencies  | true          | If true will include all dependencies with the compile scope                                                     |
| includeRuntimeDependencies  | true          | If true will include all dependencies with the runtime scope                                                     |
| includeProvidedDependencies | false         | If true will include all dependencies with the provided scope                                                    |
| includeTestDependencies     | false         | If true will include all dependencies with the test scope                                                        |
| strategy                    | passOnMatch   | passOnMatch makes the rules behave like a whitelist, failOnMatch makes the rules behave like a blacklist         |
| rules                       | []            | The list of rules. Use the tag `<rule>` and follow the rule format below                                         |
| exclusions                  | []            | The list of excluded artifacts. Use the tag `<exclude>` and write the GAV separated by colons                    |
| ignored                     | []            | The list of ignored artifacts. Use the tag `<ignore>` and write the GAV separated by colons                      |

### Rule format

A rule in the configuration looks like this:

    <field>:<type>:<value>

The field can be one of `name` or `url`, which will make the plugin compare either the name or url field of the license.

The type can be one of `equal` or `regex`, which will make the plugin either compare for a direct equality match or a regular expression match.

The value is either the string to match with or the regular expression to match with depending on what was chosen for the type.
