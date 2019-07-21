# API Queuing Service

To compile or run tests please install [mill](https://www.lihaoyi.com/mill/)

Command to run tests:

```
mill queuing.test
```

To open the code with your IntelliJ you need to generate some files:

```
mill queuing.compile
mill mill.scalalib.GenIdea/idea
```