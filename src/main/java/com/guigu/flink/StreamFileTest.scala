package com.guigu.flink
import org.apache.flink.api.common.serialization.SimpleStringEncoder
import org.apache.flink.streaming.api.functions.sink.filesystem.StreamingFileSink
import org.apache.flink.streaming.api.functions.sink.filesystem.bucketassigners.DateTimeBucketAssigner
import java.util.concurrent.TimeUnit

import org.apache.flink.core.fs.Path
import org.apache.flink.streaming.api.functions.sink.filesystem.rollingpolicies.DefaultRollingPolicy
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
object StreamFileTest {
  def main(args: Array[String]): Unit = {
    val env = StreamExecutionEnvironment.createLocalEnvironment(1)
    val lines = env.socketTextStream("hadoop01", 9999)

    // 设置checkpoint
    env.enableCheckpointing(TimeUnit.SECONDS.toMillis(10))

    val sink = StreamingFileSink.forRowFormat(
      new Path("file:///G:/data/test"),
      new SimpleStringEncoder[String]("UTF-8"))
      .withBucketAssigner(new DateTimeBucketAssigner())
      .withRollingPolicy(
        DefaultRollingPolicy.create()
          .withRolloverInterval(TimeUnit.SECONDS.toMillis(2))
          .withInactivityInterval(TimeUnit.SECONDS.toMillis(1))
          .withMaxPartSize(   1024)
          .build()
      ).build()
    lines.print()
    lines.addSink(sink).setParallelism(1)
    env.execute()
  }
}
