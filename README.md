# pants-dependency-conflict

This is an example project to demonstrate how to discover hidden transitive dependencies that may override your explicit dependencies and how to find them. A [blog post is available](https://systemsdesign.tech/pants-build-transitive-dependency-conflicts-finding-and-fixing) on this topic.

## Pants Setup

1. Clone the repo.
2. Pants requires python 3+, so if you don't already have it, I recommend installing [pyenv](https://github.com/pyenv/pyenv) (or the (windows version)[https://github.com/pyenv-win/pyenv-win] if you aren't using the linux subsystem).
3. Run `./pants list ::` to let pants install itself.

## Java 1.8 Required

You will also need Java 1.8 for this to run. If you are on Java 9 or beyond you will run into that pesky `ClassCastException`:
```
Exception in thread "main" java.lang.ClassCastException: class jdk.internal.loader.ClassLoaders$AppClassLoader cannot be cast to class java.net.URLClassLoader (jdk.internal.loader.ClassLoaders$AppClassLoader and java.net.URLClassLoader are in module java.base of loader 'bootstrap')
```

# Part 1 - Starting with a Single Dependency

This example repo is initially setup with a single dependency on
`org.bouncycastle:bcprov-jdk15on:1.64`, as seen in [example/src/main/scala/BUILD].
The others are currently commented out.
The code under [examples/src/main/scala/tech/systemsdesign/pants/AppMain.scala] simply
attempts to add BouncyCastle as a security provider and then get a `javax.crypto.Mac` instance,
specifically with the `Hmac-Sha3-512` algorithm.

We can run and see this is so, while also printing out the version of BouncyCastle at the same
time:

```shell
$ ./pants -q run example/src/main/scala:bin
BC version 1.64
Requesting algorithm Hmac-Sha3-512
$
```

Success! We are running expected BouncyCastle 1.64 and requesting the algorithm works great.

# Part 2 - Adding a Dependency with a Conflicting Transitive Library

We've decided in our project that we want to generate PDFs for users. To do that, we use
the available library `org.xhtmlrenderer:flying-saucer-pdf:9.0.8`. Why 9.0.8 from 2015? Because
it's old and this is supposed to be an example.

Let's add that dependency to the (BUILD)[example/src/main/scala/BUILD] file
(which you should simply be able to uncomment the line):

```python
scala_library(
  sources = ["**/*.scala"],
  dependencies = [
    "3rdparty/org/xhtmlrenderer:flying-saucer-pdf",
    "3rdparty/org/bouncycastle:bcprov-jdk15on",
  ]
)
```

And now let's re-run our application:

```shell
BC version 1.38
Requesting algorithm Hmac-Sha3-512
Exception in thread "main" java.security.NoSuchAlgorithmException: Algorithm Hmac-Sha3-512 not available
	at java.base/javax.crypto.Mac.getInstance(Mac.java:190)
	at tech.systemsdesign.pants.AppMain$.main(AppMain.scala:15)
	at tech.systemsdesign.pants.AppMain.main(AppMain.scala)

FAILURE: java tech.systemsdesign.pants.AppMain ... exited non-zero (1)


FAILURE
```

And now we become sad. BouncyCastle is suddenly version 1.38 and we have thrown a `java.security.NoSuchAlgorithmException`!

# Part 3 - Debugging the Dependencies

## Pants Clean and Compile with -ldebug

When running a compile on the same code, pants does not indicate in any way that we have
a duplicate class in our classpath:

```shell
$ ./pants clean-all
...
$ ./pants compile example/src/main/scala::

22:24:39 00:00 [main]
               (To run a reporting server: ./pants server)
22:24:40 00:01   [setup]
22:24:40 00:01     [parse]
               Executing tasks in goals: bootstrap -> imports -> unpack-wheels -> unpack-jars -> deferred-sources -> gen -> native-compile -> jvm-platform-validate -> resolve -> compile
22:24:40 00:01   [bootstrap]
22:24:40 00:01     [substitute-aliased-targets]
22:24:40 00:01     [jar-dependency-management]
22:24:40 00:01     [bootstrap-jvm-tools]
22:24:40 00:01     [provide-tools-jar]
22:24:40 00:01   [imports]
22:24:40 00:01     [ivy-imports]
22:24:40 00:01   [unpack-wheels]
22:24:40 00:01     [unpack-wheels]
22:24:40 00:01   [unpack-jars]
22:24:40 00:01     [unpack-jars]
22:24:40 00:01   [deferred-sources]
22:24:40 00:01     [deferred-sources]
22:24:40 00:01   [gen]
22:24:40 00:01     [protoc]
22:24:40 00:01     [thrift-java]
22:24:40 00:01     [thrift-py]
22:24:40 00:01     [py-thrift-namespace-clash-check]
22:24:40 00:01     [grpcio-prep]
22:24:40 00:01     [grpcio-run]
22:24:40 00:01   [native-compile]
22:24:40 00:01     [conan-prep]
22:24:40 00:01     [conan-fetch]
22:24:40 00:01     [c-for-ctypes]
22:24:40 00:01     [cpp-for-ctypes]
22:24:41 00:02   [jvm-platform-validate]
22:24:41 00:02     [jvm-platform-validate]05:24:41 [WARN] No default jvm platform is defined.

22:24:41 00:02   [resolve]
22:24:41 00:02     [coursier]
22:24:41 00:02   [compile]
22:24:41 00:02     [compile-jvm-prep-command]
22:24:41 00:02       [jvm_prep_command]
22:24:41 00:02     [compile-prep-command]
22:24:41 00:02     [compile]
22:24:41 00:02     [rsc]
22:24:41 00:02       [cache] 
                   No cached artifacts for 1 target.
                   Invalidated 1 target.
22:24:41 00:02       [isolation-mixed-pool-bootstrap] 
                   [1/1] Compiling 1 mixed source in 1 target (example/src/main/scala:scala).
22:24:41 00:02       [compile]
                     
22:24:41 00:02         [mixed]
                       [info] Compiling 1 Scala source to /Users/christopherschenk/Documents/dev/systemsdesign/pants/pants-dependency-conflict/.pants.d/compile/rsc/c1e3836b60e5/example.src.main.scala.scala/current/zinc/classes ...
                       [info] Done compiling.
                       [info] Compile success at Jul 27, 2020, 10:24:42 PM [0.539s]
                       
22:24:42 00:03     [javac]
               Waiting for background workers to finish.
22:24:42 00:03   [complete]
               SUCCESS
```

Successful compile, with no warnings! Even running with `-ldebug` doesn't reveal anything useful (I'll spare you the noise of the output here).

## Pants Classmap

This is where we begin to make progress on what is going on. Follow along in the blog post for more details.

```shell
$./pants classmap example/src/main/scala:scala | grep BouncyCastleProvider
org.bouncycastle.jce.provider.BouncyCastleProvider 3rdparty/org/xhtmlrenderer:flying-saucer-pdf
org.bouncycastle.jce.provider.BouncyCastleProvider 3rdparty/org/xhtmlrenderer:flying-saucer-pdf
org.bouncycastle.jce.provider.BouncyCastleProvider$1 3rdparty/org/bouncycastle:bcprov-jdk15on
org.bouncycastle.jce.provider.BouncyCastleProvider 3rdparty/org/bouncycastle:bcprov-jdk15on
org.bouncycastle.jce.provider.BouncyCastleProviderConfiguration 3rdparty/org/bouncycastle:bcprov-jdk15on
```

As we can see, BouncyCastleProvider is coming from two different libraries. To determine the exact details as to which version is coming from where, we want to run a `resolve` with coursier:

```shell
$./pants resolve --resolve-coursier-report 3rdparty/org/xhtmlrenderer:flying-saucer-pdf
...
21:06:05 00:01   [resolve]
21:06:05 00:01     [coursier]
21:06:06 00:02       [coursier]
                       Result:
                     └─ org.xhtmlrenderer:flying-saucer-pdf:9.0.8
                        ├─ com.lowagie:itext:2.1.7
                        │  ├─ bouncycastle:bcmail-jdk14:138
                        │  ├─ bouncycastle:bcprov-jdk14:138
                        │  └─ bouncycastle:bctsp-jdk14:138
                        │     └─ org.bouncycastle:bctsp-jdk14:1.38
                        │        ├─ org.bouncycastle:bcmail-jdk14:1.38
                        │        │  └─ org.bouncycastle:bcprov-jdk14:1.38
                        │        └─ org.bouncycastle:bcprov-jdk14:1.38
                        └─ org.xhtmlrenderer:flying-saucer-core:9.0.8
...
               Waiting for background workers to finish.
21:06:09 00:05   [complete]
               SUCCESS
```

And here we can see that `flying-saucer-pdf` version `9.0.8` has BouncyCastle 1.38 as its own dependency. We have successfully found the culprit that is overriding our desired dependency of 1.64, and now we need to make sure we include only the version we want.

## Excluding Transitive Dependencies

We are able to exclude a transitive dependency from a 3rd-party library like `flying-saucer-pdf` adding the following to our `3rdparty/org/xhtmlrenderer/BUILD` file:

```python
jar_library(
  name = "flying-saucer-pdf",
  jars = [
    jar(
      org = "org.xhtmlrenderer",
      name = "flying-saucer-pdf",
      rev = "9.0.8",
      excludes = [
        exclude(org="org.bouncycastle"),
        exclude(org="bouncycastle"),
      ]
    )
  ],
  dependencies = [
    '3rdparty/org/bouncycastle:bcprov-jdk15on',
    '3rdparty/org/bouncycastle:bcpkix-jdk15on',
    '3rdparty/org/bouncycastle:bcmail-jdk15on',
  ]
)
```

You can also run the `AppMain` and see the output there as well:

```shell
$ ./pants run example:bin

21:20:30 00:00 [main]
               (To run a reporting server: ./pants server)
21:20:30 00:00   [setup]
...
21:20:32 00:02   [run]
21:20:32 00:02     [py]
21:20:32 00:02     [jvm]
21:20:32 00:02       [run]

BC version 1.64
Requesting algorithm Hmac-Sha3-512

               Waiting for background workers to finish.
21:20:33 00:03   [complete]
               SUCCESS
$
```