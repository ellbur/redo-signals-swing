
import redosignals.RedoSignalsSwing._
import redosignals.Source

object NextOnEDTTest extends App {
  later {
    val x = new Source[Int](0)
    
    val y = computeOnSeparateThread(x) { x =>
      Thread.sleep(5000)
      x
    }
    
    new Thread() {
      override def run() {
        later(println(y.nextValidOnEDT))
      }
    }.start()
  }
}
