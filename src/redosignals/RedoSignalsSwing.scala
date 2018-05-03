
package redosignals

import javax.swing.SwingUtilities

object RedoSignalsSwing extends SwingBridge with Threading {
  def later(f: => Unit): Unit = {
    SwingUtilities.invokeLater(new Runnable {
      override def run(): Unit = {
        f
      }
    })
  }
}
