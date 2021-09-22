[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.cdptech/cdpclient/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.cdptech/cdpclient)


CDP Java Client
===============

### Project description

A simple Java interface for the CDP Studio development platform that allows Java applications to interact with
CDP Applications - retrieve CDP Application structures and read-write object values. For more information
about CDP Studio see https://cdpstudio.com/.

### Usage

The library is available at Maven (https://search.maven.org/artifact/com.cdptech/cdpclient/) and the API is
described in the javadoc (https://www.javadoc.io/doc/com.cdptech/cdpclient/).


### Dependencies

* [Maven](https://maven.apache.org/) - Downloads necessary dependencies and builds the library.
* [Project Lombok](https://projectlombok.org/) plugin - Install it to your IDE when developing this library
  or the auto-complete will not find some generated getters-setters.

#### Logging

The underlying websocket library uses [SLF4J](https://www.slf4j.org/) for logging and does not ship with any
default logging implementation.

Feel free to use whichever logging framework you desire. For example:

    <dependency>
      <groupId>org.slf4j</groupId>
      <artifactId>slf4j-simple</artifactId>
      <version>1.7.25</version>
    </dependency>

### Contact

Email: support@cdptech.com
