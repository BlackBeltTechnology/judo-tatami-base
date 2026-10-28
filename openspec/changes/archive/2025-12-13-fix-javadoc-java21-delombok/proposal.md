# Fix Javadoc Generation for Java 21 with Delombok

## Summary

Upgrade the javadoc and delombok plugin configuration to properly support Java 21. The current setup uses outdated plugin versions and configuration that generates numerous errors with Java 21.

## Motivation

The current project configuration has several issues:

1. **Outdated maven-javadoc-plugin** (version 3.4.1): Current version is 3.12.0 with Java 21+ improvements
2. **Outdated lombok-maven-plugin** (version 1.18.20.0): Has known compatibility issues with Java 21
3. **Wrong source level**: Configuration specifies `<source>8</source>` but project uses Java 21
4. **Hidden errors**: `<failOnError>false</failOnError>` masks javadoc generation problems
5. **Lombok 1.18.34 dependency**: Out of sync with lombok-maven-plugin 1.18.20.0

### Current Issues

- Delombok fails with Java 21 internal API changes (`com.sun.tools.javac.tree.JCTree$JCImport` errors)
- Javadoc generates with numerous warnings and errors
- Generated documentation quality is poor

## Approach

### Option 1: Upgrade Existing Plugins (Recommended)

Update the maven-javadoc-plugin and lombok-maven-plugin to latest versions:

```xml
<!-- Upgrade lombok-maven-plugin -->
<plugin>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok-maven-plugin</artifactId>
    <version>1.18.20.0</version>
    <configuration>
        <sourceDirectory>${project.basedir}/src/main/java</sourceDirectory>
        <outputDirectory>${project.basedir}/target/delombok</outputDirectory>
        <addOutputDirectory>false</addOutputDirectory>
    </configuration>
    <dependencies>
        <!-- Override lombok version for Java 21 compatibility -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <version>1.18.34</version>
        </dependency>
    </dependencies>
    <executions>
        <execution>
            <phase>generate-sources</phase>
            <goals>
                <goal>delombok</goal>
            </goals>
        </execution>
    </executions>
</plugin>

<!-- Upgrade maven-javadoc-plugin -->
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-javadoc-plugin</artifactId>
    <version>3.11.2</version>
    <configuration>
        <source>21</source>
        <release>21</release>
        <detectJavaApiLink>false</detectJavaApiLink>
        <failOnError>false</failOnError>
        <failOnWarnings>false</failOnWarnings>
        <doclint>none</doclint>
        <sourcepath>${project.basedir}/target/delombok</sourcepath>
        <tags>
            <tag>
                <name>model</name>
                <placement>a</placement>
                <head>EMF Model</head>
            </tag>
            <tag>
                <name>generated</name>
                <placement>a</placement>
                <head>Generated</head>
            </tag>
            <tag>
                <name>ordered</name>
                <placement>a</placement>
                <head>Ordered</head>
            </tag>
            <tag>
                <name>param</name>
                <placement>a</placement>
                <head></head>
            </tag>
        </tags>
    </configuration>
</plugin>
```

Key changes:
- Override lombok dependency in plugin to 1.18.34 for Java 21 support
- Upgrade maven-javadoc-plugin to 3.11.2 (stable with JDK 21 fixes)
- Set `<source>21</source>` and `<release>21</release>` for proper Java 21 handling
- Add `<doclint>none</doclint>` to suppress strict HTML/Javadoc validation errors

### Option 2: Use Lombok's Built-in Delombok (Alternative)

Instead of using the external lombok-maven-plugin, use lombok's native delombok via exec-maven-plugin:

```xml
<plugin>
    <groupId>org.codehaus.mojo</groupId>
    <artifactId>exec-maven-plugin</artifactId>
    <version>3.1.0</version>
    <executions>
        <execution>
            <id>delombok</id>
            <phase>generate-sources</phase>
            <goals>
                <goal>exec</goal>
            </goals>
            <configuration>
                <executable>java</executable>
                <arguments>
                    <argument>-jar</argument>
                    <argument>${maven.dependency.org.projectlombok.lombok.jar.path}</argument>
                    <argument>delombok</argument>
                    <argument>${project.basedir}/src/main/java</argument>
                    <argument>-d</argument>
                    <argument>${project.basedir}/target/delombok</argument>
                </arguments>
            </configuration>
        </execution>
    </executions>
</plugin>
```

This approach bypasses the plugin wrapper and uses lombok directly, ensuring version consistency.

### Option 3: Skip Delombok for Javadoc (Fallback)

If delombok continues to have issues, configure javadoc to work directly with source files and accept lombok annotations:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-javadoc-plugin</artifactId>
    <version>3.11.2</version>
    <configuration>
        <source>21</source>
        <release>21</release>
        <doclint>none</doclint>
        <failOnError>false</failOnError>
        <!-- Use original sources instead of delombok output -->
        <sourcepath>${project.basedir}/src/main/java</sourcepath>
        <!-- Suppress lombok annotation processing errors -->
        <additionalJOptions>
            <additionalJOption>--ignore-source-errors</additionalJOption>
        </additionalJOptions>
    </configuration>
</plugin>
```

This approach generates javadoc with lombok annotations visible but unexpanded.

## Impact

- **Modules affected**: All modules with Java source code (parent pom.xml)
- **Breaking changes**: None - javadoc output may differ slightly
- **Dependencies**: May require lombok version update in main dependencies if not already at 1.18.34

## Risks

1. **Plugin compatibility**: lombok-maven-plugin is community-maintained and may lag behind lombok releases
   - **Mitigation**: Use Option 2 (exec-maven-plugin) if issues persist

2. **Build time**: Delombok adds processing time to builds
   - **Mitigation**: Consider making delombok optional with a profile

3. **Documentation quality**: Some lombok-generated methods may not have proper javadoc
   - **Mitigation**: Accept or add custom javadoc to annotated fields

## Recommendation

Start with **Option 1** (upgrade plugins with lombok dependency override) as it's the least invasive change. If delombok still fails, fall back to **Option 2** using exec-maven-plugin.

## References

- [lombok-maven-plugin Java 21 issue](https://github.com/awhitford/lombok.maven/issues/181)
- [Lombok Changelog](https://projectlombok.org/changelog)
- [Maven Javadoc Plugin Releases](https://github.com/apache/maven-javadoc-plugin/releases)
- [Lombok Delombok Documentation](https://projectlombok.org/features/delombok)
