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
  ],
)
