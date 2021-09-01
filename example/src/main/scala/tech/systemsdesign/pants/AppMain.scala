package tech.systemsdesign.pants

import java.security.Security
import javax.crypto.Mac
import org.bouncycastle.jce.provider.BouncyCastleProvider

object AppMain {
  def main(args: Array[String]): Unit = {
    println
    val provider = new BouncyCastleProvider()
    println(provider)
    Security.addProvider(provider)
    val alg = "Hmac-Sha3-512"
    println(s"Requesting algorithm $alg")
    Mac.getInstance(alg)
  }
}
