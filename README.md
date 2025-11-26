This is a companion repository for https://github.com/eclipse-jdtls/eclipse.jdt.javac .
This current repository is a fork of https://github.com/eclipse-jdt/eclipse.jdt.core which hosts
in the `dom-with-javac` some modification to test cases in order to make them compatible with Javac
backend.

This repository is only meant to host the tweaks for the tests to pass with Javac backend.

It is not meant to:
* publish any artifact: https://github.com/eclipse-jdtls/eclipse.jdt.javac is where artifact development
and publication is maintained
* host parallel development of JDT-Core (beyond exceptional testing): changes to other bits of
JDT-Core should be pushed upstream
