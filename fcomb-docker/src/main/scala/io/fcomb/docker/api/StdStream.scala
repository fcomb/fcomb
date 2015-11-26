package io.fcomb.docker.api

import akka.stream.stage._
import akka.stream.BidiShape
import akka.stream.scaladsl._
import akka.util.{ByteString, ByteStringBuilder, ByteIterator}
import java.nio.ByteOrder

object StdStream extends Enumeration {
  type StdStream = Value

  val In = Value(0)
  val Out = Value(1)
  val Err = Value(2)
}

object StdStreamFrame {
  type StdStreamFrame = (StdStream.StdStream, ByteString)

  private val zeros = ByteString(0, 0, 0)

  private implicit val byteOrder = ByteOrder.BIG_ENDIAN

  class FramingException(msg: String) extends RuntimeException(msg)

  private class FromBytesStage(maximumFrameLength: Int) extends PushPullStage[ByteString, StdStreamFrame] {
    private var buffer = ByteString.empty
    private var stream = StdStream.Out
    private val minimumChunkSize = 8
    private var frameSize = Int.MaxValue

    private def parseLength: Int = {
      val bs = buffer.iterator.drop(4)
      var count = 4
      var decoded = 0
      while (count > 0) {
        decoded <<= 8
        decoded |= bs.next().toInt & 0xFF
        count -= 1
      }
      decoded
    }

    private def tryPull(ctx: Context[StdStreamFrame]): SyncDirective = {
      if (ctx.isFinishing) ctx.fail(new FramingException(
        "Stream finished but there was a truncated final frame in the buffer"
      ))
      else ctx.pull()
    }

    override def onPush(chunk: ByteString, ctx: Context[StdStreamFrame]): SyncDirective = {
      buffer ++= chunk
      doParse(ctx)
    }

    override def onPull(ctx: Context[StdStreamFrame]): SyncDirective = {
      doParse(ctx)
    }

    override def onUpstreamFinish(ctx: Context[StdStreamFrame]): TerminationDirective = {
      if (buffer.nonEmpty) ctx.absorbTermination()
      else ctx.finish()
    }

    private def emitFrame(ctx: Context[StdStreamFrame]): SyncDirective = {
      val parsedFrame = buffer.drop(minimumChunkSize)
        .take(frameSize)
        .compact
      stream = StdStream(buffer.head)
      buffer = buffer.drop(frameSize)
      frameSize = Int.MaxValue
      if (ctx.isFinishing && buffer.isEmpty) ctx.pushAndFinish((stream, parsedFrame))
      else ctx.push((stream, parsedFrame))
    }

    private def doParse(ctx: Context[StdStreamFrame]): SyncDirective = {
      if (buffer.size >= frameSize) {
        emitFrame(ctx)
      } else if (buffer.size >= minimumChunkSize) {
        frameSize = parseLength + minimumChunkSize
        if (frameSize > maximumFrameLength)
          ctx.fail(new FramingException(s"Maximum allowed frame size is $maximumFrameLength " +
            s"but decoded frame header reported size $frameSize"))
        else if (buffer.size >= frameSize)
          emitFrame(ctx)
        else tryPull(ctx)
      } else tryPull(ctx)
    }

    override def postStop(): Unit = {
      buffer = null
    }
  }

  def codec(maximumFrameLength: Int) =
    BidiFlow.fromGraph(FlowGraph.create() { b =>
      val outbound = b.add(Flow[ByteString])
      val inbound = b.add(Flow[ByteString]
        .transform(() ⇒ new FromBytesStage(maximumFrameLength)))
      BidiShape.fromFlows(outbound, inbound)
    })
}
