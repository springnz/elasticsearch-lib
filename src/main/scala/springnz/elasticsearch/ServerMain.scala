package springnz.elasticsearch

object ServerMain {
 def main(args: Array[String]) {
   val server = new ESServer("springnz-server")
   server.start()
   while (!server.isClosed) {
     try {
       Thread.sleep(60 * 1000)
     } catch {
       case _: Throwable ⇒ server.stop()
     }
   }
 }
}
