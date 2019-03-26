guardrail-maven-plugin
======================

A maven plugin for adding clients and servers generated by [guardrail](https://github.com/twilio/guardrail) to your service.

Usage
-----

Add to your `pom.xml`:
```
<build>
  <plugins>
    ...
    <plugin>
      <groupId>com.twilio</groupId>
      <artifactId>guardrail-maven-plugin_2.12</artifactId>
      <version>Please use the latest available release!</version>
      <executions>
        <execution>
          <id>generate-petstore-client</id>
          <goals>
            <goal>generate-sources</goal>
          </goals>
          <configuration>
            <language>scala</language>
            <specPath>${project.basedir}/src/main/swagger/example-client.yaml</specPath>
            <packageName>com.example.client</packageName>
          </configuration>
        </execution>
      </executions>
    </plugin>
    ...
  </plugins>
</build>
```

## Configuration

To generate multiple clients, specify multiple `<execution>` sections.

| Parameter Name | Description |
|:---------------|:------------|
| outputPath | Location of generated classes (defaults to `${project.build.directory}/generated-sources/swagger-clients`) |
| language | Which language to generate (defaults to `java`. Valid values are: `java`, `scala`) |
| kind | What kind of code should be generated (defaults to `client`. Valid values are: `client`, `server`, `models`) |
| specPath | Location of the swagger specification file |
| packageName | Package name to use for the generated classes |
| dtoPackage | Package name for the data transfer objects (defaults to same as `packageName`) |
| tracing | Whether or not to generate clients that accept a `TracingContext` which will send tracing headers to the remote service (defaults to `false`) |
| customImports | A list of `<customImport>`s that will be added to the top of all generated files. Useful for providing additional typeclass instances or domain-specific types |
| framework | The framework to generate the code for (defaults to `akka-http`) |
