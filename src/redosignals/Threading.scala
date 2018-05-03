
package redosignals

import java.awt.FlowLayout
import javax.swing._

import com.github.ellbur.lapper.Lapper._

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Promise}
import java.beans.PropertyChangeEvent
import java.beans.PropertyChangeListener

trait Threading {
  self: RedoSignalsSwing.type =>
  trait SlowTarget[A] extends ComputedTarget[Option[A]] {
    def nextValidOnEDT: A
  }
  
  class SlowTargetImpl[A, B](from: Target[A], to: A => B, priority: Int = Thread.NORM_PRIORITY) extends SlowTarget[B] {
    private[this] implicit val redoObserving = new Observing
    
    private var _version: Int = 0
    private val waiters = ArrayBuffer[(Promise[B], Int)]()
    
    private val fromVersioned = from map { from =>
      _version += 1
      (from, _version)
    }
    
    override def finalize(): Unit = {
      super.finalize()
    }
    
    override protected def compute() = source.rely(currentObserving, upset) map (_._1)
    
    private lazy val source = new Source[Option[(B, Int)]](None)
    
    fromVersioned foreach { case (next, version) =>
      writerModerator ! Write(next, version)
    }
    
    def dispose() {
      writer ! Dispose
      writerModerator ! Dispose
    }
    
    private case class Write(data: A, version: Int)
    case object ReadyForData
    private case object Dispose
    
    private lazy val writerModerator: Actor = actorWithOptions(priority = priority) { r =>
      def pending(data: A, version: Int): Next = {
        r.react {
          case Dispose => done
          case ReadyForData =>
            writer ! Write(data, version)
            noPendingNotReady()
          case Write(data, version) =>
            pending(data, version)
        }
      }
      
      def noPendingNotReady(): Next = {
        r.react {
          case Dispose => done
          case Write(data, version) =>
            pending(data, version)
          case ReadyForData =>
            noPendingReady()
        }
      }
      
      def noPendingReady(): Next = {
        r.react {
          case Dispose => done
          case write: Write =>
            writer ! write
            noPendingNotReady()
          case ReadyForData =>
            noPendingReady()
        }
      }
      
      noPendingReady()
    }
    
    private lazy val writer: Actor = actorWithOptions(priority = priority) { r =>
      def main(): Next = {
        r.react {
          case Write(input, version) =>
            try {
              val output = to(input)
              later {
                if (waiters.nonEmpty) {
                  val (readyWaiters, notReadyWaiters) = waiters.partition(_._2 <= version)
                  waiters.clear()
                  waiters ++= notReadyWaiters
                  readyWaiters foreach (_._1.success(output))
                }
                
                source() = Some((output, version))
              }
              writerModerator ! ReadyForData
              then(main())
            }
            catch {
              case t: Throwable =>
                t.printStackTrace()
                writerModerator ! ReadyForData
                then(main())
            }
          
          case Dispose =>
            // Allow the actor to terminate
            done
          
          case _ =>
            main()
        }
      }
      
      main()
    }
    
    override def nextValidOnEDT = {
      assert(SwingUtilities.isEventDispatchThread)
      
      val targetVersion = fromVersioned.now._2
      
      source.now match {
        case Some((x, version)) if version >= targetVersion =>
          x
        
        case _ =>
          val promise = Promise[B]()
          waiters += ((promise, targetVersion))
          
          val worker = new SwingWorker[B, Unit] {
            override def doInBackground(): B = Await.result(promise.future, Duration.Inf)
          }
          
          // From the Swing javadoc examples
          class SwingWorkerCompletionWaiter(var dialog: JDialog) extends PropertyChangeListener {
            override def propertyChange(event: PropertyChangeEvent) = {
              if ("state" == event.getPropertyName && (SwingWorker.StateValue.DONE eq event.getNewValue)) {
                dialog.setVisible(false)
                dialog.dispose()
              }
            }
          }
          val dialog = new JDialog(null: JFrame, true)
          worker.addPropertyChangeListener(new SwingWorkerCompletionWaiter(dialog))
          worker.execute()
          dialog.setLayout(new FlowLayout)
          dialog.add(new JLabel("Calculating..."))
          dialog.pack()
          dialog.setLocationRelativeTo(null)
          dialog.setVisible(true)
          
          worker.get()
      }
    }
  }
  
  def computeOnSeparateThread[A, B](from: Target[A], priority: Int = Thread.NORM_PRIORITY)(to: A => B): SlowTarget[B] =
    new SlowTargetImpl[A, B](from, to, priority = priority)
  
  def onEDT[A](f: => A): A = {
    if (SwingUtilities.isEventDispatchThread) {
      f
    }
    else {
      var theThing: Option[Either[Throwable, A]] = None
      
      SwingUtilities.invokeAndWait(new Runnable {
        override def run(): Unit = {
          try {
            theThing = Some(Right(f))
          }
          catch {
            case t: Throwable =>
              theThing = Some(Left(t))
          }
        }
      })
      
      theThing.get match {
        case Left(a) => throw a
        case Right(b) => b
      }
    }
  }
  
  def laterOnWorkerThread(operation: => Unit): Unit = {
    assert(SwingUtilities.isEventDispatchThread)
    
    val worker = new SwingWorker[Unit, Unit] {
      override def doInBackground(): Unit = operation
    }
    
    worker.execute()
  }
  
  class SynchronizedValue[A](from: Target[A]) {
    implicit val redoObserving = new Observing
    
    private var it: A = onEDT(from.now)
    
    def apply() = synchronized(it)
    
    def get = apply()
    
    later {
      from foreach (x => synchronized(it = x))
    }
  }
  
  def synchronize[A](from: Target[A]): SynchronizedValue[A] = new SynchronizedValue(from)
}
