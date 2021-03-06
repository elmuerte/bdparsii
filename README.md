[![Maven Central](https://img.shields.io/maven-central/v/com.mpobjects/bdparsii.svg)](https://search.maven.org/search?q=g:com.mpobjects%20AND%20a:bdparsii)
[![Javadocs](https://www.javadoc.io/badge/com.mpobjects/bdparsii.svg)](https://www.javadoc.io/doc/com.mpobjects/bdparsii)
[![Vulnerability Scan](https://snyk.io/test/github/mpobjects/bdparsii/badge.svg)](https://snyk.io/test/github/mpobjects/bdparsii)
[![Build Status](https://travis-ci.org/mpobjects/bdparsii.svg?branch=master)](https://travis-ci.org/mpobjects/bdparsii)
[![Coverage Status](https://coveralls.io/repos/github/mpobjects/bdparsii/badge.svg?branch=master)](https://coveralls.io/github/mpobjects/bdparsii?branch=master)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=com.mpobjects%3Abdparsii&metric=alert_status)](https://sonarcloud.io/dashboard?id=com.mpobjects%3Abdparsii)
[![Sonatype Nexus (Snapshots)](https://img.shields.io/nexus/s/https/oss.sonatype.org/com.mpobjects/bdparsii.svg)](https://oss.sonatype.org/content/repositories/snapshots/com/mpobjects/bdparsii/)

# bdparsii 

A BigDecimal port of [Scireum's Parsii](https://github.com/scireum/parsii) library. 

Using it is as simple as:

```java
Scope scope = Scope.create();   
Variable a = scope.getVariable("a");   
Expression expr = Parser.parse("3 + a * 4", scope);   
a.setValue(4);   
System.out.println(expr.evaluate());   
a.setValue(5);   
System.out.println(expr.evaluate());
```

## MathContext

An important part of calculations with BigDecimals is the [MathContext](https://docs.oracle.com/javase/8/docs/api/java/math/MathContext.html). By default `DECIMAL64` is used, which is similar to the `double` precision.

The MathContext can be set on the `Scope` instance. It can also be passed on in the `evaluate` method of expression. But this will not affect parts of the expression which were simplified to constants.

Complex mathematical functions are executed by the [Big Math library](https://github.com/eobermuhlner/big-math). Most of these functions do not support unlimited precision (as there simply is no end). In this case a fallback MathContext is used, which is by default `DECIMAL128`.

## Performance

Due to calculation on arbitrary-precision decimals the performance of calculations is significantly worse than the calculations performed by the double based parsii.
Performance depends a lot on the used precision and used mathematical functions.

For more information see [the performance test suite](src/test/perftest/README.md).

## Download

bdparsii is available from the [maven central repository](https://search.maven.org/search?q=g:com.mpobjects%20AND%20a:bdparsii):

```xml
<dependency>
	<groupId>com.mpobjects</groupId>
	<artifactId>bdparsii</artifactId>
	<version>1.0.0</version>
</dependency>
```
